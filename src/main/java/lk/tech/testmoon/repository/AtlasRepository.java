package lk.tech.testmoon.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.config.AppPathsProperties;
import lk.tech.testmoon.model.AtlasInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;

@Repository
@RequiredArgsConstructor
public class AtlasRepository {
    private final AppPathsProperties paths;
    private final ObjectMapper objectMapper;

    public AtlasInfo findInfo() {
        try {
            File file = new File(paths.getAtlasJsonPath());
            if (!file.exists()) {
                throw new IllegalStateException("atlas.json not found");
            }
            if (file.length() == 0) {
                throw new IllegalStateException("atlas.json is empty");
            }
            return objectMapper.readValue(file, AtlasInfo.class);
        } catch (IOException e) {
            throw new RuntimeException("Error reading atlas info JSON", e);
        }
    }

    public void saveInfo(AtlasInfo info) {
        try {
            File file = new File(paths.getAtlasJsonPath());
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, info);
        } catch (IOException e) {
            throw new RuntimeException("Error saving atlas info JSON", e);
        }
    }
}
