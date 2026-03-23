package lk.tech.testmoon.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AtlasPixels {

    private int x;
    private int y;
    private int width;
    private int height;
}