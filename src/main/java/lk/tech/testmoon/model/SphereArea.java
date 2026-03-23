package lk.tech.testmoon.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SphereArea {
    private Integer areaId;
    private String type;
    private List<SphereVertex> vertices;
    private List<SphereUv> uv;
    private List<Integer> triangles;
    
    // Additional fields for GET /sphere aggregation
    private Long groupId;
    private Long userId;
    private String url;
}
