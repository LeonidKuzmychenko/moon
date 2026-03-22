package lk.tech.testmoon.controller;

import lk.tech.testmoon.dto.UvAtlasLayoutDto;
import lk.tech.testmoon.service.UvAtlasService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/uv-atlas")
public class UvAtlasController {

    private final UvAtlasService uvAtlasService;

    public UvAtlasController(UvAtlasService uvAtlasService) {
        this.uvAtlasService = uvAtlasService;
    }

    /**
     * UV-развёртка в JSON: размеры атласа, URL картинки и для каждого areaId — границы в mesh UV и в атласе.
     */
    @GetMapping(value = "/layout", produces = MediaType.APPLICATION_JSON_VALUE)
    public UvAtlasLayoutDto layout() {
        return uvAtlasService.getOrBuildLayout();
    }

    /**
     * Сгенерированный атлас (PNG). Фронт использует как текстуру; tile.jpg вписан в ячейки по UV areaId.
     */
    @GetMapping(value = "/atlas.png", produces = "image/png")
    public ResponseEntity<byte[]> atlasPng() {
        byte[] png = uvAtlasService.getOrBuildAtlasPng();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(png);
    }
}
