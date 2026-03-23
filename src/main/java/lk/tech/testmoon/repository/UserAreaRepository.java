package lk.tech.testmoon.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.config.AppPathsProperties;
import lk.tech.testmoon.model.UserAreaConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;

@Repository
@RequiredArgsConstructor
public class UserAreaRepository {
    private final AppPathsProperties paths;
    private final ObjectMapper objectMapper;

    public UserAreaConfig findAll() {
        try {
            File file = new File(paths.getUserAreasPath());
            if (!file.exists()) {
                return new UserAreaConfig();
            }
            return objectMapper.readValue(file, UserAreaConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Error reading user areas JSON", e);
        }
    }

    public void save(UserAreaConfig config) {
        try {
            File file = new File(paths.getUserAreasPath());
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, config);
        } catch (IOException e) {
            throw new RuntimeException("Error saving user areas JSON", e);
        }
    }
}
