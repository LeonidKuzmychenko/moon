package lk.tech.testmoon.service;

import lk.tech.testmoon.dto.SphereMeshDto;
import org.springframework.stereotype.Service;

@Service
public class SphereMeshService {

    public SphereMeshDto generate(int latSegments, int lonSegments, float radius) {

        int vertexCount = (latSegments + 1) * (lonSegments + 1);
        int triangleCount = latSegments * lonSegments * 2;

        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        int[] indices = new int[triangleCount * 3];
        int[] tileIds = new int[triangleCount];

        int vIndex = 0;

        // ------------------------
        // 1. ВЕРШИНЫ + NORMALS
        // ------------------------
        for (int lat = 0; lat <= latSegments; lat++) {

            float theta = (float) (Math.PI * lat / latSegments);

            for (int lon = 0; lon <= lonSegments; lon++) {

                float phi = (float) (2 * Math.PI * lon / lonSegments);

                float x = (float) (Math.sin(theta) * Math.cos(phi));
                float y = (float) Math.cos(theta);
                float z = (float) (Math.sin(theta) * Math.sin(phi));

                // position
                positions[vIndex] = x * radius;
                positions[vIndex + 1] = y * radius;
                positions[vIndex + 2] = z * radius;

                // normal (unit vector)
                normals[vIndex] = x;
                normals[vIndex + 1] = y;
                normals[vIndex + 2] = z;

                vIndex += 3;
            }
        }

        // ------------------------
        // 2. ИНДЕКСЫ + TILE IDS
        // ------------------------
        int iIndex = 0;
        int tIndex = 0;
        int tileId = 0;

        for (int lat = 0; lat < latSegments; lat++) {
            for (int lon = 0; lon < lonSegments; lon++) {

                int i1 = lat * (lonSegments + 1) + lon;
                int i2 = i1 + 1;
                int i3 = i1 + (lonSegments + 1);
                int i4 = i3 + 1;

                // triangle 1
                indices[iIndex++] = i1;
                indices[iIndex++] = i2;
                indices[iIndex++] = i3;
                tileIds[tIndex++] = tileId;

                // triangle 2
                indices[iIndex++] = i2;
                indices[iIndex++] = i4;
                indices[iIndex++] = i3;
                tileIds[tIndex++] = tileId;

                tileId++;
            }
        }

        return new SphereMeshDto(
                positions,
                normals,
                indices,
                tileIds
        );
    }
}