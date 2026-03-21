package lk.tech.testmoon.dto;

public record SphereMeshDto(
    float[] positions,   // [x,y,z,...]
    float[] uvs,         // [u,v,...]
    int[] indices,       // triangles
    int[] tileIds        // triangle → tileId
) {}