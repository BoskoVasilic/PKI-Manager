package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.dto.CertificateResponse;
import com.tim12.pk_infrastructure.dto.IssueCertificateRequest;
import com.tim12.pk_infrastructure.security.CustomUserDetails;
import com.tim12.pk_infrastructure.service.CertificateService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

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

    /**
     * Admins can pass any org (from request body or query param).
     * CA users are always scoped to their own org, ignoring any passed value.
     */
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