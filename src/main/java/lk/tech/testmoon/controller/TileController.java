//package lk.tech.testmoon.controller;
//
//import lk.tech.testmoon.dto.TileDto;
//import lk.tech.testmoon.service.TileService;
//import org.springframework.web.bind.annotation.*;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.util.List;
//
//@RestController
//@RequestMapping("/tiles")
//public class TileController {
//
//    private final TileService service;
//
//    public TileController(TileService service) {
//        this.service = service;
//    }
//
//    @GetMapping
//    public List<TileDto> getTiles() {
//        return service.getAllTiles();
//    }
//
//    @PostMapping("/{id}/image")
//    public TileDto upload(
//        @PathVariable Long id,
//        @RequestParam("file") MultipartFile file
//    ) throws IOException {
//        return service.updateTile(id, file);
//    }
//}