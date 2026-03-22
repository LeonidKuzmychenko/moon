package lk.tech.testmoon.dto;

import lombok.Data;
import java.util.List;

@Data
public class MoonModelData {
    private float[] positions;
    private int[] indices;
    private float[] uv;
    private int[] uvIds;
    private int northUvId;
    private int southUvId;
}
