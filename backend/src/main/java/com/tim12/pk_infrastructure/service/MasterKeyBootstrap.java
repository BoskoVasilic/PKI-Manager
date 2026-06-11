package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.MasterKey;
import com.tim12.pk_infrastructure.model.enums.MasterKeyStatus;
import com.tim12.pk_infrastructure.repository.MasterKeyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class MasterKeyBootstrap {

    private final MasterKeyRepository masterKeyRepository;
    private final KeyEncryptionService   keyEncryptionService;

    @Value("${pki.master.key}")
    private String bootstrapKeyBase64;

    @PostConstruct
    public void ensureActiveMasterKey() {
        if (masterKeyRepository.findByStatus(MasterKeyStatus.ACTIVE).isEmpty()) {
            log.info("No active master key in DB — seeding from bootstrap key");
            // Wrap the bootstrap key with itself so it's stored the same way as future keys
            String wrapped = keyEncryptionService.generateAndWrapNewMasterKey();
            // But for the first key, we actually want to wrap the *current* bootstrap key
            // so existing encrypted data still decrypts correctly.
            // Wrap the bootstrap key value using itself as the wrapping key:
            SecretKeySpec bk = new SecretKeySpec(
                    Base64.getDecoder().decode(bootstrapKeyBase64), "AES");
            String wrappedBootstrap = keyEncryptionService.encryptWithKey(bootstrapKeyBase64, bk);

            MasterKey first = MasterKey.builder()
                    .version(1)
                    .keyBase64(wrappedBootstrap)
                    .status(MasterKeyStatus.ACTIVE)
                    .createdAt(LocalDateTime.now())
                    .activatedAt(LocalDateTime.now())
                    .build();
            masterKeyRepository.save(first);
            log.info("Seeded master key v1 as ACTIVE");
        }
    }
}