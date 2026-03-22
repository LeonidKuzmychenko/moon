package lk.tech.testmoon.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AtlasItem {
    private int groupId;
    private int x;
    private int y;
    private int width;
    private int height;
}
