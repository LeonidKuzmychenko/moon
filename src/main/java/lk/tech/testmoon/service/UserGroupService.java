package lk.tech.testmoon.service;

import lk.tech.testmoon.model.User;
import lk.tech.testmoon.model.UserAreasWrapper;
import lk.tech.testmoon.model.UserGroup;
import lk.tech.testmoon.repository.UserAreasRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserGroupService {

    private final UserAreasRepository repository;

    public UserGroupService(UserAreasRepository repository) {
        this.repository = repository;
    }

    public List<UserGroup> getGroupsByUserId(int userId) {
        return repository.read().getUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst()
                .map(User::getGroups)
                .orElse(new ArrayList<>());
    }

    public Optional<UserGroup> getGroupById(int userId, int groupId) {
        return getGroupsByUserId(userId).stream()
                .filter(g -> g.getGroupId() == groupId)
                .findFirst();
    }

    public UserGroup createGroup(int userId, UserGroup group) {
        UserAreasWrapper wrapper = repository.read();
        User user = wrapper.getUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        if (user.getGroups() == null) {
            user.setGroups(new ArrayList<>());
        }
        user.getGroups().add(group);
        repository.save(wrapper);
        return group;
    }

    public UserGroup updateGroup(int userId, int groupId, UserGroup groupDetails) {
        UserAreasWrapper wrapper = repository.read();
        User user = wrapper.getUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));

        List<UserGroup> groups = user.getGroups();
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i).getGroupId() == groupId) {
                groupDetails.setGroupId(groupId);
                groups.set(i, groupDetails);
                repository.save(wrapper);
                return groupDetails;
            }
        }
        throw new RuntimeException("Group not found: " + groupId);
    }

    public void deleteGroup(int userId, int groupId) {
        UserAreasWrapper wrapper = repository.read();
        wrapper.getUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst()
                .ifPresent(user -> {
                    user.getGroups().removeIf(g -> g.getGroupId() == groupId);
                    repository.save(wrapper);
                });
    }

    public List<UserGroup> getAllGroups() {
        List<UserGroup> allGroups = new ArrayList<>();
        repository.read().getUsers().forEach(u -> allGroups.addAll(u.getGroups()));
        return allGroups;
    }
}
