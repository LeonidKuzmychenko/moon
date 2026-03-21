package lk.tech.testmoon.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.tech.testmoon.dto.SphereMeshDto;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Service
public class SphereMeshService {

    private static final String MODEL_NAME = "Moon.glb";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SphereMeshDto loadFromGlb() {
        byte[] glbBytes = readGlbBytes();
        ParsedGlb parsedGlb = parseGlb(glbBytes);

        JsonNode meshes = parsedGlb.root.path("meshes");
        if (!meshes.isArray() || meshes.isEmpty()) {
            throw new IllegalStateException("No meshes found in Moon.glb");
        }

        List<float[]> positionParts = new ArrayList<>();
        List<float[]> normalParts = new ArrayList<>();
        List<int[]> indexParts = new ArrayList<>();
        boolean hasAnyNormals = false;
        int totalVertexCount = 0;

        for (JsonNode mesh : meshes) {
            JsonNode primitives = mesh.path("primitives");
            if (!primitives.isArray()) {
                continue;
            }
            for (JsonNode primitive : primitives) {
                int positionAccessor = primitive.path("attributes").path("POSITION").asInt(-1);
                int normalAccessor = primitive.path("attributes").path("NORMAL").asInt(-1);
                int indexAccessor = primitive.path("indices").asInt(-1);

                if (positionAccessor < 0 || indexAccessor < 0) {
                    continue;
                }

                float[] positions = readFloatVec3Accessor(parsedGlb.root, parsedGlb.binChunk, positionAccessor);
                int vertexCount = positions.length / 3;
                float[] normals = normalAccessor >= 0
                        ? readFloatVec3Accessor(parsedGlb.root, parsedGlb.binChunk, normalAccessor)
                        : new float[0];

                if (normals.length > 0 && normals.length != positions.length) {
                    throw new IllegalStateException("Normals length must match positions length");
                }

                int[] indices = readIndicesAccessor(parsedGlb.root, parsedGlb.binChunk, indexAccessor);
                if (indices.length % 3 != 0) {
                    throw new IllegalStateException("Indices are not triangles");
                }

                positionParts.add(positions);
                normalParts.add(normals);
                indexParts.add(indices);
                hasAnyNormals = hasAnyNormals || normals.length > 0;
                totalVertexCount += vertexCount;
            }
        }

        if (positionParts.isEmpty()) {
            throw new IllegalStateException("No mesh primitives with POSITION and indices found in Moon.glb");
        }

        float[] mergedPositions = concatFloatArrays(positionParts);
        float[] mergedNormals = hasAnyNormals
                ? mergeNormalsAligned(normalParts, positionParts)
                : new float[0];
        int[] mergedTriangleIndices = mergeIndicesWithVertexOffset(indexParts, positionParts);
        int[] quads = trianglesToDegenerateQuads(mergedTriangleIndices);

        if (mergedPositions.length / 3 != totalVertexCount) {
            throw new IllegalStateException("Merged positions size mismatch");
        }

        return new SphereMeshDto(mergedPositions, mergedNormals, quads);
    }

    private byte[] readGlbBytes() {
        try {
            ClassPathResource classPathResource = new ClassPathResource(MODEL_NAME);
            if (classPathResource.exists()) {
                try (InputStream in = classPathResource.getInputStream()) {
                    return in.readAllBytes();
                }
            }
            Path fallbackPath = Path.of("build", "resources", "main", MODEL_NAME);
            if (Files.exists(fallbackPath)) {
                return Files.readAllBytes(fallbackPath);
            }
            throw new IllegalStateException("Moon.glb was not found in classpath or build/resources/main");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Moon.glb", e);
        }
    }

    private ParsedGlb parseGlb(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int magic = buffer.getInt();
        int version = buffer.getInt();
        int length = buffer.getInt();

        if (magic != 0x46546C67) {
            throw new IllegalStateException("Invalid GLB magic");
        }
        if (version != 2) {
            throw new IllegalStateException("Only GLB v2 is supported");
        }
        if (length != bytes.length) {
            throw new IllegalStateException("Invalid GLB length");
        }

        JsonNode root = null;
        byte[] binChunk = null;

        while (buffer.remaining() >= 8) {
            int chunkLength = buffer.getInt();
            int chunkType = buffer.getInt();
            if (chunkLength < 0 || chunkLength > buffer.remaining()) {
                throw new IllegalStateException("Corrupted GLB chunk");
            }

            byte[] chunkData = new byte[chunkLength];
            buffer.get(chunkData);

            if (chunkType == 0x4E4F534A) { // JSON
                String json = new String(chunkData, StandardCharsets.UTF_8).trim();
                try {
                    root = objectMapper.readTree(json);
                } catch (IOException e) {
                    throw new IllegalStateException("Failed to parse GLB JSON chunk", e);
                }
            } else if (chunkType == 0x004E4942) { // BIN
                binChunk = chunkData;
            }
        }

        if (root == null || binChunk == null) {
            throw new IllegalStateException("GLB must contain JSON and BIN chunks");
        }
        return new ParsedGlb(root, binChunk);
    }

    private float[] readFloatVec3Accessor(JsonNode root, byte[] binChunk, int accessorIndex) {
        JsonNode accessors = root.path("accessors");
        JsonNode accessor = accessors.path(accessorIndex);
        if (accessor.isMissingNode()) {
            throw new IllegalStateException("Accessor not found: " + accessorIndex);
        }

        int componentType = accessor.path("componentType").asInt();
        String type = accessor.path("type").asText();
        int count = accessor.path("count").asInt();
        if (componentType != 5126 || !"VEC3".equals(type)) {
            throw new IllegalStateException("Expected FLOAT VEC3 accessor");
        }

        int byteOffset = accessor.path("byteOffset").asInt(0);
        int bufferViewIndex = accessor.path("bufferView").asInt(-1);
        JsonNode bufferView = root.path("bufferViews").path(bufferViewIndex);
        int viewOffset = bufferView.path("byteOffset").asInt(0);
        int stride = bufferView.path("byteStride").asInt(12);

        float[] out = new float[count * 3];
        ByteBuffer bb = ByteBuffer.wrap(binChunk).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < count; i++) {
            int base = viewOffset + byteOffset + (i * stride);
            out[i * 3] = bb.getFloat(base);
            out[i * 3 + 1] = bb.getFloat(base + 4);
            out[i * 3 + 2] = bb.getFloat(base + 8);
        }
        return out;
    }

    private int[] readIndicesAccessor(JsonNode root, byte[] binChunk, int accessorIndex) {
        JsonNode accessor = root.path("accessors").path(accessorIndex);
        if (accessor.isMissingNode()) {
            throw new IllegalStateException("Index accessor not found: " + accessorIndex);
        }
        int componentType = accessor.path("componentType").asInt();
        int count = accessor.path("count").asInt();
        int byteOffset = accessor.path("byteOffset").asInt(0);

        int bufferViewIndex = accessor.path("bufferView").asInt(-1);
        JsonNode bufferView = root.path("bufferViews").path(bufferViewIndex);
        int viewOffset = bufferView.path("byteOffset").asInt(0);

        ByteBuffer bb = ByteBuffer.wrap(binChunk).order(ByteOrder.LITTLE_ENDIAN);
        int[] indices = new int[count];
        int elementSize = switch (componentType) {
            case 5121 -> 1; // UNSIGNED_BYTE
            case 5123 -> 2; // UNSIGNED_SHORT
            case 5125 -> 4; // UNSIGNED_INT
            default -> throw new IllegalStateException("Unsupported index componentType: " + componentType);
        };

        for (int i = 0; i < count; i++) {
            int pos = viewOffset + byteOffset + (i * elementSize);
            indices[i] = switch (componentType) {
                case 5121 -> Byte.toUnsignedInt(bb.get(pos));
                case 5123 -> Short.toUnsignedInt(bb.getShort(pos));
                case 5125 -> bb.getInt(pos);
                default -> throw new IllegalStateException("Unsupported index componentType: " + componentType);
            };
        }
        return indices;
    }

    private float[] concatFloatArrays(List<float[]> parts) {
        int total = parts.stream().mapToInt(a -> a.length).sum();
        float[] out = new float[total];
        int offset = 0;
        for (float[] part : parts) {
            System.arraycopy(part, 0, out, offset, part.length);
            offset += part.length;
        }
        return out;
    }

    private float[] mergeNormalsAligned(List<float[]> normalParts, List<float[]> positionParts) {
        int total = positionParts.stream().mapToInt(a -> a.length).sum();
        float[] out = new float[total];
        int outOffset = 0;
        for (int i = 0; i < normalParts.size(); i++) {
            float[] normals = normalParts.get(i);
            float[] positions = positionParts.get(i);
            if (normals.length == positions.length) {
                System.arraycopy(normals, 0, out, outOffset, normals.length);
            }
            outOffset += positions.length;
        }
        return out;
    }

    private int[] mergeIndicesWithVertexOffset(List<int[]> indexParts, List<float[]> positionParts) {
        int total = indexParts.stream().mapToInt(a -> a.length).sum();
        int[] out = new int[total];
        int outOffset = 0;
        int vertexOffset = 0;
        for (int i = 0; i < indexParts.size(); i++) {
            int[] indices = indexParts.get(i);
            for (int index : indices) {
                out[outOffset++] = index + vertexOffset;
            }
            vertexOffset += positionParts.get(i).length / 3;
        }
        return out;
    }

    private int[] trianglesToDegenerateQuads(int[] triangleIndices) {
        int triangleCount = triangleIndices.length / 3;
        int[] quads = new int[triangleCount * 4];
        int q = 0;
        for (int t = 0; t < triangleCount; t++) {
            int a = triangleIndices[t * 3];
            int b = triangleIndices[t * 3 + 1];
            int c = triangleIndices[t * 3 + 2];
            quads[q++] = a;
            quads[q++] = b;
            quads[q++] = c;
            quads[q++] = c;
        }
        return quads;
    }

    private record ParsedGlb(JsonNode root, byte[] binChunk) {
    }
}