package lk.tech.testmoon.dto;

/**
 * UV-область mesh для areaId и соответствующий прямоугольник в атласе (UV в соглашении Three.js: v снизу вверх).
 */
public record AreaUvRegionDto(
    int areaId,
    double meshUmin,
    double meshVmin,
    double meshUmax,
    double meshVmax,
    double atlasUmin,
    double atlasVmin,
    double atlasUmax,
    double atlasVmax
) {}
