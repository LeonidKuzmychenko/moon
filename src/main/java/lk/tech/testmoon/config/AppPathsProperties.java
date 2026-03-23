package lk.tech.testmoon.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppPathsProperties {
    private String userAreasPath;
    private String spherePath;
    private String atlasPngPath;
    private String atlasKtx2Path;
    private String atlasJsonPath;
    private String tilesDirPath;
    private int atlasSize;
    private int atlasPadding;
    private double sphereRadius;
    private int sphereLatSegments;
    private int sphereLonSegments;
    private int atlasTileSize = 256; // или 512
}
