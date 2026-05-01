//package com.tim12.pk_infrastructure.controller;
//
//import com.tim12.pk_infrastructure.model.dtos.csr.CsrRequestDto;
//import com.tim12.pk_infrastructure.model.dtos.csr.CsrResponseDto;
//import com.tim12.pk_infrastructure.model.dtos.csr.CsrUploadDto;
//import com.tim12.pk_infrastructure.service.CsrService;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.access.prepost.PreAuthorize;
//import org.springframework.web.bind.annotation.*;
//
//@RestController
//@RequestMapping("/api/csr")
//@RequiredArgsConstructor
//@CrossOrigin(origins = "http://localhost:4200")
//public class CsrController {
//
//    private final CsrService csrService;
//
//    /**
//     * Autogenerate opcija.
//     *
//     * POST /api/csr/autogenerate
//     *
//     * Server generiše RSA par ključeva, potpisuje EE sertifikat izabranim CA.
//     * Response sadrži i sertifikat i privatni ključ u PEM formatu.
//     * NAPOMENA: Privatni ključ se vraća JEDNOM i nije sačuvan na serveru!
//     */
//    @PostMapping("/autogenerate")
//    @PreAuthorize("hasRole('USER')")
//    public ResponseEntity<CsrResponseDto> autogenerate(@Valid @RequestBody CsrRequestDto request) {
//        try {
//            CsrResponseDto response = csrService.autogenerate(request);
//            return ResponseEntity.ok(response);
//        } catch (IllegalArgumentException | IllegalStateException e) {
//            return ResponseEntity.badRequest().build();
//        } catch (Exception e) {
//            throw new RuntimeException("Greška pri generisanju sertifikata: " + e.getMessage(), e);
//        }
//    }
//
//    /**
//     * Upload CSR opcija.
//     *
//     * POST /api/csr/upload
//     *
//     * Korisnik uploaduje PEM-enkodovan CSR koji je sam generisao (npr. openssl).
//     * Server parsuje CSR, potpisuje ga izabranim CA.
//     * Response sadrži SAMO sertifikat - privatni ključ je kod korisnika.
//     */
//    @PostMapping("/upload")
//    @PreAuthorize("hasRole('USER')")
//    public ResponseEntity<CsrResponseDto> uploadCsr(@RequestBody CsrUploadDto request) {
//        try {
//            CsrResponseDto response = csrService.uploadCsr(request);
//            return ResponseEntity.ok(response);
//        } catch (IllegalArgumentException | IllegalStateException e) {
//            return ResponseEntity.badRequest().build();
//        } catch (Exception e) {
//            throw new RuntimeException("Greška pri potpisivanju CSR-a: " + e.getMessage(), e);
//        }
//    }
//}