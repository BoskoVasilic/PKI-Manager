package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.service.CrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/crl")
@RequiredArgsConstructor
public class CrlController {

    private final CrlService crlService;

    @GetMapping(value = "/{issuerSerial}/crl.crl", produces = "application/pkix-crl")
    public ResponseEntity<?> getCrlForIssuer(@PathVariable String issuerSerial) {
        try {
            byte[] crlBytes = crlService.generateCrl(issuerSerial);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/pkix-crl"));
            headers.setContentLength(crlBytes.length);
            headers.setCacheControl("max-age=3600");
            headers.set("Content-Disposition", "attachment; filename=\"crl.crl\"");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(crlBytes);

        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", "Failed to generate CRL: " + e.getMessage()));
        }
    }
}