package lk.tech.testmoon.dto;

public record SphereMeshDto(
    float[] positions,   // [x,y,z,...]
    float[] normals,     // [x,y,z,...]
    float[] uvs,         // [u,v,...] TEXCOORD_0 из GLB, длина = positions.length (по 2 float на вершину)
    int[] quads,         // [v0,v1,v2,v3,...]
    int[] areaIds        // one id per quad entry; North/South = один id на всю группу; Sphere = id на каждый квадрат
) {}
