package lk.tech.testmoon.model;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtlasInfo {

    private int atlasWidth;
    private int atlasHeight;

    private int padding;
    private int version;

    private List<AtlasGroupInfo> groups;
}