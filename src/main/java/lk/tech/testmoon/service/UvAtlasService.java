package lk.tech.testmoon.service;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorShortData;
import lk.tech.testmoon.dto.MoonModelData;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.io.GltfModelReader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Service
public class UvAtlasService {

    @Autowired
    private ResourceLoader resourceLoader;

    private static final String GLB_RESOURCE = "classpath:Moon.glb";
    private static final String TILE_RESOURCE = "classpath:tile.jpg";
    private static final String OUTPUT_PATH = "src/main/resources/static/generated/atlas.png";
    private static final int ATLAS_WIDTH = 13200;
    private static final int ATLAS_HEIGHT = 9000;

    public MoonModelData getMoonModelData() throws IOException {
        Resource resource = resourceLoader.getResource(GLB_RESOURCE);
        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.read(resource.getURI());

        List<Float> allPositions = new ArrayList<>();
        List<Float> allUvs = new ArrayList<>();
        List<Integer> allIndices = new ArrayList<>();
        List<Integer> allUvIds = new ArrayList<>();

        int vertexOffset = 0;
        int currentUvId = 1;
        int northUvId = currentUvId++;
        int southUvId = currentUvId++;

        for (MeshModel meshModel : gltfModel.getMeshModels()) {
            String name = meshModel.getName();
            if (name == null) continue;

            for (MeshPrimitiveModel primitiveModel : meshModel.getMeshPrimitiveModels()) {
                AccessorModel positionAccessor = primitiveModel.getAttributes().get("POSITION");
                AccessorModel uvAccessor = primitiveModel.getAttributes().get("TEXCOORD_0");
                AccessorModel indexAccessor = primitiveModel.getIndices();

                float[] positions = getFloatArray(positionAccessor);
                float[] uvs = getFloatArray(uvAccessor);
                int[] indices = getIntArray(indexAccessor);

                // Add positions and UVs
                for (float p : positions) allPositions.add(p);
                for (float u : uvs) allUvs.add(u);

                // Add indices with offset
                for (int index : indices) {
                    allIndices.add(index + vertexOffset);
                }

                int numFaces = indices.length / 3;
                if (name.equalsIgnoreCase("North")) {
                    // All triangles in North have the same uvId
                    for (int i = 0; i < numFaces; i++) {
                        allUvIds.add(northUvId);
                    }
                } else if (name.equalsIgnoreCase("South")) {
                    // All triangles in South have the same uvId
                    for (int i = 0; i < numFaces; i++) {
                        allUvIds.add(southUvId);
                    }
                } else if (name.equalsIgnoreCase("Sphere")) {
                    // Every 2 triangles (quad) have the same uvId
                    for (int i = 0; i < numFaces; i++) {
                        allUvIds.add(currentUvId + (i / 2));
                    }
                    currentUvId += (numFaces + 1) / 2;
                } else {
                    // Default behavior for other groups if any
                    for (int i = 0; i < numFaces; i++) {
                        allUvIds.add(currentUvId++);
                    }
                }

                vertexOffset += positions.length / 3;
            }
        }

        MoonModelData data = new MoonModelData();
        data.setPositions(floatListToArray(allPositions));
        data.setUv(floatListToArray(allUvs));
        data.setIndices(allIndices.stream().mapToInt(Integer::intValue).toArray());
        data.setUvIds(allUvIds.stream().mapToInt(Integer::intValue).toArray());

        return data;
    }

    private float[] floatListToArray(List<Float> list) {
        float[] array = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    public void generateAtlas(MoonModelData data) throws IOException {
        BufferedImage atlas = new BufferedImage(ATLAS_WIDTH, ATLAS_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = atlas.createGraphics();

        // Fill background with white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, ATLAS_WIDTH, ATLAS_HEIGHT);

        BufferedImage tile;
        try (InputStream is = resourceLoader.getResource(TILE_RESOURCE).getInputStream()) {
            tile = ImageIO.read(is);
        }

        int[] indices = data.getIndices();
        float[] uvs = data.getUv();

        int[] uvIds = data.getUvIds();

        for (int i = 0; i < indices.length; i += 3) {
            int faceIndex = i / 3;
            int i1 = indices[i];
            int i2 = indices[i + 1];
            int i3 = indices[i + 2];

            float u1 = uvs[i1 * 2], v1 = uvs[i1 * 2 + 1];
            float u2 = uvs[i2 * 2], v2 = uvs[i2 * 2 + 1];
            float u3 = uvs[i3 * 2], v3 = uvs[i3 * 2 + 1];

            // Use (u, 1-v) for top-left (0,0) image coordinates
            Polygon poly = new Polygon();
            poly.addPoint((int)(u1 * ATLAS_WIDTH), (int)((1 - v1) * ATLAS_HEIGHT));
            poly.addPoint((int)(u2 * ATLAS_WIDTH), (int)((1 - v2) * ATLAS_HEIGHT));
            poly.addPoint((int)(u3 * ATLAS_WIDTH), (int)((1 - v3) * ATLAS_HEIGHT));

            Rectangle bounds = poly.getBounds();
            if (bounds.width > 0 && bounds.height > 0) {
                Shape oldClip = g2d.getClip();
                g2d.setClip(poly);
                g2d.drawImage(tile, bounds.x, bounds.y, bounds.width, bounds.height, null);
                g2d.setClip(oldClip);
            }
            
            // Draw face border and its ID in the middle for debugging/clarity
//            g2d.setColor(new Color(0, 0, 0, 50));
//            g2d.drawPolygon(poly);
            
            // Label each triangle with its uvId
//            g2d.setColor(Color.BLACK);
//            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
//            int centerX = (int)((u1 + u2 + u3) / 3.0 * ATLAS_WIDTH);
//            int centerY = (int)((1 - (v1 + v2 + v3) / 3.0) * ATLAS_HEIGHT);
//            g2d.drawString(String.valueOf(uvIds[faceIndex]), centerX, centerY);
        }

        g2d.dispose();

        File output = new File(OUTPUT_PATH);
        output.getParentFile().mkdirs();
        ImageIO.write(atlas, "png", output);
    }

    private float[] getFloatArray(AccessorModel accessor) {
        AccessorFloatData accessorData = (AccessorFloatData) accessor.getAccessorData();
        float[] result = new float[accessorData.getNumElements() * accessorData.getNumComponentsPerElement()];
        for (int i = 0; i < accessorData.getNumElements(); i++) {
            for (int j = 0; j < accessorData.getNumComponentsPerElement(); j++) {
                result[i * accessorData.getNumComponentsPerElement() + j] = accessorData.get(i, j);
            }
        }
        return result;
    }

    private int[] getIntArray(AccessorModel accessor) {
        AccessorData accessorData = accessor.getAccessorData();
        int numElements = accessorData.getNumElements();
        int numComponents = accessorData.getNumComponentsPerElement();
        int[] result = new int[numElements * numComponents];
        
        for (int i = 0; i < numElements; i++) {
            for (int j = 0; j < numComponents; j++) {
                if (accessorData instanceof AccessorByteData) {
                    result[i * numComponents + j] = ((AccessorByteData) accessorData).get(i, j) & 0xFF;
                } else if (accessorData instanceof AccessorShortData) {
                    result[i * numComponents + j] = ((AccessorShortData) accessorData).get(i, j) & 0xFFFF;
                } else if (accessorData instanceof AccessorIntData) {
                    result[i * numComponents + j] = ((AccessorIntData) accessorData).get(i, j);
                }
            }
        }
        return result;
    }
}

