package lk.tech.testmoon.service;

import lk.tech.testmoon.model.User;
import lk.tech.testmoon.model.UserAreaConfig;
import lk.tech.testmoon.repository.UserAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserAreaRepository repository;

    public List<User> getAllUsers() {
        UserAreaConfig config = repository.findAll();
        return config.getUsers() != null ? config.getUsers() : new ArrayList<>();
    }

    public Optional<User> getUserById(Long userId) {
        return getAllUsers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst();
    }

    public User createUser(User user) {
        UserAreaConfig config = repository.findAll();
        if (config.getUsers() == null) config.setUsers(new ArrayList<>());
        config.getUsers().add(user);
        repository.save(config);
        return user;
    }

    public User updateUser(Long userId, User updatedUser) {
        UserAreaConfig config = repository.findAll();
        if (config.getUsers() != null) {
            for (int i = 0; i < config.getUsers().size(); i++) {
                if (config.getUsers().get(i).getUserId().equals(userId)) {
                    updatedUser.setUserId(userId);
                    config.getUsers().set(i, updatedUser);
                    repository.save(config);
                    return updatedUser;
                }
            }
        }
        return null;
    }

    public boolean deleteUser(Long userId) {
        UserAreaConfig config = repository.findAll();
        if (config.getUsers() != null) {
            boolean removed = config.getUsers().removeIf(u -> u.getUserId().equals(userId));
            if (removed) {
                repository.save(config);
                return true;
            }
        }
        return false;
    }
}
