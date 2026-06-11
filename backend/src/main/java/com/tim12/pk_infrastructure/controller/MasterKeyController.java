package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.repository.MasterKeyRepository;
import com.tim12.pk_infrastructure.service.MasterKeyRotationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/master-key")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MasterKeyController {

    private final MasterKeyRotationService rotationService;
    private final MasterKeyRepository masterKeyRepository;

    @PostMapping("/rotate")
    public ResponseEntity<String> rotate() {
        rotationService.rotateMasterKey();
        return ResponseEntity.ok("Master key rotated successfully");
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(masterKeyRepository.findAll());
    }
}