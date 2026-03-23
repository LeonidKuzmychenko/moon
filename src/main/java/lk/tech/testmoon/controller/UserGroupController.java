package lk.tech.testmoon.controller;

import lk.tech.testmoon.model.UserGroup;
import lk.tech.testmoon.service.UserGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/groups")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserGroupController {
    private final UserGroupService userGroupService;

    @GetMapping
    public List<UserGroup> getGroupsByUserId(@PathVariable Long userId) {
        return userGroupService.getGroupsByUserId(userId);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<UserGroup> getGroupById(@PathVariable Long userId, @PathVariable Long groupId) {
        return userGroupService.getGroupById(userId, groupId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public UserGroup createGroup(@PathVariable Long userId, @RequestBody UserGroup group) {
        return userGroupService.createGroup(userId, group);
    }

    @PutMapping("/{groupId}")
    public ResponseEntity<UserGroup> updateGroup(@PathVariable Long userId, @PathVariable Long groupId, @RequestBody UserGroup group) {
        UserGroup updated = userGroupService.updateGroup(userId, groupId, group);
        return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long userId, @PathVariable Long groupId) {
        return userGroupService.deleteGroup(userId, groupId) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
