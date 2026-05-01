package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.dto.CertificateDto;
import com.tim12.pk_infrastructure.dto.CertificateResponse;
import com.tim12.pk_infrastructure.dto.IssueCertificateRequest;
import com.tim12.pk_infrastructure.security.CustomUserDetails;
import com.tim12.pk_infrastructure.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('CA_USER', 'ADMIN')")
    public ResponseEntity<?> issueCertificate(
            @RequestBody IssueCertificateRequest request,
            Authentication auth) {
        try {
            String callerOrg = resolveOrganization(auth, request.getOrganization());
            return ResponseEntity.ok(CertificateResponse.from(
                    certificateService.issueCertificate(request, callerOrg)
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("message", "Internal error"));
        }
    }

    @GetMapping("/issuers")
    @PreAuthorize("hasAnyRole('CA_USER', 'ADMIN')")
    public ResponseEntity<List<CertificateResponse>> getAvailableIssuers(
            @RequestParam(required = false) String organization,
            Authentication auth) {
        String callerOrg = resolveOrganization(auth, organization);
        List<CertificateResponse> issuers = certificateService
                .getAvailableIssuers(callerOrg)
                .stream()
                .map(CertificateResponse::from)
                .toList();
        return ResponseEntity.ok(issuers);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CertificateDto>> getMyCertificates() {
        return ResponseEntity.ok(certificateService.getMyEndEntityCertificates());
    }

    @GetMapping("/my/{serialNumber}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CertificateDto> getMyCertificate(@PathVariable String serialNumber) {
        return ResponseEntity.ok(certificateService.getMyCertificateBySerial(serialNumber));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    private String resolveOrganization(Authentication auth, String requestedOrg) {
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            if (requestedOrg == null || requestedOrg.isBlank()) {
                throw new IllegalArgumentException("Admin must specify an organization.");
            }
            return requestedOrg;
        }

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();
        return userDetails.getOrganization();
    }
}