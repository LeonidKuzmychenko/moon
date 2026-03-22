package lk.tech.testmoon.service;

import lk.tech.testmoon.model.AtlasItem;
import lk.tech.testmoon.model.UserGroup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class AtlasService {

    private final UserGroupService userGroupService;
    private final ObjectMapper objectMapper;

    @Value("${tiles.path:src/main/resources/tiles}")
    private String tilesPath;

    @Value("${atlas.path:src/main/resources/atlas/atlas.png}")
    private String atlasPngPath;

    @Value("${atlas.json.path:src/main/resources/atlas/atlas.json}")
    private String atlasJsonPath;

    public AtlasService(UserGroupService userGroupService, ObjectMapper objectMapper) {
        this.userGroupService = userGroupService;
        this.objectMapper = objectMapper;
    }

    public void generateAtlas() throws IOException {
        List<UserGroup> groups = userGroupService.getAllGroups();
        if (groups.isEmpty()) {
            return;
        }

        List<AtlasItem> atlasItems = new ArrayList<>();
        int tileWidth = 256; // Default size, can be dynamic
        int tileHeight = 256;
        int columns = (int) Math.ceil(Math.sqrt(groups.size()));
        int rows = (int) Math.ceil((double) groups.size() / columns);

        BufferedImage atlas = new BufferedImage(columns * tileWidth, rows * tileHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = atlas.createGraphics();

        for (int i = 0; i < groups.size(); i++) {
            UserGroup group = groups.get(i);
            int col = i % columns;
            int row = i / columns;
            int x = col * tileWidth;
            int y = row * tileHeight;

            File tileFile = new File(tilesPath, group.getTile());
            if (tileFile.exists()) {
                BufferedImage tileImage = ImageIO.read(tileFile);
                g.drawImage(tileImage, x, y, tileWidth, tileHeight, null);
            }

            atlasItems.add(new AtlasItem(group.getGroupId(), x, y, tileWidth, tileHeight));
        }
        g.dispose();

        // Save PNG
        File pngFile = new File(atlasPngPath);
        pngFile.getParentFile().mkdirs();
        ImageIO.write(atlas, "PNG", pngFile);

        // Save JSON
        objectMapper.writeValue(new File(atlasJsonPath), atlasItems);
    }

    public File getAtlasPng() {
        return new File(atlasPngPath);
    }

    public List<AtlasItem> getAtlasInfo() throws IOException {
        File file = new File(atlasJsonPath);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(file, objectMapper.getTypeFactory().constructCollectionType(List.class, AtlasItem.class));
    }
}
