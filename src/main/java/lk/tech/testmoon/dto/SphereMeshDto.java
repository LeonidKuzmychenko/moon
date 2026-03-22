package lk.tech.testmoon.dto;

public record SphereMeshDto(
    float[] positions,   // [x,y,z,...]
    float[] normals,     // [x,y,z,...]
    float[] uvs,         // [u,v,...] TEXCOORD_0 из GLB, 2 float на каждую вершину (x,y,z -> u,v)
    int[] quads,         // [v0,v1,v2,v3,...]
    int[] areaIds,       // one id per quad entry
    String[] areaRegions // "SPHERE", "NORTH", or "SOUTH" for each unique areaId
) {}
