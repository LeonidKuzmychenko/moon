package lk.tech.testmoon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.model.*;
import lk.tech.testmoon.repository.UserAreaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AtlasService {

    private final ObjectMapper objectMapper;
    private final UserAreaRepository userAreaRepository;
    private final String sphereFilePath;
    private final String atlasPngPath;
    private final String atlasJsonPath;
    private final String tilesDirPath;

    private static final int ATLAS_WIDTH = 4000;
    private static final int ATLAS_HEIGHT = 2000;

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
        
        // Fill background with transparent
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);
        g2d.setComposite(AlphaComposite.SrcOver);

        List<Map<String, Object>> atlasInfo = new ArrayList<>();

        for (User user : userConfig.getUsers()) {
            if (user.getGroups() == null) continue;
            for (UserGroup group : user.getGroups()) {
                if (group.getAreaIds() == null || group.getAreaIds().isEmpty()) continue;

                // Find bounding box of areaIds in (phi, theta) space
                double minPhi = Double.MAX_VALUE;
                double maxPhi = Double.MIN_VALUE;
                double minTheta = Double.MAX_VALUE;
                double maxTheta = Double.MIN_VALUE;

                List<Double> thetas = new ArrayList<>();
                for (Integer areaId : group.getAreaIds()) {
                    SphereArea area = sphereData.getAreas().stream()
                            .filter(a -> a.getAreaId() == areaId)
                            .findFirst()
                            .orElse(null);
                    if (area == null) continue;

                    for (SphereArea.Vertex v : area.getVertices()) {
                        double phi = v.getPhi();
                        double theta = v.getTheta();
                        
                        minPhi = Math.min(minPhi, phi);
                        maxPhi = Math.max(maxPhi, phi);
                        thetas.add(theta);
                    }
                }

                if (thetas.isEmpty()) continue;

                // Sort thetas to find the largest gap
                thetas.sort(Double::compare);
                
                double largestGap = 0;
                int largestGapIndex = -1;

                for (int i = 0; i < thetas.size(); i++) {
                    double t1 = thetas.get(i);
                    double t2 = thetas.get((i + 1) % thetas.size());
                    double gap = (t2 - t1 + 2 * Math.PI) % (2 * Math.PI);
                    if (gap > largestGap) {
                        largestGap = gap;
                        largestGapIndex = i;
                    }
                }

                // If largest gap is significant, it means the group wraps around
                // But only if it's NOT the wrap-around gap (between last and first)
                if (largestGap > Math.PI) {
                    if (largestGapIndex == thetas.size() - 1) {
                        // Largest gap is the one crossing 0. Group does NOT cross 0.
                        minTheta = thetas.get(0);
                        maxTheta = thetas.get(thetas.size() - 1);
                    } else {
                        // Largest gap is in the middle. Group DOES cross 0.
                        minTheta = thetas.get(largestGapIndex + 1);
                        maxTheta = thetas.get(largestGapIndex) + 2 * Math.PI;
                    }
                } else {
                    // No large gap, assume it's a contiguous block or full row
                    minTheta = thetas.get(0);
                    maxTheta = thetas.get(thetas.size() - 1);
                    // Special case: if it covers almost the whole row, make it exactly 0..2PI
                    if (largestGap < 0.2 && (maxTheta - minTheta) > 1.8 * Math.PI) {
                        minTheta = 0;
                        maxTheta = 2 * Math.PI;
                    }
                }

                // Map to atlas pixel coordinates
                double x_double = (minTheta / (2 * Math.PI) * ATLAS_WIDTH);
                double y_double = (minPhi / Math.PI * ATLAS_HEIGHT);
                double w_double = (maxTheta / (2 * Math.PI) * ATLAS_WIDTH) - x_double;
                double h_double = (maxPhi / Math.PI * ATLAS_HEIGHT) - y_double;

                int x = (int) x_double;
                int y = (int) y_double;
                int w = (int) w_double;
                int h = (int) h_double;

                if (w <= 0) w = 1;
                if (h <= 0) h = 1;

                // Draw tile
                try {
                    File tileFile = new File(tilesDirPath, group.getTile());
                    if (tileFile.exists()) {
                        BufferedImage tileImg = ImageIO.read(tileFile);
                        
                        // Draw with wrap-around support
                        int drawX = x % ATLAS_WIDTH;
                        g2d.drawImage(tileImg, drawX, y, w, h, null);
                        if (drawX + w > ATLAS_WIDTH) {
                            g2d.drawImage(tileImg, drawX - ATLAS_WIDTH, y, w, h, null);
                        }
                        
                        Map<String, Object> info = new HashMap<>();
                        info.put("groupId", group.getGroupId());
                        info.put("atlasCoords", Map.of("x", drawX, "y", y, "w", w, "h", h));
                        atlasInfo.add(info);
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
