package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.dtos.ActivateRequestDTO;
import com.tim12.pk_infrastructure.service.AuthService;
import com.tim12.pk_infrastructure.service.AuthService.LoginResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.BadCredentialsException;

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
        return ResponseEntity.ok(
                new LoginResponse(result.accessToken(), result.refreshToken(), result.twoFaRequired())
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        LoginResult result = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(
                new LoginResponse(result.accessToken(), result.refreshToken(), false)
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    public record LoginRequest(String email, String password) {}
    public record RefreshRequest(String refreshToken) {}
    public record LoginResponse(String accessToken, String refreshToken, boolean twoFaRequired) {}
}