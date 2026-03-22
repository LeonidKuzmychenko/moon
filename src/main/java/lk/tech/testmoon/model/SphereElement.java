package lk.tech.testmoon.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SphereElement {
    private int areaId;
    private String type; // "rect" or "circle"
    private List<Double> vertices; // Coordinates for the element
    private double lat;
    private double lon;
    private double width;
    private double height;
}
