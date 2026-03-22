package lk.tech.testmoon.service;

import lk.tech.testmoon.model.SphereElement;
import lk.tech.testmoon.model.SphereInfo;
import lk.tech.testmoon.model.UserAreasWrapper;
import lk.tech.testmoon.repository.UserAreasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SphereService {

    private final UserAreasRepository userAreasRepository;
    private final ObjectMapper objectMapper;

    @Value("${sphere.json.path:src/main/resources/sphere/sphere.json}")
    private String sphereJsonPath;

    public SphereService(UserAreasRepository userAreasRepository, ObjectMapper objectMapper) {
        this.userAreasRepository = userAreasRepository;
        this.objectMapper = objectMapper;
    }

    public void generateSphereLayout() throws IOException {
        int rows = 40; // Total vertical rows (latitude)
        int maxSegmentsAtEquator = 80;
        double reductionCoefficient = 1.0; // Adjustable coefficient for reduction towards poles
        
        List<SphereElement> elements = new ArrayList<>();
        int currentAreaId = 1;

        for (int i = 0; i <= rows; i++) {
            double lat = -Math.PI / 2.0 + (double) i * Math.PI / rows;
            
            // Calculate segments in this row. Use cos(lat) to reduce count towards poles.
            int n = (int) Math.max(1, maxSegmentsAtEquator * Math.cos(lat) * reductionCoefficient);
            
            String type = (i == 0 || i == rows) ? "circle" : "rect";
            
            for (int j = 0; j < n; j++) {
                double lon = (double) j * 2.0 * Math.PI / n;
                
                SphereElement element = new SphereElement();
                element.setAreaId(currentAreaId++);
                element.setType(type);
                element.setLat(lat);
                element.setLon(lon);
                
                // Approximate width and height in radians
                element.setWidth(2.0 * Math.PI / n);
                element.setHeight(Math.PI / rows);
                
                elements.add(element);
            }
        }

        SphereInfo sphereInfo = new SphereInfo(elements);
        objectMapper.writeValue(new File(sphereJsonPath), sphereInfo);
    }

    public Map<String, Object> getCombinedData() throws IOException {
        File sphereFile = new File(sphereJsonPath);
        if (!sphereFile.exists()) {
            generateSphereLayout();
        }
        
        SphereInfo sphereInfo = objectMapper.readValue(sphereFile, SphereInfo.class);
        UserAreasWrapper userAreas = userAreasRepository.read();
        
        Map<String, Object> response = new HashMap<>();
        response.put("sphere", sphereInfo);
        response.put("userAreas", userAreas);
        
        return response;
    }
}
