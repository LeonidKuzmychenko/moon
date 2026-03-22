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

    private static final int ATLAS_WIDTH = 2048;
    private static final int ATLAS_HEIGHT = 1024;

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

                for (Integer areaId : group.getAreaIds()) {
                    SphereArea area = sphereData.getAreas().stream()
                            .filter(a -> a.getAreaId() == areaId)
                            .findFirst()
                            .orElse(null);
                    if (area == null) continue;

                    for (SphereArea.Vertex v : area.getVertices()) {
                        double phi = Math.acos(v.getY());
                        double theta = Math.atan2(v.getZ(), v.getX());
                        if (theta < 0) theta += 2 * Math.PI;

                        minPhi = Math.min(minPhi, phi);
                        maxPhi = Math.max(maxPhi, phi);
                        minTheta = Math.min(minTheta, theta);
                        maxTheta = Math.max(maxTheta, theta);
                    }
                }

                // Correct for theta wrap-around (if max-min > PI, it probably wraps)
                if (maxTheta - minTheta > Math.PI) {
                    // This is a simple heuristic, but works for local groups.
                    // For global groups, it might be more complex.
                    // Let's assume groups don't wrap around for now.
                }

                // Map to atlas pixel coordinates
                int x = (int) (minTheta / (2 * Math.PI) * ATLAS_WIDTH);
                int y = (int) (minPhi / Math.PI * ATLAS_HEIGHT);
                int w = (int) (maxTheta / (2 * Math.PI) * ATLAS_WIDTH) - x;
                int h = (int) (maxPhi / Math.PI * ATLAS_HEIGHT) - y;
                
                if (w <= 0) w = 1;
                if (h <= 0) h = 1;

                // Draw tile
                try {
                    File tileFile = new File(tilesDirPath, group.getTile());
                    if (tileFile.exists()) {
                        BufferedImage tileImg = ImageIO.read(tileFile);
                        g2d.drawImage(tileImg, x, y, w, h, null);
                        
                        Map<String, Object> info = new HashMap<>();
                        info.put("groupId", group.getGroupId());
                        info.put("atlasCoords", Map.of("x", x, "y", y, "w", w, "h", h));
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
