package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.ActivationToken;
import com.tim12.pk_infrastructure.model.Organization;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200/")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/activate-ca")
    public void activateCaUser(String token, String newPassword) {
        try {
            authService.activateCaUser(token, newPassword);
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
}
