package com.pm.authenticationservice.service;

import com.pm.authenticationservice.model.AuthUser;
import com.pm.authenticationservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository){
        this.userRepository=userRepository;
    }

    public Optional<AuthUser> findByEmail(String email){
        return userRepository.findByEmail(email);


    }
}
