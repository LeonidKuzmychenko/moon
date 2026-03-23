package lk.tech.testmoon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.model.*;
import lk.tech.testmoon.repository.UserAreaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

@Service
public class AtlasService {

    private final ObjectMapper objectMapper;
    private final UserAreaRepository userAreaRepository;
    private final String sphereFilePath;
    private final String atlasPngPath;
    private final String atlasJsonPath;
    private final String tilesDirPath;

    private static final int ATLAS_WIDTH = 15360;
    private static final int ATLAS_HEIGHT = 8640;

    public AtlasService(UserAreaRepository userAreaRepository,
                        @Value("${app.sphere-path:src/main/resources/sphere.json}") String sphereFilePath,
                        @Value("${app.atlas-png-path:src/main/resources/atlas/atlas.png}") String atlasPngPath,
                        @Value("${app.atlas-json-path:src/main/resources/atlas/atlas.json}") String atlasJsonPath,
                        @Value("${app.tiles-dir-path:src/main/resources/tiles}") String tilesDirPath) {
        this.objectMapper = new ObjectMapper();
        this.userAreaRepository = userAreaRepository;
        this.sphereFilePath = sphereFilePath;
        this.atlasPngPath = atlasPngPath;
        this.atlasJsonPath = atlasJsonPath;
        this.tilesDirPath = tilesDirPath;
    }

    public void generateAtlas() {
        SphereData sphereData;
        try {
            sphereData = objectMapper.readValue(new File(sphereFilePath), SphereData.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read sphere.json", e);
        }

        UserAreaConfig userConfig = userAreaRepository.read();
        if (userConfig.getUsers() == null) return;

        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();
        
        // Quality rendering hints for sharper edges
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        
        // Fill background with white tile if available, otherwise solid white
        try {
            File whiteTileFile = new File(tilesDirPath, "white.jpg");
            if (whiteTileFile.exists()) {
                BufferedImage whiteTile = ImageIO.read(whiteTileFile);
                g2d.drawImage(whiteTile, 0, 0, ATLAS_WIDTH, ATLAS_HEIGHT, null);
            } else {
                g2d.setColor(Color.WHITE);
                g2d.fillRect(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);
            }
        } catch (IOException e) {
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);
        }

        List<Map<String, Object>> atlasInfo = new ArrayList<>();

        // Draw user groups
        for (User user : userConfig.getUsers()) {
            if (user.getGroups() == null) continue;
            for (UserGroup group : user.getGroups()) {
                if (group.getAreaIds() == null || group.getAreaIds().isEmpty()) continue;

                List<SphereArea> groupAreas = new ArrayList<>();
                for (Integer areaId : group.getAreaIds()) {
                    sphereData.getAreas().stream()
                            .filter(a -> a.getAreaId() == areaId)
                            .findFirst()
                            .ifPresent(groupAreas::add);
                }

                if (groupAreas.isEmpty()) continue;

                try {
                    File tileFile = new File(tilesDirPath, group.getTile());
                    if (tileFile.exists()) {
                        BufferedImage tileImg = ImageIO.read(tileFile);
                        drawAreasOnAtlas(g2d, groupAreas, tileImg, group.getGroupId(), atlasInfo);
                    }
                } catch (IOException e) {
                    System.err.println("Could not read tile image: " + group.getTile());
                }
            }
        }

        g2d.dispose();

        try {
            ImageIO.write(atlas, "png", new File(atlasPngPath));
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(atlasJsonPath), atlasInfo);
        } catch (IOException e) {
            throw new RuntimeException("Could not save atlas files", e);
        }
    }

    private void drawAreasOnAtlas(Graphics2D g2d, List<SphereArea> areas, BufferedImage tileImg, Long groupId, List<Map<String, Object>> atlasInfo) {
        double minPhi = Double.MAX_VALUE;
        double maxPhi = Double.MIN_VALUE;
        List<Double> thetas = new ArrayList<>();

        for (SphereArea area : areas) {
            for (SphereArea.Vertex v : area.getVertices()) {
                double phi = v.getPhi();
                double theta = v.getTheta();
                
                minPhi = Math.min(minPhi, phi);
                maxPhi = Math.max(maxPhi, phi);
                thetas.add(theta);
            }
        }

        if (thetas.isEmpty()) return;

        thetas.sort(Double::compare);
        
        double largestGap = 0;
        int largestGapIndex = -1;
        double minTheta;
        double maxTheta;

        for (int i = 0; i < thetas.size(); i++) {
            double t1 = thetas.get(i);
            double t2 = thetas.get((i + 1) % thetas.size());
            double gap = (t2 - t1 + 2 * Math.PI) % (2 * Math.PI);
            if (gap > largestGap) {
                largestGap = gap;
                largestGapIndex = i;
            }
        }

        if (largestGap > Math.PI) {
            if (largestGapIndex == thetas.size() - 1) {
                minTheta = thetas.get(0);
                maxTheta = thetas.get(thetas.size() - 1);
            } else {
                minTheta = thetas.get(largestGapIndex + 1);
                maxTheta = thetas.get(largestGapIndex) + 2 * Math.PI;
            }
        } else {
            minTheta = thetas.get(0);
            maxTheta = thetas.get(thetas.size() - 1);
            if (largestGap < 0.2 && (maxTheta - minTheta) > 1.8 * Math.PI) {
                minTheta = 0;
                maxTheta = 2 * Math.PI;
            }
        }

        double x_double = (minTheta / (2 * Math.PI) * ATLAS_WIDTH);
        double y_double = (minPhi / Math.PI * ATLAS_HEIGHT);
        double x_end_double = (maxTheta / (2 * Math.PI) * ATLAS_WIDTH);
        double y_end_double = (maxPhi / Math.PI * ATLAS_HEIGHT);

        int x = (int) Math.round(x_double);
        int y = (int) Math.round(y_double);
        int x_end = (int) Math.round(x_end_double);
        int y_end = (int) Math.round(y_end_double);

        int w = x_end - x;
        int h = y_end - y;

        if (w <= 0) w = 1;
        if (h <= 0) h = 1;

        int drawX = x % ATLAS_WIDTH;
        if (drawX < 0) drawX += ATLAS_WIDTH;

        g2d.drawImage(tileImg, drawX, y, w, h, null);
        if (drawX + w > ATLAS_WIDTH) {
            g2d.drawImage(tileImg, drawX - ATLAS_WIDTH, y, w, h, null);
        }
        
        if (groupId != null && atlasInfo != null) {
            Map<String, Object> info = new HashMap<>();
            info.put("groupId", groupId);
            info.put("atlasCoords", Map.of("x", drawX, "y", y, "w", w, "h", h));
            atlasInfo.add(info);
        }
    }

    public byte[] getAtlasPng() {
        try {
            return Files.readAllBytes(new File(atlasPngPath).toPath());
        } catch (IOException e) {
            throw new RuntimeException("Could not read atlas.png", e);
        }
    }

    public List<Map<String, Object>> getAtlasJson() {
        try {
            return objectMapper.readValue(new File(atlasJsonPath), List.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read atlas.json", e);
        }
    }
}
