package lk.tech.testmoon.controller;

import lk.tech.testmoon.model.AtlasItem;
import lk.tech.testmoon.service.AtlasService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/atlas")
public class AtlasController {

    private final AtlasService service;

    public AtlasController(AtlasService service) {
        this.service = service;
    }

    @PostMapping
    public void generateAtlas() throws IOException {
        service.generateAtlas();
    }

    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getAtlasPng() {
        File file = service.getAtlasPng();
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new FileSystemResource(file));
    }

    @GetMapping("/info")
    public List<AtlasItem> getAtlasInfo() throws IOException {
        return service.getAtlasInfo();
    }
}
