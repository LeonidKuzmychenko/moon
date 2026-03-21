package lk.tech.testmoon.controller;

import lk.tech.testmoon.dto.SphereMeshDto;
import lk.tech.testmoon.service.SphereMeshService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class SphereMeshController {

    private final SphereMeshService sphereMeshService;

    public SphereMeshController(SphereMeshService sphereMeshService) {
        this.sphereMeshService = sphereMeshService;
    }


    @GetMapping("/sphere")
    public SphereMeshDto getSphere() {
        return sphereMeshService.loadFromGlb();
    }
}
