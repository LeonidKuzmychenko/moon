package lk.tech.testmoon.controller;

import lk.tech.testmoon.model.AtlasInfo;
import lk.tech.testmoon.service.AtlasService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

@RestController
@RequestMapping("/atlas")
@RequiredArgsConstructor
public class AtlasController {

    private final AtlasService atlasService;

    @PostMapping
    public ResponseEntity<String> generateAtlas(
            @RequestParam(defaultValue = "ktx2") String type
    ) {
        try {
            atlasService.generateAtlas(type);
            return ResponseEntity.ok("Atlas generated: " + type);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body("Atlas generation failed: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<Resource> getAtlas(
            @RequestParam(defaultValue = "ktx2") String type
    ) {
        File file = atlasService.getAtlasFile(type);

        if (!file.exists() || file.length() == 0) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);

        String contentType = "image/ktx2";
        if ("png".equalsIgnoreCase(type)) {
            contentType = "image/png";
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()))
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @GetMapping("/info")
    public ResponseEntity<AtlasInfo> getAtlasInfo() {
        AtlasInfo info = atlasService.getAtlasInfo();

        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(info);
    }
}