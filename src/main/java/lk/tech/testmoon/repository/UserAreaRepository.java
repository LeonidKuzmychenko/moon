package lk.tech.testmoon.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.model.UserAreaConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;

@Repository
public class UserAreaRepository {

    private final ObjectMapper objectMapper;
    private final String filePath;

    public UserAreaRepository(@Value("${app.user-areas-path:src/main/resources/user/userAreas.json}") String filePath) {
        this.objectMapper = new ObjectMapper();
        this.filePath = filePath;
    }

    public UserAreaConfig read() {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                return new UserAreaConfig();
            }
            return objectMapper.readValue(file, UserAreaConfig.class);
        } catch (IOException e) {
            throw new RuntimeException("Could not read user areas file", e);
        }
    }

    public void write(UserAreaConfig config) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), config);
        } catch (IOException e) {
            throw new RuntimeException("Could not write user areas file", e);
        }
    }
}
