package lk.tech.testmoon.service;

import lk.tech.testmoon.model.User;
import lk.tech.testmoon.model.UserAreaConfig;
import lk.tech.testmoon.repository.UserAreaRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserAreaRepository repository;

    public UserService(UserAreaRepository repository) {
        this.repository = repository;
    }

    public List<User> getAllUsers() {
        UserAreaConfig config = repository.read();
        return config.getUsers() != null ? config.getUsers() : new ArrayList<>();
    }

    public Optional<User> getUserById(Long userId) {
        return getAllUsers().stream()
                .filter(u -> u.getUserId().equals(userId))
                .findFirst();
    }

    public User createUser(User user) {
        UserAreaConfig config = repository.read();
        if (config.getUsers() == null) {
            config.setUsers(new ArrayList<>());
        }
        config.getUsers().add(user);
        repository.write(config);
        return user;
    }

    public User updateUser(Long userId, User updatedUser) {
        UserAreaConfig config = repository.read();
        List<User> users = config.getUsers();
        if (users != null) {
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getUserId().equals(userId)) {
                    updatedUser.setUserId(userId);
                    users.set(i, updatedUser);
                    repository.write(config);
                    return updatedUser;
                }
            }
        }
        throw new RuntimeException("User not found with id: " + userId);
    }

    public void deleteUser(Long userId) {
        UserAreaConfig config = repository.read();
        List<User> users = config.getUsers();
        if (users != null) {
            users.removeIf(u -> u.getUserId().equals(userId));
            repository.write(config);
        }
    }
}
