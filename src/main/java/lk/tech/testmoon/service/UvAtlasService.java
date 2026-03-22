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
    private static final int ATLAS_WIDTH = 2200;
    private static final int ATLAS_HEIGHT = 1500;

    public MoonModelData getMoonModelData() throws IOException {
        Resource resource = resourceLoader.getResource(GLB_RESOURCE);
        GltfModelReader reader = new GltfModelReader();
        GltfModel gltfModel = reader.read(resource.getURI());

        if (gltfModel.getMeshModels().isEmpty()) {
            throw new IOException("No meshes found in the GLB file");
        }
        MeshModel meshModel = gltfModel.getMeshModels().get(0);
        MeshPrimitiveModel primitiveModel = meshModel.getMeshPrimitiveModels().get(0);

        AccessorModel positionAccessor = primitiveModel.getAttributes().get("POSITION");
        AccessorModel uvAccessor = primitiveModel.getAttributes().get("TEXCOORD_0");
        AccessorModel indexAccessor = primitiveModel.getIndices();

        float[] positions = getFloatArray(positionAccessor);
        float[] uvs = getFloatArray(uvAccessor);
        int[] indices = getIntArray(indexAccessor);

        // Assign uvId to each face (triangle)
        int numFaces = indices.length / 3;
        int[] uvIds = new int[numFaces];
        for (int i = 0; i < numFaces; i++) {
            uvIds[i] = i + 1; // 1-based IDs
        }

        MoonModelData data = new MoonModelData();
        data.setPositions(positions);
        data.setUv(uvs);
        data.setIndices(indices);
        data.setUvIds(uvIds);

        return data;
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

        for (int i = 0; i < indices.length; i += 3) {
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
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.drawPolygon(poly);
            
            // Label each triangle with its uvId
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            int centerX = (int)((u1 + u2 + u3) / 3.0 * ATLAS_WIDTH);
            int centerY = (int)((1 - (v1 + v2 + v3) / 3.0) * ATLAS_HEIGHT);
            g2d.drawString(String.valueOf(i / 3 + 1), centerX, centerY);
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

