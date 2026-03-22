package lk.tech.testmoon.service;

import lk.tech.testmoon.dto.AreaUvRegionDto;
import lk.tech.testmoon.dto.SphereMeshDto;
import lk.tech.testmoon.dto.UvAtlasLayoutDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Строит атлас: в каждую ячейку кладёт tile.jpg по UV-границам areaId, сохраняет PNG в ресурсы (если доступно) и кэширует байты.
 */
@Service
public class UvAtlasService {

    private static final String TILE_NAME = "tile.jpg";
    private static final int MAX_ATLAS_WIDTH = 2048;
    private static final double MIN_UV_RANGE = 1e-5;

    private final SphereMeshService sphereMeshService;

    private volatile byte[] cachedAtlasPng;
    private volatile UvAtlasLayoutDto cachedLayout;

    public UvAtlasService(SphereMeshService sphereMeshService) {
        this.sphereMeshService = sphereMeshService;
    }

    public UvAtlasLayoutDto getOrBuildLayout() {
        if (cachedLayout != null) {
            return cachedLayout;
        }
        synchronized (this) {
            if (cachedLayout != null) {
                return cachedLayout;
            }
            build();
            return Objects.requireNonNull(cachedLayout);
        }
    }

    public byte[] getOrBuildAtlasPng() {
        if (cachedAtlasPng != null) {
            return cachedAtlasPng;
        }
        getOrBuildLayout();
        return Objects.requireNonNull(cachedAtlasPng);
    }

    private void build() {
        SphereMeshDto mesh = sphereMeshService.loadFromGlb();
        float[] uvs = mesh.uvs();
        int[] quads = mesh.quads();
        int[] areaIds = mesh.areaIds();

        int vertexCount = mesh.positions().length / 3;
        if (uvs.length != vertexCount * 2) {
            throw new IllegalStateException("uvs length must match positions (2 floats per vertex)");
        }

        Map<Integer, float[]> bounds = new HashMap<>();
        int quadCount = quads.length / 4;
        for (int qi = 0; qi < quadCount; qi++) {
            int aid = areaIds[qi];
            int v0 = quads[qi * 4];
            int v1 = quads[qi * 4 + 1];
            int v2 = quads[qi * 4 + 2];
            int v3 = quads[qi * 4 + 3];
            int[] verts = (v2 == v3) ? new int[]{v0, v1, v2} : new int[]{v0, v1, v2, v3};
            for (int vi : verts) {
                float u = uvs[vi * 2];
                float v = uvs[vi * 2 + 1];
                float[] b = bounds.computeIfAbsent(aid, ignored -> new float[]{u, v, u, v});
                b[0] = Math.min(b[0], u);
                b[1] = Math.min(b[1], v);
                b[2] = Math.max(b[2], u);
                b[3] = Math.max(b[3], v);
            }
        }

        List<Integer> sortedIds = new ArrayList<>(bounds.keySet());
        Collections.sort(sortedIds);

        List<PackRect> packRects = new ArrayList<>();
        for (int aid : sortedIds) {
            float[] b = bounds.get(aid);
            double uw = b[2] - b[0];
            double uh = b[3] - b[1];
            if (uw < MIN_UV_RANGE) {
                uw = MIN_UV_RANGE;
            }
            if (uh < MIN_UV_RANGE) {
                uh = MIN_UV_RANGE;
            }
            packRects.add(new PackRect(aid, b[0], b[1], b[2], b[3], uw, uh));
        }

        double sumArea = packRects.stream().mapToDouble(r -> r.uw * r.uh).sum();
        if (sumArea <= 0) {
            sumArea = 1;
        }
        double targetPixels = (double) MAX_ATLAS_WIDTH * MAX_ATLAS_WIDTH * 0.75;
        double scale = Math.sqrt(targetPixels / sumArea);
        scale = Math.min(scale, 512);

        for (PackRect r : packRects) {
            r.pxW = Math.max(8, (int) Math.ceil(r.uw * scale));
            r.pxH = Math.max(8, (int) Math.ceil(r.uh * scale));
        }

        packRects.sort((a, b) -> Integer.compare(b.pxH, a.pxH));

        int margin = 2;
        int curX = margin;
        int curY = margin;
        int rowH = 0;
        int atlasW = MAX_ATLAS_WIDTH;
        List<PackRect> placed = new ArrayList<>();

        for (PackRect r : packRects) {
            if (curX + r.pxW + margin > atlasW) {
                curX = margin;
                curY += rowH + margin;
                rowH = 0;
            }
            r.px = curX;
            r.py = curY;
            placed.add(r);
            rowH = Math.max(rowH, r.pxH);
            curX += r.pxW + margin;
        }

        int atlasH = curY + rowH + margin;
        atlasH = Math.max(atlasH, 64);

        BufferedImage tile = loadTileImage();
        BufferedImage atlas = new BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = atlas.createGraphics();
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, atlasW, atlasH);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        List<AreaUvRegionDto> areaDtos = new ArrayList<>();
        for (PackRect r : placed) {
            g.drawImage(tile, r.px, r.py, r.pxW, r.pxH, null);
            double[] atlasUv = pixelRectToThreeJsUv(r.px, r.py, r.pxW, r.pxH, atlasW, atlasH);
            areaDtos.add(new AreaUvRegionDto(
                    r.areaId,
                    r.meshUmin, r.meshVmin, r.meshUmax, r.meshVmax,
                    atlasUv[0], atlasUv[1], atlasUv[2], atlasUv[3]
            ));
        }
        g.dispose();

        byte[] png;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(atlas, "png", bos);
            png = bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode atlas PNG", e);
        }

        cachedAtlasPng = png;
        cachedLayout = new UvAtlasLayoutDto(atlasW, atlasH, "/api/uv-atlas/atlas.png", areaDtos);

        writeAtlasToResourcesIfPossible(png);
    }

    /**
     * Пиксели (x,y) — от верхнего левого угла PNG; Three.js UV — origin внизу слева.
     */
    private static double[] pixelRectToThreeJsUv(int x, int y, int w, int h, int atlasW, int atlasH) {
        double uMin = x / (double) atlasW;
        double uMax = (x + w) / (double) atlasW;
        double vMinThree = 1.0 - (y + h) / (double) atlasH;
        double vMaxThree = 1.0 - y / (double) atlasH;
        return new double[]{uMin, vMinThree, uMax, vMaxThree};
    }

    private BufferedImage loadTileImage() {
        try {
            ClassPathResource res = new ClassPathResource(TILE_NAME);
            if (!res.exists()) {
                Path p = Path.of("build", "resources", "main", TILE_NAME);
                if (Files.exists(p)) {
                    return ImageIO.read(p.toFile());
                }
                throw new IllegalStateException(TILE_NAME + " not found on classpath");
            }
            try (InputStream in = res.getInputStream()) {
                BufferedImage img = ImageIO.read(in);
                if (img == null) {
                    throw new IllegalStateException("Could not decode " + TILE_NAME);
                }
                return img;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + TILE_NAME, e);
        }
    }

    private void writeAtlasToResourcesIfPossible(byte[] png) {
        try {
            String userDir = System.getProperty("user.dir", ".");
            Path out = Path.of(userDir, "src", "main", "resources", "static", "generated", "atlas.png");
            Files.createDirectories(out.getParent());
            Files.write(out, png);
        } catch (Exception ignored) {
            // jar / read-only — только кэш в памяти
        }
    }

    private static final class PackRect {
        final int areaId;
        final float meshUmin;
        final float meshVmin;
        final float meshUmax;
        final float meshVmax;
        final double uw;
        final double uh;
        int pxW;
        int pxH;
        int px;
        int py;

        PackRect(int areaId, float meshUmin, float meshVmin, float meshUmax, float meshVmax, double uw, double uh) {
            this.areaId = areaId;
            this.meshUmin = meshUmin;
            this.meshVmin = meshVmin;
            this.meshUmax = meshUmax;
            this.meshVmax = meshVmax;
            this.uw = uw;
            this.uh = uh;
        }
    }
}
