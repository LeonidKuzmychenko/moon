package lk.tech.testmoon.controller;

import lk.tech.testmoon.service.SphereService;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/sphere")
public class SphereController {

    private final SphereService service;

    public SphereController(SphereService service) {
        this.service = service;
    }

    @PostMapping
    public void generateSphere() throws IOException {
        service.generateSphereLayout();
    }

    @GetMapping
    public Map<String, Object> getSphereData() throws IOException {
        return service.getCombinedData();
    }
}
