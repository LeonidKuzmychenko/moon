package lk.tech.testmoon.service;

import lk.tech.testmoon.model.User;
import lk.tech.testmoon.model.UserGroup;
import lk.tech.testmoon.repository.UserAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserGroupService {
    private final UserService userService;
    private final UserAreaRepository repository;

    public List<UserGroup> getGroupsByUserId(Long userId) {
        return userService.getUserById(userId)
                .map(User::getGroups)
                .orElse(new ArrayList<>());
    }

    public Optional<UserGroup> getGroupById(Long userId, Long groupId) {
        return getGroupsByUserId(userId).stream()
                .filter(g -> g.getGroupId().equals(groupId))
                .findFirst();
    }

    public UserGroup createGroup(Long userId, UserGroup group) {
        userService.getUserById(userId).ifPresent(user -> {
            if (user.getGroups() == null) user.setGroups(new ArrayList<>());
            user.getGroups().add(group);
            userService.updateUser(userId, user);
        });
        return group;
    }

    public UserGroup updateGroup(Long userId, Long groupId, UserGroup updatedGroup) {
        Optional<User> userOpt = userService.getUserById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getGroups() != null) {
                for (int i = 0; i < user.getGroups().size(); i++) {
                    if (user.getGroups().get(i).getGroupId().equals(groupId)) {
                        updatedGroup.setGroupId(groupId);
                        user.getGroups().set(i, updatedGroup);
                        userService.updateUser(userId, user);
                        return updatedGroup;
                    }
                }
            }
        }
        return null;
    }

    public boolean deleteGroup(Long userId, Long groupId) {
        Optional<User> userOpt = userService.getUserById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getGroups() != null) {
                boolean removed = user.getGroups().removeIf(g -> g.getGroupId().equals(groupId));
                if (removed) {
                    userService.updateUser(userId, user);
                    return true;
                }
            }
        }
        return false;
    }
}
