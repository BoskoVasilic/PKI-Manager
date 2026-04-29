package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.dto.CertificateDto;
import com.tim12.pk_infrastructure.service.CertificateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:4200")
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
}