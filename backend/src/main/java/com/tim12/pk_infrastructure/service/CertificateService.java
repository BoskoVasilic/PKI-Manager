package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.dto.CertificateDto;
import com.tim12.pk_infrastructure.model.Certificate;
import com.tim12.pk_infrastructure.model.CertificateType;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;

    // CustomUserDetailsService vraca Spring User (ne nas custom User),
    // pa vadimo email iz principala i loadujemo iz baze
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    public List<CertificateDto> getMyEndEntityCertificates() {
        User currentUser = getCurrentUser();
        return certificateRepository
                .findByOwnerAndType(currentUser, CertificateType.END_ENTITY)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public CertificateDto getMyCertificateBySerial(String serialNumber) {
        User currentUser = getCurrentUser();
        Certificate cert = certificateRepository
                .findBySerialNumberAndOwner(serialNumber, currentUser)
                .orElseThrow(() -> new RuntimeException("Certificate not found"));
        return toDto(cert);
    }

    private CertificateDto toDto(Certificate c) {
        return CertificateDto.builder()
                .serialNumber(c.getSerialNumber())
                .subjectCN(c.getSubjectCN())
                .subjectO(c.getSubjectO())
                .subjectOU(c.getSubjectOU())
                .subjectC(c.getSubjectC())
                .subjectEmail(c.getSubjectEmail())
                .issuerCN(c.getIssuerCN())
                .validFrom(c.getValidFrom())
                .validTo(c.getValidTo())
                .type(c.getType())
                .status(c.getStatus())
                .revocationReason(c.getRevocationReason())
                .build();
    }
}