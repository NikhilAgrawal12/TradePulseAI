package com.tradepulseai.authservice.controller;

import com.tradepulseai.authservice.dto.auth.LoginRequestDTO;
import com.tradepulseai.authservice.dto.auth.LoginResponseDTO;
import com.tradepulseai.authservice.dto.auth.ForgotPasswordCodeRequestDTO;
import com.tradepulseai.authservice.dto.auth.ForgotPasswordCodeRequestResponseDTO;
import com.tradepulseai.authservice.dto.auth.ForgotPasswordCodeVerifyRequestDTO;
import com.tradepulseai.authservice.dto.auth.ForgotPasswordCodeVerifyResponseDTO;
import com.tradepulseai.authservice.dto.auth.ForgotPasswordResetRequestDTO;
import com.tradepulseai.authservice.dto.auth.ForgotPasswordResponseDTO;
import com.tradepulseai.authservice.dto.auth.RegisterRequestDTO;
import com.tradepulseai.authservice.dto.auth.RegisterResponseDTO;
import com.tradepulseai.authservice.dto.credentials.CredentialsResponseDTO;
import com.tradepulseai.authservice.dto.credentials.UpdateCredentialsRequestDTO;
import com.tradepulseai.authservice.dto.credentials.UpdateCredentialsResponseDTO;
import com.tradepulseai.authservice.model.User;
import com.tradepulseai.authservice.service.AuthService;
import com.tradepulseai.authservice.service.ForgotPasswordService;
import com.tradepulseai.authservice.service.UserService;
import io.jsonwebtoken.JwtException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final ForgotPasswordService forgotPasswordService;
    private final UserService userService;

    public AuthController(AuthService authService, ForgotPasswordService forgotPasswordService, UserService userService) {
        this.authService = authService;
        this.forgotPasswordService = forgotPasswordService;
        this.userService = userService;
    }

    @Operation(summary="Generate token on user login")
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginRequestDTO loginRequestDTO){

        Optional<String> tokenOptional = authService.authenticate(loginRequestDTO);

        if(tokenOptional.isEmpty()){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String token= tokenOptional.get();
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }

    @Operation(summary="Validate Token")
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String authHeader){

        // Authorization: Bearer <token>
        if(authHeader==null || !authHeader.startsWith("Bearer ")){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return authService.validateToken(authHeader.substring(7))
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @Operation(summary="Register a new user")
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequestDTO registerRequestDTO){
        try {
            User user = authService.register(registerRequestDTO);
            RegisterResponseDTO responseDTO = new RegisterResponseDTO(user.getUserId(), user.getEmail(), user.getRole());
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDTO);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message", e.getMessage()));
        } catch (DataIntegrityViolationException e) {
            log.warn("Registration conflict for email {}", registerRequestDTO.getEmail(), e);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of("message", "Unable to register user due to conflicting data"));
        } catch (DataAccessException e) {
            log.error("Registration failed due to database access issue for email {}", registerRequestDTO.getEmail(), e);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("message", "Registration is temporarily unavailable"));
        } catch (Exception e) {
            log.error("Unexpected error during registration for email {}", registerRequestDTO.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Registration failed due to an internal error"));
        }
    }

    @Operation(summary = "Send a 4-digit password reset code to the user email")
    @PostMapping("/forgot-password/request-code")
    public ResponseEntity<?> requestForgotPasswordCode(@Valid @RequestBody ForgotPasswordCodeRequestDTO request) {
        try {
            ForgotPasswordService.CodeRequestResult result = forgotPasswordService.requestCode(request.getEmail());
            if (result.status() == ForgotPasswordService.CodeRequestStatus.USER_NOT_FOUND) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ForgotPasswordResponseDTO(result.message()));
            }
            return ResponseEntity.ok(new ForgotPasswordCodeRequestResponseDTO(
                    result.message(),
                    forgotPasswordService.getCodeTtlSeconds(),
                    forgotPasswordService.getMaxAttempts()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ForgotPasswordResponseDTO(ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ForgotPasswordResponseDTO(ex.getMessage()));
        }
    }

    @Operation(summary = "Verify 4-digit password reset code")
    @PostMapping("/forgot-password/verify-code")
    public ResponseEntity<?> verifyForgotPasswordCode(@Valid @RequestBody ForgotPasswordCodeVerifyRequestDTO request) {
        ForgotPasswordService.CodeVerifyResult result = forgotPasswordService.verifyCode(request.getEmail(), request.getCode());

        return switch (result.status()) {
            case CODE_VERIFIED -> ResponseEntity.ok(
                    new ForgotPasswordCodeVerifyResponseDTO(result.message(), result.resetToken(), result.attemptsRemaining())
            );
            case CODE_MISMATCH -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ForgotPasswordCodeVerifyResponseDTO(result.message(), null, result.attemptsRemaining()));
            case ATTEMPTS_EXHAUSTED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ForgotPasswordCodeVerifyResponseDTO(result.message(), null, 0));
            case CODE_EXPIRED -> ResponseEntity.status(HttpStatus.GONE)
                    .body(new ForgotPasswordCodeVerifyResponseDTO(result.message(), null, 0));
            case CODE_INVALID -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ForgotPasswordCodeVerifyResponseDTO(result.message(), null, 0));
        };
    }

    @Operation(summary = "Reset password after successful code verification")
    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetForgotPassword(@Valid @RequestBody ForgotPasswordResetRequestDTO request) {
        ForgotPasswordService.ResetResult result = forgotPasswordService.resetPassword(
                request.getEmail(),
                request.getResetToken(),
                request.getNewPassword(),
                request.getConfirmPassword()
        );

        return switch (result.status()) {
            case RESET_SUCCESS -> ResponseEntity.ok(new ForgotPasswordResponseDTO(result.message()));
            case CODE_EXPIRED -> ResponseEntity.status(HttpStatus.GONE).body(new ForgotPasswordResponseDTO(result.message()));
            case CODE_INVALID -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ForgotPasswordResponseDTO(result.message()));
            case USER_NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ForgotPasswordResponseDTO(result.message()));
            case INVALID_PASSWORD, PASSWORD_MISMATCH, SAME_AS_OLD_PASSWORD -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ForgotPasswordResponseDTO(result.message()));
        };
    }

    @Operation(summary = "Get user by id")
    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        return userService.findById(userId)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(new RegisterResponseDTO(user.getUserId(), user.getEmail(), user.getRole())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("message", "User not found")));
    }

    @Operation(summary = "Get user by email")
    @GetMapping("/users/email/{email}")
    public ResponseEntity<?> getUserByEmail(@PathVariable String email) {
        return userService.findByEmail(email)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(new RegisterResponseDTO(user.getUserId(), user.getEmail(), user.getRole())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(java.util.Map.of("message", "User not found")));
    }

    @Operation(summary = "Get account credentials by user id")
    @GetMapping("/users/{userId}/credentials")
    public ResponseEntity<?> getCredentials(@PathVariable Long userId, @RequestHeader("Authorization") String authHeader) {
        try {
            authorizeUserId(authHeader, userId);
            CredentialsResponseDTO response = authService.getCredentials(userId);
            return ResponseEntity.ok(response);
        } catch (JwtException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            HttpStatus status = "User not found".equals(ex.getMessage()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    @Operation(summary = "Update account email")
    @PutMapping("/users/{userId}/credentials")
    public ResponseEntity<?> updateCredentials(
            @PathVariable Long userId,
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody UpdateCredentialsRequestDTO request
    ) {
        try {
            authorizeUserId(authHeader, userId);
            UpdateCredentialsResponseDTO response = authService.updateCredentials(userId, request);
            return ResponseEntity.ok(response);
        } catch (JwtException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(java.util.Map.of("message", ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            HttpStatus status = "An account with this email already exists".equals(ex.getMessage())
                    ? HttpStatus.CONFLICT
                    : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status).body(java.util.Map.of("message", ex.getMessage()));
        }
    }

    private void authorizeUserId(String authHeader, Long pathUserId) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new JwtException("Missing or invalid Authorization header");
        }

        Long tokenUserId = authService.extractUserId(authHeader.substring(7));
        if (!pathUserId.equals(tokenUserId)) {
            throw new JwtException("You are not allowed to access this account");
        }
    }

}
