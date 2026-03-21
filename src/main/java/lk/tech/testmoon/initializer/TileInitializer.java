//package lk.tech.testmoon.initializer;
//
//import jakarta.annotation.PostConstruct;
//import lk.tech.testmoon.model.Tile;
//import lk.tech.testmoon.repository.TileRepository;
//import org.springframework.stereotype.Component;
//
//import java.time.Instant;
//
//@Component
//public class TileInitializer {
//
//    private final TileRepository repo;
//
//    public TileInitializer(TileRepository repo) {
//        this.repo = repo;
//    }
//
//    @PostConstruct
//    public void init() {
//        if (repo.count() == 0) {
//            int totalTiles = 4096; // например
//
//            for (long i = 0; i < totalTiles; i++) {
//                Tile t = new Tile();
//                t.setId(i);
//                t.setImagePath(null);
//                t.setUpdatedAt(Instant.now());
//                repo.save(t);
//            }
//        }
//    }
//}