package lk.tech.testmoon.controller;

import lk.tech.testmoon.model.UserGroup;
import lk.tech.testmoon.service.UserGroupService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users/{userId}/groups")
public class UserGroupController {

    private final UserGroupService service;

    public UserGroupController(UserGroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserGroup> getGroupsByUserId(@PathVariable int userId) {
        return service.getGroupsByUserId(userId);
    }

    @GetMapping("/{groupId}")
    public UserGroup getGroupById(@PathVariable int userId, @PathVariable int groupId) {
        return service.getGroupById(userId, groupId).orElseThrow(() -> new RuntimeException("Group not found"));
    }

    @PostMapping
    public UserGroup createGroup(@PathVariable int userId, @RequestBody UserGroup group) {
        return service.createGroup(userId, group);
    }

    @PutMapping("/{groupId}")
    public UserGroup updateGroup(@PathVariable int userId, @PathVariable int groupId, @RequestBody UserGroup group) {
        return service.updateGroup(userId, groupId, group);
    }

    @DeleteMapping("/{groupId}")
    public void deleteGroup(@PathVariable int userId, @PathVariable int groupId) {
        service.deleteGroup(userId, groupId);
    }
}
