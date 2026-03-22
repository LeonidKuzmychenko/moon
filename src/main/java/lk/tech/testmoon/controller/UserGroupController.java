package lk.tech.testmoon.controller;

import lk.tech.testmoon.model.UserGroup;
import lk.tech.testmoon.service.UserGroupService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/groups")
public class UserGroupController {

    private final UserGroupService userGroupService;

    public UserGroupController(UserGroupService userGroupService) {
        this.userGroupService = userGroupService;
    }

    @GetMapping
    public List<UserGroup> getGroupsForUser(@PathVariable Long userId) {
        return userGroupService.getGroupsForUser(userId);
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<UserGroup> getGroupById(@PathVariable Long userId, @PathVariable Long groupId) {
        return userGroupService.getGroupById(userId, groupId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserGroup createGroup(@PathVariable Long userId, @RequestBody UserGroup group) {
        return userGroupService.createGroup(userId, group);
    }

    @PutMapping("/{groupId}")
    public UserGroup updateGroup(@PathVariable Long userId, @PathVariable Long groupId, @RequestBody UserGroup group) {
        return userGroupService.updateGroup(userId, groupId, group);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long userId, @PathVariable Long groupId) {
        userGroupService.deleteGroup(userId, groupId);
    }
}
