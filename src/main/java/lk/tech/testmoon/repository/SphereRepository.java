package lk.tech.testmoon.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.config.AppPathsProperties;
import lk.tech.testmoon.model.SphereData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;

@Repository
@RequiredArgsConstructor
public class SphereRepository {
    private final AppPathsProperties paths;
    private final ObjectMapper objectMapper;

    public SphereData find() {
        try {
            File file = new File(paths.getSpherePath());
            if (!file.exists()) {
                return SphereData.builder()
                        .radius(paths.getSphereRadius())
                        .latSegments(paths.getSphereLatSegments())
                        .lonSegments(paths.getSphereLonSegments())
                        .build();
            }
            return objectMapper.readValue(file, SphereData.class);
        } catch (IOException e) {
            throw new RuntimeException("Error reading sphere JSON", e);
        }
    }

    public void save(SphereData sphereData) {
        try {
            File file = new File(paths.getSpherePath());
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, sphereData);
        } catch (IOException e) {
            throw new RuntimeException("Error saving sphere JSON", e);
        }
    }
}
