package com.tradepulse.authservice.service;

import com.tradepulse.authservice.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ForgotPasswordService {

    private static final Logger log = LoggerFactory.getLogger(ForgotPasswordService.class);

    private static final int CODE_LENGTH = 4;
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration CODE_TTL = Duration.ofMinutes(2);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(5);

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final ConcurrentMap<String, ForgotPasswordChallenge> challengesByEmail = new ConcurrentHashMap<>();

    public ForgotPasswordService(
            UserService userService,
            PasswordEncoder passwordEncoder,
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${tradepulse.mail.from:no-reply@tradepulse.local}") String fromAddress
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
        this.clock = Clock.systemUTC();
    }

    public CodeRequestResult requestCode(String email) {
        String normalizedEmail = normalizeEmail(email);
        Optional<User> userOptional = userService.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            return new CodeRequestResult(CodeRequestStatus.USER_NOT_FOUND, "No account found with this email address.");
        }

        String code = generateCode();
        Instant now = Instant.now(clock);
        ForgotPasswordChallenge challenge = new ForgotPasswordChallenge(code, now.plus(CODE_TTL));
        challengesByEmail.put(normalizedEmail, challenge);

        sendCodeEmail(normalizedEmail, code);
        return new CodeRequestResult(CodeRequestStatus.CODE_SENT, "Verification code sent to your email.");
    }

    public CodeVerifyResult verifyCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        ForgotPasswordChallenge challenge = challengesByEmail.get(normalizedEmail);
        if (challenge == null) {
            return new CodeVerifyResult(CodeVerifyStatus.CODE_INVALID, "Code is invalid. Please request a new code.", null, 0);
        }

        Instant now = Instant.now(clock);
        if (now.isAfter(challenge.codeExpiresAt())) {
            challengesByEmail.remove(normalizedEmail);
            return new CodeVerifyResult(CodeVerifyStatus.CODE_EXPIRED, "Code expired. Please start again.", null, 0);
        }

        if (!challenge.code().equals(code)) {
            int attemptsUsed = challenge.incrementAttempts();
            int attemptsRemaining = Math.max(MAX_ATTEMPTS - attemptsUsed, 0);
            if (attemptsRemaining == 0) {
                challengesByEmail.remove(normalizedEmail);
                return new CodeVerifyResult(CodeVerifyStatus.ATTEMPTS_EXHAUSTED, "Code is invalid. Please sign in and try forgot password again.", null, 0);
            }
            return new CodeVerifyResult(CodeVerifyStatus.CODE_MISMATCH, "Code did not match.", null, attemptsRemaining);
        }

        String resetToken = UUID.randomUUID().toString();
        challenge.markVerified(resetToken, now.plus(RESET_TOKEN_TTL));
        return new CodeVerifyResult(CodeVerifyStatus.CODE_VERIFIED, "Code verified. Please set your new password.", resetToken, MAX_ATTEMPTS - challenge.attemptsUsed());
    }

    public ResetResult resetPassword(String email, String resetToken, String newPassword, String confirmPassword) {
        String normalizedEmail = normalizeEmail(email);
        ForgotPasswordChallenge challenge = challengesByEmail.get(normalizedEmail);
        if (challenge == null) {
            return new ResetResult(ResetStatus.CODE_INVALID, "Code is invalid. Please request a new code.");
        }

        // After code verification, only the reset token TTL matters — do NOT check codeExpiresAt here.
        if (!challenge.isVerified() || challenge.resetToken() == null || !challenge.resetToken().equals(resetToken)) {
            return new ResetResult(ResetStatus.CODE_INVALID, "Verification not completed. Please verify the code first.");
        }

        Instant now = Instant.now(clock);
        if (challenge.resetTokenExpiresAt() == null || now.isAfter(challenge.resetTokenExpiresAt())) {
            challengesByEmail.remove(normalizedEmail);
            return new ResetResult(ResetStatus.CODE_EXPIRED, "Reset session expired. Please start again.");
        }

        if (newPassword == null || newPassword.length() < 8) {
            return new ResetResult(ResetStatus.INVALID_PASSWORD, "New password must be at least 8 characters.");
        }

        if (!newPassword.equals(confirmPassword)) {
            return new ResetResult(ResetStatus.PASSWORD_MISMATCH, "New password and confirm password do not match.");
        }

        Optional<User> userOptional = userService.findByEmail(normalizedEmail);
        if (userOptional.isEmpty()) {
            challengesByEmail.remove(normalizedEmail);
            return new ResetResult(ResetStatus.USER_NOT_FOUND, "No account found with this email address.");
        }

        User user = userOptional.get();
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            return new ResetResult(ResetStatus.SAME_AS_OLD_PASSWORD, "New password must be different from your current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userService.save(user);
        challengesByEmail.remove(normalizedEmail);
        return new ResetResult(ResetStatus.RESET_SUCCESS, "Password updated successfully. Please sign in.");
    }

    public int getMaxAttempts() {
        return MAX_ATTEMPTS;
    }

    public int getCodeTtlSeconds() {
        return (int) CODE_TTL.toSeconds();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        return email.trim().toLowerCase();
    }

    private String generateCode() {
        int max = (int) Math.pow(10, CODE_LENGTH);
        return String.format("%0" + CODE_LENGTH + "d", random.nextInt(max));
    }

    private void sendCodeEmail(String email, String code) {
        String subject = "TradePulse password reset code";
        String body = "Your TradePulse verification code is " + code + ".";

        if (mailSender == null) {
            log.error("Mail sender is not configured. Cannot deliver forgot-password code to {}", email);
            throw new IllegalStateException("Email delivery is not configured. Please contact support.");
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send forgot-password email to {}", email, ex);
            throw new IllegalStateException("Unable to send verification code. Please try again.");
        }
    }

    public enum CodeRequestStatus {
        CODE_SENT,
        USER_NOT_FOUND
    }

    public record CodeRequestResult(CodeRequestStatus status, String message) {}

    public enum CodeVerifyStatus {
        CODE_VERIFIED,
        CODE_MISMATCH,
        CODE_EXPIRED,
        ATTEMPTS_EXHAUSTED,
        CODE_INVALID
    }

    public record CodeVerifyResult(CodeVerifyStatus status, String message, String resetToken, int attemptsRemaining) {}

    public enum ResetStatus {
        RESET_SUCCESS,
        CODE_EXPIRED,
        CODE_INVALID,
        USER_NOT_FOUND,
        INVALID_PASSWORD,
        PASSWORD_MISMATCH,
        SAME_AS_OLD_PASSWORD
    }

    public record ResetResult(ResetStatus status, String message) {}

    private static final class ForgotPasswordChallenge {
        private final String code;
        private final Instant codeExpiresAt;
        private int attemptsUsed;
        private boolean verified;
        private String resetToken;
        private Instant resetTokenExpiresAt;

        private ForgotPasswordChallenge(String code, Instant codeExpiresAt) {
            this.code = code;
            this.codeExpiresAt = codeExpiresAt;
        }

        private synchronized int incrementAttempts() {
            attemptsUsed += 1;
            return attemptsUsed;
        }

        private synchronized void markVerified(String token, Instant tokenExpiresAt) {
            verified = true;
            resetToken = token;
            resetTokenExpiresAt = tokenExpiresAt;
        }

        private synchronized String code() {
            return code;
        }

        private synchronized Instant codeExpiresAt() {
            return codeExpiresAt;
        }

        private synchronized int attemptsUsed() {
            return attemptsUsed;
        }

        private synchronized boolean isVerified() {
            return verified;
        }

        private synchronized String resetToken() {
            return resetToken;
        }

        private synchronized Instant resetTokenExpiresAt() {
            return resetTokenExpiresAt;
        }
    }
}
