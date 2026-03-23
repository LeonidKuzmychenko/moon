package lk.tech.testmoon.controller;

import lk.tech.testmoon.model.SphereData;
import lk.tech.testmoon.service.SphereService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sphere")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SphereController {
    private final SphereService sphereService;

    @PostMapping
    public ResponseEntity<String> generateSphere() {
        sphereService.generateSphere();
        return ResponseEntity.ok("Sphere generated");
    }

    @GetMapping
    public SphereData getSphere() {
        return sphereService.getSphereData();
    }
}
