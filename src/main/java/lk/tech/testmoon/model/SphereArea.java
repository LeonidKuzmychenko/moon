package lk.tech.testmoon.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SphereArea {
    private int areaId;
    private List<Vertex> vertices;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Vertex {
        private double x;
        private double y;
        private double z;
    }
}
