//package lk.tech.testmoon.service;
//
//import lk.tech.testmoon.dto.TileDto;
//import lk.tech.testmoon.model.Tile;
//import lk.tech.testmoon.repository.TileRepository;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.Instant;
//import java.util.List;
//
//@Service
//public class TileService {
//
//    private final TileRepository repo;
//
//    public TileService(TileRepository repo) {
//        this.repo = repo;
//    }
//
//    public List<TileDto> getAllTiles() {
//        return repo.findAll().stream()
//            .map(t -> new TileDto(
//                t.getId(),
//                t.getImagePath(),
//                t.getUpdatedAt()
//            ))
//            .toList();
//    }
//
//    public TileDto updateTile(Long id, MultipartFile file) throws IOException {
//        Tile tile = repo.findById(id)
//            .orElseThrow();
//
//        String filename = saveFile(id, file);
//
//        tile.setImagePath("/images/" + filename);
//        tile.setUpdatedAt(Instant.now());
//
//        repo.save(tile);
//
//        return new TileDto(
//            tile.getId(),
//            tile.getImagePath(),
//            tile.getUpdatedAt()
//        );
//    }
//
//    private String saveFile(Long id, MultipartFile file) throws IOException {
//        String filename = "tile_" + id + ".png";
//        Path path = Paths.get("storage/images/" + filename);
//
//        Files.createDirectories(path.getParent());
//        Files.write(path, file.getBytes());
//
//        return filename;
//    }
//}