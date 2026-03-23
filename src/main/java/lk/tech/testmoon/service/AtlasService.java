package lk.tech.testmoon.service;

import lk.tech.testmoon.config.AppPathsProperties;
import lk.tech.testmoon.model.*;
import lk.tech.testmoon.repository.AtlasRepository;
import lk.tech.testmoon.repository.UserAreaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AtlasService {

    private final AtlasRepository atlasRepository;
    private final UserAreaRepository userAreaRepository;
    private final AppPathsProperties properties;

    private static final int PADDING = 4;
    private static final int VERSION = 2;

    public void generateAtlas(String type) {
        int atlasSize = properties.getAtlasSize();
        int tileSize = properties.getAtlasTileSize(); // <-- НОВОЕ

        if (atlasSize <= 0 || tileSize <= 0) {
            throw new IllegalArgumentException("atlasSize/tileSize must be > 0");
        }

        UserAreaConfig config = userAreaRepository.findAll();
        if (config == null || config.getUsers() == null) {
            throw new IllegalStateException("userAreas.json is empty");
        }

        BufferedImage atlas = new BufferedImage(
                atlasSize,
                atlasSize,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D g = atlas.createGraphics();

        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, atlasSize, atlasSize);

            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            List<AtlasGroupInfo> groups = new ArrayList<>();

            int x = PADDING;
            int y = PADDING;
            int rowHeight = tileSize;

            for (User user : config.getUsers()) {
                if (user.getGroups() == null) continue;

                for (UserGroup group : user.getGroups()) {

                    BufferedImage original = loadTile(group.getTile());
                    BufferedImage tile = resizeTile(original, tileSize);

                    int w = tileSize;
                    int h = tileSize;

                    // перенос строки
                    if (x + w + PADDING > atlasSize) {
                        x = PADDING;
                        y += rowHeight + PADDING;
                    }

                    if (y + h + PADDING > atlasSize) {
                        throw new IllegalStateException("Atlas overflow");
                    }

                    g.drawImage(tile, x, y, null);

                    AtlasPixels pixels = AtlasPixels.builder()
                            .x(x)
                            .y(y)
                            .width(w)
                            .height(h)
                            .build();

                    // bottom-left UV
                    double u1 = (double) x / atlasSize;
                    double u2 = (double) (x + w) / atlasSize;

                    double v1 = (double) (atlasSize - (y + h)) / atlasSize;
                    double v2 = (double) (atlasSize - y) / atlasSize;

                    AtlasCoords coords = AtlasCoords.builder()
                            .u1(u1)
                            .v1(v1)
                            .u2(u2)
                            .v2(v2)
                            .build();

                    validateCoords(coords);

                    groups.add(
                            AtlasGroupInfo.builder()
                                    .groupId(group.getGroupId())
                                    .tile(group.getTile())
                                    .atlasCoords(coords)
                                    .atlasPixels(pixels)
                                    .build()
                    );

                    x += w + PADDING;
                }
            }

            g.dispose();

            saveAtlasPng(atlas);

            AtlasInfo info = AtlasInfo.builder()
                    .atlasWidth(atlasSize)
                    .atlasHeight(atlasSize)
                    .padding(PADDING)
                    .version(VERSION)
                    .groups(groups)
                    .build();

            atlasRepository.saveInfo(info);

            if ("ktx2".equalsIgnoreCase(type)) {
                convertToKtx2();
            }

        } finally {
            atlas.flush();
        }
    }

    private BufferedImage resizeTile(BufferedImage src, int size) {
        BufferedImage resized = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, size, size, null);

        g.dispose();
        return resized;
    }

    private BufferedImage loadTile(String tileName) {
        try {
            File file = new File(properties.getTilesDirPath(), tileName);

            if (!file.exists()) {
                return fallbackTile();
            }

            BufferedImage img = ImageIO.read(file);
            return img != null ? img : fallbackTile();

        } catch (Exception e) {
            log.warn("Failed to load tile: {}", tileName, e);
            return fallbackTile();
        }
    }

    private BufferedImage fallbackTile() {
        BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.MAGENTA);
        g.fillRect(0, 0, 256, 256);
        g.dispose();
        return img;
    }

    private void validateCoords(AtlasCoords c) {
        if (c.getU1() < 0 || c.getV1() < 0 ||
                c.getU2() > 1 || c.getV2() > 1 ||
                c.getU1() >= c.getU2() ||
                c.getV1() >= c.getV2()) {
            throw new IllegalStateException("Invalid atlas coords");
        }
    }

    private void saveAtlasPng(BufferedImage atlas) {
        try {
            File file = new File(properties.getAtlasPngPath());
            file.getParentFile().mkdirs();

            ImageIO.write(atlas, "png", file);

            if (!file.exists() || file.length() == 0) {
                throw new IllegalStateException("atlas.png not created");
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to save atlas.png", e);
        }
    }

    private void convertToKtx2() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "toktx",
                    "--t2",
                    "--genmipmap",
                    "--bcmp",
                    "--clevel", "3",
                    properties.getAtlasKtx2Path(),
                    properties.getAtlasPngPath()
            );

            pb.inheritIO();

            Process p = pb.start();
            int exit = p.waitFor();

            if (exit != 0) {
                throw new RuntimeException("toktx failed");
            }

        } catch (Exception e) {
            throw new RuntimeException("KTX2 conversion failed", e);
        }
    }

    public File getAtlasFile(String type) {
        if ("png".equalsIgnoreCase(type)) {
            return new File(properties.getAtlasPngPath());
        }
        return new File(properties.getAtlasKtx2Path());
    }

    public AtlasInfo getAtlasInfo() {
        return atlasRepository.findInfo();
    }
}