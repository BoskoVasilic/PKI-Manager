package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.dtos.*;
import com.tim12.pk_infrastructure.service.AuthService;
import com.tim12.pk_infrastructure.service.AuthService.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/activate-ca")
    public void activateCaUser(@RequestBody ActivateRequestDTO request) {
        try {
            authService.activateCaUser(request.getToken(), request.getPassword());
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/validate-token")
    public void validate(@RequestParam("token") String token) {
        try {
            authService.validateToken(token);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResult result = authService.login(request.email(), request.password());
        return ResponseEntity.ok(new LoginResponse(result.token(), result.twoFaRequired()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    public record LoginRequest(String email, String password) {}
    public record LoginResponse(String accessToken, boolean twoFaRequired) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequestDTO req) {
        try {
            authService.register(req);
            return ResponseEntity.ok(Map.of(
                    "message", "Registration successful. Check your email for the activation link."
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/activate/challenge")
    public ResponseEntity<?> getActivationChallenge(@RequestParam String token) {
        try {
            ChallengeResponseDTO challenge = authService.getRegistrationChallenge(token);
            return ResponseEntity.ok(challenge);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/activate")
    public ResponseEntity<?> activateAccount(@RequestBody ActivateAccountRequestDTO req) {
        try {
            authService.activateAccount(req);
            return ResponseEntity.ok(Map.of("message", "Account activated successfully. You may now log in."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequestDTO req) {
        try {
            authService.initiateForgotPassword(req);
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok(Map.of(
                "message", "If an account with this email exists, we will send a password reset link."
        ));
    }

    @GetMapping("/forgot-password/challenge")
    public ResponseEntity<?> getForgotPasswordChallenge(@RequestParam String token) {
        try {
            ChallengeResponseDTO challenge = authService.getForgotPasswordChallenge(token);
            return ResponseEntity.ok(challenge);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/forgot-password/reset")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequestDTO req) {
        try {
            authService.resetPassword(req);
            return ResponseEntity.ok(Map.of("message", "Password has been changed successfully."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}