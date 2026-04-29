package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.dto.CertificateResponse;
import com.tim12.pk_infrastructure.dto.IssueCertificateRequest;
import com.tim12.pk_infrastructure.service.CertificateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('CA_USER', 'ADMIN')")
    public ResponseEntity<CertificateResponse> issueCertificate(
            @RequestBody IssueCertificateRequest request,
            Authentication auth) {
        try {
            String callerOrg = getOrganizationFromAuth(auth);
            return ResponseEntity.ok(CertificateResponse.from(
                    certificateService.issueCertificate(request, callerOrg)
            ));
        } catch (IllegalArgumentException | SecurityException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/issuers")
    @PreAuthorize("hasAnyRole('CA_USER', 'ADMIN')")
    public ResponseEntity<List<CertificateResponse>> getAvailableIssuers(Authentication auth) {
        String callerOrg = getOrganizationFromAuth(auth);
        List<CertificateResponse> issuers = certificateService
                .getAvailableIssuers(callerOrg)
                .stream()
                .map(CertificateResponse::from)
                .toList();
        return ResponseEntity.ok(issuers);
    }

    private String getOrganizationFromAuth(Authentication auth) {
        return auth.getName();
    }
}