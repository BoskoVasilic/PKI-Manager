package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.model.dtos.csr.CsrRequestDto;
import com.tim12.pk_infrastructure.model.dtos.csr.CsrResponseDto;
import com.tim12.pk_infrastructure.model.dtos.csr.CsrUploadDto;
import com.tim12.pk_infrastructure.service.CsrService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/certificates/csr")
@RequiredArgsConstructor
public class CsrController {

    private final CsrService csrService;


    @GetMapping("/available-cas")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<CertificateDTO>> listAvailableCas() {
        return ResponseEntity.ok(csrService.listAvailableCas());
    }


    @PostMapping("/autogenerate")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> autogenerate(@Valid @RequestBody CsrRequestDto request) {
        try {
            CsrResponseDto response = csrService.autogenerate(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Error generating certificate: " + e.getMessage()));
        }
    }

    @PostMapping("/upload")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> uploadCsr(@Valid @RequestBody CsrUploadDto request) {
        try {
            CsrResponseDto response = csrService.uploadCsr(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Error signing CSR: " + e.getMessage()));
        }
    }
}