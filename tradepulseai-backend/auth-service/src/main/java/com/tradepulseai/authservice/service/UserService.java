package com.tradepulseai.authservice.service;

import com.tradepulseai.authservice.model.User;
import com.tradepulseai.authservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    public Optional<User> findByEmail(String email){
        if (email == null) {
            return Optional.empty();
        }
        return userRepository.findByEmail(email.trim().toLowerCase());
    }

    public boolean existsByEmail(String email) {
        if (email == null) {
            return false;
        }
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    public User createUser(String email, String encodedPassword, String role) {
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPassword(encodedPassword);
        user.setRole(role);
        return userRepository.save(user);
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}

