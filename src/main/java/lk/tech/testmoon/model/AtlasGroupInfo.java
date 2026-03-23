package lk.tech.testmoon.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtlasGroupInfo {

    private Long groupId;

    private AtlasCoords atlasCoords;
    private AtlasPixels atlasPixels;

    // для дебага (опционально)
    private String tile;
}