package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.model.dtos.IssueCertificateRequest;
import com.tim12.pk_infrastructure.model.dtos.IssueCertificateRequestCA;
import com.tim12.pk_infrastructure.model.dtos.RevokeRequestDTO;
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
            @RequestBody IssueCertificateRequestCA request,
            Authentication auth) {
        try {
            return ResponseEntity.ok(certificateService.issueCertificate(request));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            System.out.println("\n\nError issuing certificate: " + e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "Internal error"));
        }
    }

    @GetMapping("/my/issuers")
    @PreAuthorize("hasAnyRole('CA_USER', 'ADMIN')")
    public ResponseEntity<List<CertificateDTO>> getAllMyAvailableIssuers() {
        return ResponseEntity.ok(certificateService.getAllMyAvailableIssuers());
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CertificateDTO>> getMyCertificates() {
        return ResponseEntity.ok(certificateService.getMyEndEntityCertificates());
    }

    @GetMapping("/my/{serialNumber}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<CertificateDTO> getMyCertificate(@PathVariable String serialNumber) {
        return ResponseEntity.ok(certificateService.getMyCertificateBySerial(serialNumber));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleNotFound(RuntimeException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @GetMapping("/issuers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CertificateDTO>> getAvailableIssuers() {
        return ResponseEntity.ok(certificateService.getAvailableIssuers());
    }

    @GetMapping("/{serialNumber}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CertificateDTO> getCertificate(@PathVariable String serialNumber) {
        return ResponseEntity.ok(certificateService.getCertificateBySerial(serialNumber));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> issueCertificate(@RequestBody IssueCertificateRequest request) {
        try {
            Certificate issued = certificateService.issueCertificate(request);
            return ResponseEntity.ok(issued);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CertificateDTO>> getAllCertificates() {
        return ResponseEntity.ok(certificateService.getAllCertificates());
    }

    record ErrorResponse(String message) {}
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

    @PutMapping("/{serialNumber}/revoke")
    public ResponseEntity<?> revokeCertificate(
            @PathVariable String serialNumber,
            @RequestBody RevokeRequestDTO revokeRequest) {
        try {
            Certificate revoked = certificateService.revokeCertificate(
                    serialNumber, String.valueOf(revokeRequest.getReason()));
            return ResponseEntity.ok(revoked);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/revoked")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<CertificateDTO>> getRevokedCertificates() {
        return ResponseEntity.ok(certificateService.getRevokedCertificates());
    }
}