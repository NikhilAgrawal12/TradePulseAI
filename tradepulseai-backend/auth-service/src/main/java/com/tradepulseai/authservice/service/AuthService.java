package com.tradepulseai.authservice.service;

import com.tradepulseai.authservice.dto.auth.LoginRequestDTO;
import com.tradepulseai.authservice.dto.auth.RegisterRequestDTO;
import com.tradepulseai.authservice.dto.credentials.CredentialsResponseDTO;
import com.tradepulseai.authservice.dto.credentials.UpdateCredentialsRequestDTO;
import com.tradepulseai.authservice.dto.credentials.UpdateCredentialsResponseDTO;
import com.tradepulseai.authservice.model.User;
import com.tradepulseai.authservice.util.JwtUtil;
import io.jsonwebtoken.JwtException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;


@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserService userService, PasswordEncoder passwordEncoder,  JwtUtil jwtUtil) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public Optional<String> authenticate(LoginRequestDTO loginRequestDTO) {
        return userService
                .findByEmail(loginRequestDTO.getEmail())
                .filter(u -> passwordEncoder.matches(loginRequestDTO.getPassword(),
                        u.getPassword()))
                .map(u -> jwtUtil.generateToken(u.getEmail(), u.getRole(), u.getUserId()));
    }

    public boolean validateToken(String token){
        try{
            jwtUtil.validateToken(token);
            return true;

        } catch (JwtException e){
            return false;
        }
    }

    public User register(RegisterRequestDTO registerRequestDTO) {
        String normalizedEmail = normalizeEmail(registerRequestDTO.getEmail());
        if (userService.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        // Encode password using BCryptPasswordEncoder bean
        String encodedPassword = passwordEncoder.encode(registerRequestDTO.getPassword());
        // Create user with role "User" as default
        return userService.createUser(normalizedEmail, encodedPassword, "User");
    }

    public CredentialsResponseDTO getCredentials(Long userId) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return new CredentialsResponseDTO(user.getUserId(), user.getEmail());
    }

    public UpdateCredentialsResponseDTO updateCredentials(Long userId, UpdateCredentialsRequestDTO request) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        String requestedEmail = request.getEmail();
        String normalizedEmail = requestedEmail == null ? user.getEmail() : normalizeEmail(requestedEmail);
        boolean emailChanged = !normalizedEmail.equals(user.getEmail());

        if (emailChanged) {
            Optional<User> existing = userService.findByEmail(normalizedEmail);
            if (existing.isPresent() && !existing.get().getUserId().equals(userId)) {
                throw new IllegalArgumentException("An account with this email already exists");
            }
            user.setEmail(normalizedEmail);
        }

        if (!emailChanged) {
            throw new IllegalArgumentException("No credential changes provided");
        }

        User saved = userService.save(user);
        String refreshedToken = jwtUtil.generateToken(saved.getEmail(), saved.getRole(), saved.getUserId());
        return new UpdateCredentialsResponseDTO(saved.getUserId(), saved.getEmail(), refreshedToken);
    }

    public Long extractUserId(String token) {
        try {
            return jwtUtil.extractUserId(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new JwtException("Invalid JWT");
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase();
    }


}
