package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.dto.CertificateDto;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.model.dtos.IssueCertificateRequest;
import com.tim12.pk_infrastructure.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;

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
}