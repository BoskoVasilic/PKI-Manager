package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.service.TwoFactorService;
import com.tim12.pk_infrastructure.service.TwoFactorService.SetupResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/2fa")
@RequiredArgsConstructor
public class TwoFactorController {

    private final TwoFactorService twoFactorService;

   @PostMapping("/setup")
    public ResponseEntity<SetupResponse> setup(@AuthenticationPrincipal UserDetails user) {
        SetupResponse response = twoFactorService.setup(user.getUsername());
        return ResponseEntity.ok(response);
    }

   @PostMapping("/enable")
    public ResponseEntity<Void> enable(@AuthenticationPrincipal UserDetails user,
                                       @RequestBody EnableRequest request) {
        twoFactorService.enable(user.getUsername(), request.secret(), request.code());
        return ResponseEntity.ok().build();
    }
    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest request) {
        String accessToken = twoFactorService.verify(request.preAuthToken(), request.code());
        return ResponseEntity.ok(new VerifyResponse(accessToken));
    }

  @PostMapping("/disable")
    public ResponseEntity<Void> disable(@AuthenticationPrincipal UserDetails user,
                                        @RequestBody DisableRequest request) {
        twoFactorService.disable(user.getUsername(), request.code());
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    public record EnableRequest(String secret, String code) {}
    public record VerifyRequest(String preAuthToken, String code) {}
    public record VerifyResponse(String accessToken) {}
    public record DisableRequest(String code) {}
}