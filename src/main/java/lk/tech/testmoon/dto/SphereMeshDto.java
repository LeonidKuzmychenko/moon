package lk.tech.testmoon.dto;

public record SphereMeshDto(
    float[] positions,   // [x,y,z,...]
    float[] normals,     // [x,y,z,...]
    int[] quads          // [v0,v1,v2,v3,...]
) {}