package lk.tech.testmoon.service;

import lk.tech.testmoon.model.User;
import lk.tech.testmoon.model.UserAreasWrapper;
import lk.tech.testmoon.repository.UserAreasRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    private final UserAreasRepository repository;

    public UserService(UserAreasRepository repository) {
        this.repository = repository;
    }

    public List<User> getAllUsers() {
        return repository.read().getUsers();
    }

    public Optional<User> getUserById(int userId) {
        return repository.read().getUsers().stream()
                .filter(u -> u.getUserId() == userId)
                .findFirst();
    }

    public User createUser(User user) {
        UserAreasWrapper wrapper = repository.read();
        wrapper.getUsers().add(user);
        repository.save(wrapper);
        return user;
    }

    public User updateUser(int userId, User userDetails) {
        UserAreasWrapper wrapper = repository.read();
        List<User> users = wrapper.getUsers();
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserId() == userId) {
                userDetails.setUserId(userId);
                users.set(i, userDetails);
                repository.save(wrapper);
                return userDetails;
            }
        }
        throw new RuntimeException("User not found: " + userId);
    }

    public void deleteUser(int userId) {
        UserAreasWrapper wrapper = repository.read();
        wrapper.getUsers().removeIf(u -> u.getUserId() == userId);
        repository.save(wrapper);
    }
}
