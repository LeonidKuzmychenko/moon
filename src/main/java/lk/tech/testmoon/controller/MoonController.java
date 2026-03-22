package lk.tech.testmoon.controller;

import lk.tech.testmoon.dto.MoonModelData;
import lk.tech.testmoon.service.UvAtlasService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/moon")
public class MoonController {

    @Autowired
    private UvAtlasService uvAtlasService;

    @GetMapping("/data")
    public MoonModelData getMoonData() throws IOException {
        MoonModelData data = uvAtlasService.getMoonModelData();
        // Trigger atlas generation if it doesn't exist or on every request for now
        uvAtlasService.generateAtlas(data);
        return data;
    }
}
