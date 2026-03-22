package lk.tech.testmoon.repository;

import lk.tech.testmoon.model.UserAreasWrapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.ArrayList;

@Repository
public class UserAreasRepository {

    private final ObjectMapper objectMapper;
    private final String filePath;

    public UserAreasRepository(ObjectMapper objectMapper, @Value("${user.areas.path:src/main/resources/user/userAreas.json}") String filePath) {
        this.objectMapper = objectMapper;
        this.filePath = filePath;
        initFile();
    }

    private void initFile() {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                objectMapper.writeValue(file, new UserAreasWrapper(new ArrayList<>()));
            } catch (Exception e) {
                throw new RuntimeException("Could not initialize userAreas.json", e);
            }
        }
    }

    public UserAreasWrapper read() {
        try {
            return objectMapper.readValue(new File(filePath), UserAreasWrapper.class);
        } catch (Exception e) {
            throw new RuntimeException("Could not read userAreas.json", e);
        }
    }

    public void save(UserAreasWrapper wrapper) {
        try {
            objectMapper.writeValue(new File(filePath), wrapper);
        } catch (Exception e) {
            throw new RuntimeException("Could not save userAreas.json", e);
        }
    }
}
