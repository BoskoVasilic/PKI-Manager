package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.dtos.CertificateDTO;
import com.tim12.pk_infrastructure.service.CertificateDownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateDownloadController {

    private final CertificateDownloadService downloadService;

    @GetMapping("/{serialNumber}/download/pem")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAsPem(@PathVariable String serialNumber) {
        try {
            String pem = downloadService.downloadAsPem(serialNumber);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-pem-file"));
            headers.setContentDispositionFormData(
                    "attachment",
                    "certificate-" + serialNumber + ".pem"
            );

            return new ResponseEntity<>(pem.getBytes(), headers, HttpStatus.OK);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/{serialNumber}/download/cer")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAsCer(@PathVariable String serialNumber) {
        try {
            byte[] derBytes = downloadService.downloadAsCer(serialNumber);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/pkix-cert"));
            headers.setContentDispositionFormData(
                    "attachment",
                    "certificate-" + serialNumber + ".cer"
            );

            return new ResponseEntity<>(derBytes, headers, HttpStatus.OK);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage().getBytes());
        }
    }

    @GetMapping("/downloadable")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CertificateDTO>> getDownloadableCertificates() {
        try {
            List<CertificateDTO> dtos = downloadService.getDownloadableCertificates()
                    .stream()
                    .map(this::toDto)
                    .toList();
            return ResponseEntity.ok(dtos);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @GetMapping("/{serialNumber}/download/p12")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAsP12(
            @PathVariable String serialNumber,
            @RequestParam String exportPassword) {
        try {
            byte[] ksBytes = downloadService.downloadAsP12(
                    serialNumber, exportPassword.toCharArray());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-pkcs12"));
            headers.setContentDispositionFormData(
                    "attachment",
                    "certificate-" + serialNumber + ".p12"
            );

            return new ResponseEntity<>(ksBytes, headers, HttpStatus.OK);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage().getBytes());
        }
    }

    @GetMapping("/{serialNumber}/download/jks")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadAsJks(
            @PathVariable String serialNumber,
            @RequestParam String exportPassword) {
        try {
            byte[] ksBytes = downloadService.downloadAsJks(
                    serialNumber, exportPassword.toCharArray());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-java-keystore"));
            headers.setContentDispositionFormData(
                    "attachment",
                    "certificate-" + serialNumber + ".jks"
            );

            return new ResponseEntity<>(ksBytes, headers, HttpStatus.OK);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage().getBytes());
        }
    }

    private CertificateDTO toDto(Certificate c) {
        return CertificateDTO.builder()
                .serialNumber(c.getSerialNumber())
                .commonName(c.getSubjectCN())
                .organization(c.getSubjectO())
                .organizationUnit(c.getSubjectOU())
                .country(c.getSubjectC())
                .email(c.getSubjectEmail())
                .validFrom(String.valueOf(c.getValidFrom()))
                .validTo(String.valueOf(c.getValidTo()))
                .type(c.getType())
                .revoked(c.isRevoked())
                .issuerSerialNumber(c.getIssuerSerialNumber())
                .privateKeyAvailable(c.getEncryptedPrivateKey() != null 
                    && !c.getEncryptedPrivateKey().isBlank())
                .build();
    }
}