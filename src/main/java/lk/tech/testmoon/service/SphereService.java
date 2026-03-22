package lk.tech.testmoon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.model.SphereArea;
import lk.tech.testmoon.model.SphereData;
import lk.tech.testmoon.model.UserAreaConfig;
import lk.tech.testmoon.repository.UserAreaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SphereService {

    private static final int TOTAL_ROWS = 6;
    private static final int TOTAL_COLS = 6;
    private static final double RADIUS = 1.0;

    private final ObjectMapper objectMapper;
    private final UserAreaRepository userAreaRepository;
    private final String sphereFilePath;

    public SphereService(UserAreaRepository userAreaRepository,
                         @Value("${app.sphere-path:src/main/resources/sphere.json}") String sphereFilePath) {
        this.objectMapper = new ObjectMapper();
        this.userAreaRepository = userAreaRepository;
        this.sphereFilePath = sphereFilePath;
    }

    public void generateSphere() {
        List<SphereArea> areas = new ArrayList<>();
        int areaIdCounter = 1;

        for (int r = 0; r < TOTAL_ROWS; r++) {
            for (int c = 0; c < TOTAL_COLS; c++) {
                List<SphereArea.Vertex> vertices = new ArrayList<>();
                double phi1 = (double) r / TOTAL_ROWS * Math.PI;
                double phi2 = (double) (r + 1) / TOTAL_ROWS * Math.PI;
                double theta1 = (double) c / TOTAL_COLS * 2 * Math.PI;
                double theta2 = (double) (c + 1) / TOTAL_COLS * 2 * Math.PI;

                vertices.add(calculateVertex(phi1, theta1));
                vertices.add(calculateVertex(phi1, theta2));
                vertices.add(calculateVertex(phi2, theta2));
                vertices.add(calculateVertex(phi2, theta1));

                areas.add(new SphereArea(areaIdCounter++, vertices));
            }
        }

        SphereData data = new SphereData();
        data.setAreas(areas);

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(sphereFilePath), data);
        } catch (IOException e) {
            throw new RuntimeException("Could not save sphere.json", e);
        }
    }

    private SphereArea.Vertex calculateVertex(double phi, double theta) {
        double x = RADIUS * Math.sin(phi) * Math.cos(theta);
        double y = RADIUS * Math.cos(phi);
        double z = RADIUS * Math.sin(phi) * Math.sin(theta);
        return new SphereArea.Vertex(x, y, z);
    }

    public Map<String, Object> getSphereWithUserData() {
        SphereData sphereData;
        try {
            File file = new File(sphereFilePath);
            if (!file.exists()) {
                generateSphere();
            }
            sphereData = objectMapper.readValue(file, SphereData.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read sphere.json", e);
        }

        UserAreaConfig userConfig = userAreaRepository.read();
        
        // Flatten user groups to map areaId -> group info
        Map<Integer, Map<String, Object>> areaToGroupMap = (userConfig.getUsers() == null ? new ArrayList<lk.tech.testmoon.model.User>() : userConfig.getUsers()).stream()
                .flatMap(u -> (u.getGroups() == null ? new ArrayList<lk.tech.testmoon.model.UserGroup>() : u.getGroups()).stream().map(g -> Map.entry(u.getUserId(), g)))
                .flatMap(entry -> entry.getValue().getAreaIds().stream()
                        .map(areaId -> {
                            Map<String, Object> data = new java.util.HashMap<>();
                            data.put("userId", entry.getKey());
                            data.put("groupId", entry.getValue().getGroupId());
                            data.put("url", entry.getValue().getUrl());
                            data.put("tile", entry.getValue().getTile());
                            return Map.entry(areaId, data);
                        }))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1));

        return Map.of(
                "areas", sphereData.getAreas().stream().map(area -> {
                    Map<String, Object> areaMap = new java.util.HashMap<>(Map.of(
                            "areaId", area.getAreaId(),
                            "vertices", area.getVertices()
                    ));
                    if (areaToGroupMap.containsKey(area.getAreaId())) {
                        areaMap.putAll(areaToGroupMap.get(area.getAreaId()));
                    }
                    return areaMap;
                }).toList()
        );
    }
}
