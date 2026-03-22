package lk.tech.testmoon.model;

import lombok.Data;
import java.util.List;

@Data
public class UserGroup {
    private Long groupId;
    private String url;
    private String tile;
    private List<Integer> areaIds;
}
