package lk.tech.testmoon.controller;

import lk.tech.testmoon.service.AtlasService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/atlas")
public class AtlasController {

    private final AtlasService atlasService;

    public AtlasController(AtlasService atlasService) {
        this.atlasService = atlasService;
    }

    @PostMapping
    public void generateAtlas() {
        atlasService.generateAtlas();
    }

    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] getAtlas() {
        return atlasService.getAtlasPng();
    }

    @GetMapping("/info")
    public List<Map<String, Object>> getAtlasInfo() {
        return atlasService.getAtlasJson();
    }
}
