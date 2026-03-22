package lk.tech.testmoon.service;

import lk.tech.testmoon.model.User;
import lk.tech.testmoon.model.UserAreaConfig;
import lk.tech.testmoon.model.UserGroup;
import lk.tech.testmoon.repository.UserAreaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserGroupService {

    private final UserAreaRepository repository;

    public UserGroupService(UserAreaRepository repository) {
        this.repository = repository;
    }

    public List<UserGroup> getGroupsForUser(Long userId) {
        UserAreaConfig config = repository.read();
        if (config.getUsers() == null) return new ArrayList<>();
        return config.getUsers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst()
                .map(User::getGroups)
                .orElse(new ArrayList<>());
    }

    public Optional<UserGroup> getGroupById(Long userId, Long groupId) {
        return getGroupsForUser(userId).stream()
                .filter(g -> g.getGroupId().equals(groupId))
                .findFirst();
    }

    public UserGroup createGroup(Long userId, UserGroup group) {
        UserAreaConfig config = repository.read();
        List<User> users = config.getUsers();
        if (users == null) {
            users = new ArrayList<>();
            config.setUsers(users);
        }
        for (User user : users) {
            if (user.getUserId().equals(userId)) {
                if (user.getGroups() == null) {
                    user.setGroups(new ArrayList<>());
                }
                user.getGroups().add(group);
                repository.write(config);
                return group;
            }
        }
        throw new RuntimeException("User not found with id: " + userId);
    }

    public UserGroup updateGroup(Long userId, Long groupId, UserGroup updatedGroup) {
        UserAreaConfig config = repository.read();
        List<User> users = config.getUsers();
        if (users != null) {
            for (User user : users) {
                if (user.getUserId().equals(userId)) {
                    List<UserGroup> groups = user.getGroups();
                    if (groups != null) {
                        for (int i = 0; i < groups.size(); i++) {
                            if (groups.get(i).getGroupId().equals(groupId)) {
                                updatedGroup.setGroupId(groupId);
                                groups.set(i, updatedGroup);
                                repository.write(config);
                                return updatedGroup;
                            }
                        }
                    }
                }
            }
        }
        throw new RuntimeException("Group not found with id: " + groupId + " for user: " + userId);
    }

    public void deleteGroup(Long userId, Long groupId) {
        UserAreaConfig config = repository.read();
        List<User> users = config.getUsers();
        if (users != null) {
            for (User user : users) {
                if (user.getUserId().equals(userId)) {
                    if (user.getGroups() != null) {
                        user.getGroups().removeIf(g -> g.getGroupId().equals(groupId));
                        repository.write(config);
                        return;
                    }
                }
            }
        }
    }
}
