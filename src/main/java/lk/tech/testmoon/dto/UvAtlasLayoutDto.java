package lk.tech.testmoon.dto;

import java.util.List;

/**
 * JSON-описание UV-атласа: размеры, URL картинки и для каждого areaId — границы в mesh UV и в атласе.
 */
public record UvAtlasLayoutDto(
    int atlasWidth,
    int atlasHeight,
    String atlasUrl,
    List<AreaUvRegionDto> areas
) {}
