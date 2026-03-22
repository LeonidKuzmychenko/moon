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
import java.util.List;

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

        int meshesWithPrimitives = countMeshesWithPrimitives(meshes);
        /*
         * В Blender имена объектов (North / South / Sphere) попадают в glTF как node.name,
         * а meshes[].name часто "Mesh", "Mesh.001" — из‑за этого весь кап считался Sphere
         * и раскраска шла «по квадратам», а не одним areaId.
         */
        Map<Integer, MeshRegion> meshIndexToRegionFromNodes = meshIndexToRegionFromObjectNodes(parsedGlb.root);

        List<float[]> positionParts = new ArrayList<>();
        List<float[]> normalParts = new ArrayList<>();
        List<float[]> uvParts = new ArrayList<>();
        List<int[]> indexParts = new ArrayList<>();
        List<MeshRegion> regionPerPart = new ArrayList<>();
        boolean hasAnyNormals = false;
        int totalVertexCount = 0;

        for (int meshIndex = 0; meshIndex < meshes.size(); meshIndex++) {
            JsonNode mesh = meshes.get(meshIndex);
            JsonNode primitives = mesh.path("primitives");
            if (!primitives.isArray() || primitives.isEmpty()) {
                continue;
            }
            MeshRegion meshRegion = meshIndexToRegionFromNodes.get(meshIndex);
            if (meshRegion == null) {
                String meshDataName = mesh.path("name").asText("");
                meshRegion = classifyMeshName(meshDataName, meshIndex, meshesWithPrimitives);
            }

            for (JsonNode primitive : primitives) {
                int mode = primitive.path("mode").asInt(4);
                if (mode != 4) {
                    throw new IllegalStateException(
                            "Moon.glb primitive mode is not TRIANGLES (mode=" + mode + ")."
                    );
                }

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

                int texCoordAccessor = primitive.path("attributes").path("TEXCOORD_0").asInt(-1);
                float[] uvs = texCoordAccessor >= 0
                        ? readFloatVec2Accessor(parsedGlb.root, parsedGlb.binChunk, texCoordAccessor)
                        : new float[vertexCount * 2];
                if (uvs.length != vertexCount * 2) {
                    throw new IllegalStateException("UV length must match vertex count");
                }

                positionParts.add(positions);
                normalParts.add(normals);
                uvParts.add(uvs);
                indexParts.add(indices);
                regionPerPart.add(meshRegion);
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
        float[] mergedUvs = mergeUvsAligned(uvParts, positionParts);
        int[] mergedTriangleIndices = mergeIndicesWithVertexOffset(indexParts, positionParts);
        MeshRegion[] triangleRegion = buildTriangleRegions(indexParts, regionPerPart);

        TiledMesh tiled = buildQuadsAndAreaIds(mergedTriangleIndices, mergedPositions, triangleRegion);

        if (mergedPositions.length / 3 != totalVertexCount) {
            throw new IllegalStateException("Merged positions size mismatch");
        }

        return new SphereMeshDto(mergedPositions, mergedNormals, mergedUvs, tiled.quads, tiled.areaIds, tiled.areaRegions);
    }

    private int countMeshesWithPrimitives(JsonNode meshes) {
        int n = 0;
        for (JsonNode mesh : meshes) {
            JsonNode primitives = mesh.path("primitives");
            if (primitives.isArray() && primitives.size() > 0) {
                n++;
            }
        }
        return n;
    }

    /**
     * Сопоставляет индекс mesh из glTF с регионом по имени <strong>объекта</strong> (node.name),
     * как в Outliner Blender.
     */
    private Map<Integer, MeshRegion> meshIndexToRegionFromObjectNodes(JsonNode root) {
        Map<Integer, MeshRegion> map = new HashMap<>();
        JsonNode nodes = root.path("nodes");
        if (!nodes.isArray()) {
            return map;
        }
        for (JsonNode node : nodes) {
            if (!node.has("mesh")) {
                continue;
            }
            int meshIdx = node.path("mesh").asInt(-1);
            if (meshIdx < 0) {
                continue;
            }
            String objectName = node.path("name").asText("");
            MeshRegion r = classifyRegionFromBlenderObjectName(objectName);
            if (r != null) {
                map.putIfAbsent(meshIdx, r);
            }
        }
        return map;
    }

    /** Имена объектов: North, South, Sphere (как в сцене Blender). */
    private MeshRegion classifyRegionFromBlenderObjectName(String objectName) {
        String n = objectName == null ? "" : objectName.toLowerCase(Locale.ROOT).trim();
        if (n.equals("north") || n.contains("north")) {
            return MeshRegion.NORTH;
        }
        if (n.equals("south") || n.contains("south")) {
            return MeshRegion.SOUTH;
        }
        if (n.contains("sphere")) {
            return MeshRegion.SPHERE;
        }
        return null;
    }

    /**
     * Fallback: имена mesh data в GLB (часто не совпадают с объектами).
     * Если ровно 3 mesh с примитивами и имя не распознано — порядок: Sphere, North, South.
     */
    private MeshRegion classifyMeshName(String name, int meshIndex, int meshesWithPrimitives) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT).trim();
        if (n.contains("north")) {
            return MeshRegion.NORTH;
        }
        if (n.contains("south")) {
            return MeshRegion.SOUTH;
        }
        if (n.contains("sphere")) {
            return MeshRegion.SPHERE;
        }
        if (meshesWithPrimitives == 3 && meshIndex < 3) {
            return switch (meshIndex) {
                case 0 -> MeshRegion.SPHERE;
                case 1 -> MeshRegion.NORTH;
                case 2 -> MeshRegion.SOUTH;
                default -> MeshRegion.SPHERE;
            };
        }
        return MeshRegion.SPHERE;
    }

    private MeshRegion[] buildTriangleRegions(List<int[]> indexParts, List<MeshRegion> regionPerPart) {
        if (indexParts.size() != regionPerPart.size()) {
            throw new IllegalStateException("indexParts and regionPerPart size mismatch");
        }
        int triangleCount = indexParts.stream().mapToInt(a -> a.length).sum() / 3;
        MeshRegion[] out = new MeshRegion[triangleCount];
        int triOffset = 0;
        for (int p = 0; p < indexParts.size(); p++) {
            int n = indexParts.get(p).length / 3;
            MeshRegion r = regionPerPart.get(p);
            for (int i = 0; i < n; i++) {
                out[triOffset + i] = r;
            }
            triOffset += n;
        }
        return out;
    }

    /**
     * North и South — по одному areaId на всю группу.
     * Sphere — пары треугольников в квадраты, у каждого квадрата свой areaId; остаток — по одному id на треугольник.
     */
    private TiledMesh buildQuadsAndAreaIds(
            int[] triangleIndices,
            float[] positions,
            MeshRegion[] triangleRegion
    ) {
        int triangleCount = triangleIndices.length / 3;
        if (triangleRegion.length != triangleCount) {
            throw new IllegalStateException("triangleRegion length mismatch");
        }

        int nextAreaId = 0;
        int northAreaId = nextAreaId++;
        int southAreaId = nextAreaId++;

        List<Integer> northTris = new ArrayList<>();
        List<Integer> southTris = new ArrayList<>();
        boolean[] isSphere = new boolean[triangleCount];
        for (int t = 0; t < triangleCount; t++) {
            switch (triangleRegion[t]) {
                case NORTH -> northTris.add(t);
                case SOUTH -> southTris.add(t);
                case SPHERE -> isSphere[t] = true;
            }
        }

        List<int[]> quadsList = new ArrayList<>();
        List<Integer> areaIdsList = new ArrayList<>();
        Map<Integer, String> areaIdToRegion = new HashMap<>();

        areaIdToRegion.put(northAreaId, "NORTH");
        areaIdToRegion.put(southAreaId, "SOUTH");

        for (int t : northTris) {
            int a = triangleIndices[t * 3];
            int b = triangleIndices[t * 3 + 1];
            int c = triangleIndices[t * 3 + 2];
            quadsList.add(new int[]{a, b, c, c});
            areaIdsList.add(northAreaId);
        }
        for (int t : southTris) {
            int a = triangleIndices[t * 3];
            int b = triangleIndices[t * 3 + 1];
            int c = triangleIndices[t * 3 + 2];
            quadsList.add(new int[]{a, b, c, c});
            areaIdsList.add(southAreaId);
        }

        appendSphereGroups(triangleIndices, positions, isSphere, nextAreaId, quadsList, areaIdsList, areaIdToRegion);

        int[] quads = new int[quadsList.size() * 4];
        int[] areaIds = new int[areaIdsList.size()];
        for (int i = 0; i < quadsList.size(); i++) {
            int[] q = quadsList.get(i);
            quads[i * 4] = q[0];
            quads[i * 4 + 1] = q[1];
            quads[i * 4 + 2] = q[2];
            quads[i * 4 + 3] = q[3];
            areaIds[i] = areaIdsList.get(i);
        }

        int maxId = -1;
        for (int aid : areaIds) if (aid > maxId) maxId = aid;
        String[] areaRegions = new String[maxId + 1];
        for (Map.Entry<Integer, String> entry : areaIdToRegion.entrySet()) {
            areaRegions[entry.getKey()] = entry.getValue();
        }

        return new TiledMesh(quads, areaIds, areaRegions);
    }

    private static final int TARGET_GROUP_SIZE = 12; // Примерно 12 треугольников в одной области

    private void appendSphereGroups(
            int[] triangleIndices,
            float[] positions,
            boolean[] isSphere,
            int firstSphereAreaId,
            List<int[]> quadsList,
            List<Integer> areaIdsList,
            Map<Integer, String> areaIdToRegion
    ) {
        int triangleCount = triangleIndices.length / 3;
        int[][] triangles = new int[triangleCount][3];
        for (int t = 0; t < triangleCount; t++) {
            triangles[t][0] = triangleIndices[t * 3];
            triangles[t][1] = triangleIndices[t * 3 + 1];
            triangles[t][2] = triangleIndices[t * 3 + 2];
        }

        Map<Long, List<Integer>> edgeToTriangles = new HashMap<>();
        for (int t = 0; t < triangleCount; t++) {
            if (!isSphere[t]) continue;
            addEdgeOwner(edgeToTriangles, edgeKey(triangles[t][0], triangles[t][1]), t);
            addEdgeOwner(edgeToTriangles, edgeKey(triangles[t][1], triangles[t][2]), t);
            addEdgeOwner(edgeToTriangles, edgeKey(triangles[t][2], triangles[t][0]), t);
        }

        boolean[] consumed = new boolean[triangleCount];
        int areaId = firstSphereAreaId;

        for (int t = 0; t < triangleCount; t++) {
            if (!isSphere[t] || consumed[t]) continue;

            // Region Growing (BFS)
            List<Integer> group = new ArrayList<>();
            Queue<Integer> queue = new LinkedList<>();
            queue.add(t);
            consumed[t] = true;

            while (!queue.isEmpty() && group.size() < TARGET_GROUP_SIZE) {
                int current = queue.poll();
                group.add(current);

                // Find neighbors
                for (int i = 0; i < 3; i++) {
                    int v1 = triangles[current][i];
                    int v2 = triangles[current][(i + 1) % 3];
                    long edge = edgeKey(v1, v2);
                    List<Integer> neighbors = edgeToTriangles.get(edge);
                    if (neighbors != null) {
                        for (int neighbor : neighbors) {
                            if (isSphere[neighbor] && !consumed[neighbor]) {
                                consumed[neighbor] = true;
                                queue.add(neighbor);
                                if (group.size() + queue.size() >= TARGET_GROUP_SIZE) break;
                            }
                        }
                    }
                    if (group.size() + queue.size() >= TARGET_GROUP_SIZE) break;
                }
            }
            
            // Add remaining items in queue to group
            while(!queue.isEmpty()) {
                group.add(queue.poll());
            }

            for (int triIdx : group) {
                int a = triangles[triIdx][0];
                int b = triangles[triIdx][1];
                int c = triangles[triIdx][2];
                quadsList.add(new int[]{a, b, c, c});
                areaIdsList.add(areaId);
            }
            areaIdToRegion.put(areaId, "SPHERE");
            areaId++;
        }
    }

    private float[][] buildTriangleNormals(int[][] triangles, float[] positions) {
        float[][] normals = new float[triangles.length][3];
        for (int t = 0; t < triangles.length; t++) {
            int a = triangles[t][0];
            int b = triangles[t][1];
            int c = triangles[t][2];
            float ax = positions[a * 3];
            float ay = positions[a * 3 + 1];
            float az = positions[a * 3 + 2];
            float bx = positions[b * 3];
            float by = positions[b * 3 + 1];
            float bz = positions[b * 3 + 2];
            float cx = positions[c * 3];
            float cy = positions[c * 3 + 1];
            float cz = positions[c * 3 + 2];
            float abx = bx - ax;
            float aby = by - ay;
            float abz = bz - az;
            float acx = cx - ax;
            float acy = cy - ay;
            float acz = cz - az;
            float nx = aby * acz - abz * acy;
            float ny = abz * acx - abx * acz;
            float nz = abx * acy - aby * acx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1e-12f) {
                normals[t][0] = nx / len;
                normals[t][1] = ny / len;
                normals[t][2] = nz / len;
            }
        }
        return normals;
    }

    private double scorePair(float[] n1, float[] n2, float[] positions, int u, int v) {
        double dot = n1[0] * n2[0] + n1[1] * n2[1] + n1[2] * n2[2];
        float ux = positions[u * 3];
        float uy = positions[u * 3 + 1];
        float uz = positions[u * 3 + 2];
        float vx = positions[v * 3];
        float vy = positions[v * 3 + 1];
        float vz = positions[v * 3 + 2];
        double dx = ux - vx;
        double dy = uy - vy;
        double dz = uz - vz;
        double edgeLengthSq = dx * dx + dy * dy + dz * dz;
        return dot * 1000.0 + edgeLengthSq;
    }

    private void addEdgeOwner(Map<Long, List<Integer>> edgeToTriangles, long edgeKey, int triangleIndex) {
        edgeToTriangles.computeIfAbsent(edgeKey, ignored -> new ArrayList<>(2)).add(triangleIndex);
    }

    private long edgeKey(int a, int b) {
        int hi = Math.max(a, b);
        int lo = Math.min(a, b);
        return (((long) hi) << 32) | (lo & 0xffffffffL);
    }

    private int edgeHi(long key) {
        return (int) (key >>> 32);
    }

    private int edgeLo(long key) {
        return (int) key;
    }

    private int oppositeVertex(int[] triangle, int u, int v) {
        for (int vertex : triangle) {
            if (vertex != u && vertex != v) {
                return vertex;
            }
        }
        return -1;
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

    private float[] readFloatVec2Accessor(JsonNode root, byte[] binChunk, int accessorIndex) {
        JsonNode accessors = root.path("accessors");
        JsonNode accessor = accessors.path(accessorIndex);
        if (accessor.isMissingNode()) {
            throw new IllegalStateException("Accessor not found: " + accessorIndex);
        }

        int componentType = accessor.path("componentType").asInt();
        String type = accessor.path("type").asText();
        int count = accessor.path("count").asInt();
        if (componentType != 5126 || !"VEC2".equals(type)) {
            throw new IllegalStateException("Expected FLOAT VEC2 accessor for TEXCOORD");
        }

        int byteOffset = accessor.path("byteOffset").asInt(0);
        int bufferViewIndex = accessor.path("bufferView").asInt(-1);
        JsonNode bufferView = root.path("bufferViews").path(bufferViewIndex);
        int viewOffset = bufferView.path("byteOffset").asInt(0);
        int stride = bufferView.path("byteStride").asInt(8);

        float[] out = new float[count * 2];
        ByteBuffer bb = ByteBuffer.wrap(binChunk).order(ByteOrder.LITTLE_ENDIAN);

        for (int i = 0; i < count; i++) {
            int base = viewOffset + byteOffset + (i * stride);
            out[i * 2] = bb.getFloat(base);
            out[i * 2 + 1] = bb.getFloat(base + 4);
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
            case 5121 -> 1;
            case 5123 -> 2;
            case 5125 -> 4;
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

    private float[] mergeUvsAligned(List<float[]> uvParts, List<float[]> positionParts) {
        int vertexCountTotal = positionParts.stream().mapToInt(a -> a.length / 3).sum();
        float[] out = new float[vertexCountTotal * 2];
        int outOffset = 0;
        for (int i = 0; i < uvParts.size(); i++) {
            float[] uvs = uvParts.get(i);
            int vertexCount = positionParts.get(i).length / 3;
            if (uvs.length == vertexCount * 2) {
                System.arraycopy(uvs, 0, out, outOffset, uvs.length);
            }
            outOffset += vertexCount * 2;
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

    private enum MeshRegion {
        SPHERE,
        NORTH,
        SOUTH
    }

    private record ParsedGlb(JsonNode root, byte[] binChunk) {
    }

    private record TiledMesh(int[] quads, int[] areaIds, String[] areaRegions) {
    }
}
