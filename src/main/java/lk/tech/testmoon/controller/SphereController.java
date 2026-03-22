package lk.tech.testmoon.controller;

import lk.tech.testmoon.service.SphereService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/sphere")
public class SphereController {

    private final SphereService sphereService;

    public SphereController(SphereService sphereService) {
        this.sphereService = sphereService;
    }

    @PostMapping
    public void generateSphere() {
        sphereService.generateSphere();
    }

    @GetMapping
    public Map<String, Object> getSphere() {
        return sphereService.getSphereWithUserData();
    }
}
