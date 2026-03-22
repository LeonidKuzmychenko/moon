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
    private static final int DEFAULT_ATLAS_WIDTH = 2048;
    private static final double MIN_UV_RANGE = 1e-5;

    private int atlasWidth = DEFAULT_ATLAS_WIDTH;

    private final SphereMeshService sphereMeshService;

    private volatile byte[] cachedAtlasPng;
    private volatile UvAtlasLayoutDto cachedLayout;

    public UvAtlasService(SphereMeshService sphereMeshService) {
        this.sphereMeshService = sphereMeshService;
    }

    public void setAtlasWidth(int width) {
        if (width >= 64 && width <= 8192) {
            this.atlasWidth = width;
            synchronized (this) {
                cachedAtlasPng = null;
                cachedLayout = null;
            }
        }
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
        String[] areaRegions = mesh.areaRegions();
        float[] positions = mesh.positions();

        int vertexCount = positions.length / 3;
        if (uvs.length != vertexCount * 2) {
            throw new IllegalStateException("uvs length must match positions (2 floats per vertex)");
        }

        Map<Integer, float[]> bounds = new HashMap<>();
        Map<Integer, Double> area3D = new HashMap<>();
        int quadCount = quads.length / 4;
        for (int qi = 0; qi < quadCount; qi++) {
            int aid = areaIds[qi];
            int v0 = quads[qi * 4];
            int v1 = quads[qi * 4 + 1];
            int v2 = quads[qi * 4 + 2];
            int v3 = quads[qi * 4 + 3];

            // UV bounds
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

            // 3D Surface Area
            double triArea1 = calculateTriangleArea(positions, v0, v1, v2);
            double triArea2 = (v2 == v3) ? 0 : calculateTriangleArea(positions, v0, v2, v3);
            area3D.put(aid, area3D.getOrDefault(aid, 0.0) + triArea1 + triArea2);
        }

        List<Integer> sortedIds = new ArrayList<>(bounds.keySet());
        Collections.sort(sortedIds);

        List<PackRect> packRects = new ArrayList<>();
        for (int aid : sortedIds) {
            float[] b = bounds.get(aid);
            double uw = b[2] - b[0];
            double uh = b[3] - b[1];
            if (uw < MIN_UV_RANGE) uw = MIN_UV_RANGE;
            if (uh < MIN_UV_RANGE) uh = MIN_UV_RANGE;

            String region = (aid < areaRegions.length) ? areaRegions[aid] : "SPHERE";
            double a3d = area3D.getOrDefault(aid, 1.0);
            packRects.add(new PackRect(aid, b[0], b[1], b[2], b[3], uw, uh, a3d, region));
        }

        double totalArea3D = packRects.stream().mapToDouble(r -> r.area3D).sum();
        if (totalArea3D <= 0) totalArea3D = 1;

        // Расчет размера атласа и плотности пикселей
        // Мы хотим, чтобы плотность пикселей была примерно одинаковой для всех областей.
        // targetPixels — это желаемое количество пикселей в атласе.
        int atlasW = this.atlasWidth;
        double targetPixels = (double) atlasW * atlasW * 0.8;
        double pixelPerArea3D = targetPixels / totalArea3D;

        for (PackRect r : packRects) {
            double aspect;
            if ("NORTH".equals(r.region) || "SOUTH".equals(r.region)) {
                // Полюса — делаем квадратными в атласе для красоты
                aspect = 1.0;
            } else {
                aspect = r.uw / r.uh;
            }

            // pxW * pxH = r.area3D * pixelPerArea3D
            // pxW / pxH = aspect
            // pxW = pxH * aspect -> pxH * pxH * aspect = r.area3D * pixelPerArea3D
            r.pxH = (int) Math.round(Math.sqrt((r.area3D * pixelPerArea3D) / aspect));
            r.pxW = (int) Math.round(r.pxH * aspect);

            if (r.pxW < 8) r.pxW = 8;
            if (r.pxH < 8) r.pxH = 8;
            // Ограничение ширины: не больше ширины атласа за вычетом отступов
            if (r.pxW > atlasW - 4) {
                r.pxW = atlasW - 4;
                r.pxH = (int) Math.round(r.pxW / aspect);
                if (r.pxH < 8) r.pxH = 8;
            }
        }

        packRects.sort((a, b) -> Integer.compare(b.pxH, a.pxH));

        int margin = 2;
        int curX = margin;
        int curY = margin;
        int rowH = 0;
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
            BufferedImage cropped = getCroppedTile(tile, r.pxW, r.pxH);
            g.drawImage(cropped, r.px, r.py, r.pxW, r.pxH, null);

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

    private BufferedImage getCroppedTile(BufferedImage tile, int targetW, int targetH) {
        int srcW = tile.getWidth();
        int srcH = tile.getHeight();

        double srcAspect = (double) srcW / srcH;
        double targetAspect = (double) targetW / targetH;

        int cropW, cropH, cropX, cropY;

        if (srcAspect > targetAspect) {
            // Исходное изображение шире — обрезаем по бокам
            cropH = srcH;
            cropW = (int) Math.round(srcH * targetAspect);
            cropX = (srcW - cropW) / 2;
            cropY = 0;
        } else {
            // Исходное изображение выше — обрезаем сверху/снизу
            cropW = srcW;
            cropH = (int) Math.round(srcW / targetAspect);
            cropX = 0;
            cropY = (srcH - cropH) / 2;
        }

        cropW = Math.max(1, Math.min(cropW, srcW - cropX));
        cropH = Math.max(1, Math.min(cropH, srcH - cropY));

        return tile.getSubimage(cropX, cropY, cropW, cropH);
    }

    private static double calculateTriangleArea(float[] positions, int v0, int v1, int v2) {
        float ax = positions[v0 * 3];
        float ay = positions[v0 * 3 + 1];
        float az = positions[v0 * 3 + 2];
        float bx = positions[v1 * 3];
        float by = positions[v1 * 3 + 1];
        float bz = positions[v1 * 3 + 2];
        float cx = positions[v2 * 3];
        float cy = positions[v2 * 3 + 1];
        float cz = positions[v2 * 3 + 2];

        float abx = bx - ax;
        float aby = by - ay;
        float abz = bz - az;
        float acx = cx - ax;
        float acy = cy - ay;
        float acz = cz - az;

        float nx = aby * acz - abz * acy;
        float ny = abz * acx - abx * acz;
        float nz = abx * acy - aby * acx;

        return 0.5 * Math.sqrt(nx * nx + ny * ny + nz * nz);
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
        final double area3D;
        final String region;
        int pxW;
        int pxH;
        int px;
        int py;

        PackRect(int areaId, float meshUmin, float meshVmin, float meshUmax, float meshVmax, 
                 double uw, double uh, double area3D, String region) {
            this.areaId = areaId;
            this.meshUmin = meshUmin;
            this.meshVmin = meshVmin;
            this.meshUmax = meshUmax;
            this.meshVmax = meshVmax;
            this.uw = uw;
            this.uh = uh;
            this.area3D = area3D;
            this.region = region;
        }
    }
}
