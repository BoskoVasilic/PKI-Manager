package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.MasterKey;
import com.tim12.pk_infrastructure.model.enums.MasterKeyStatus;
import com.tim12.pk_infrastructure.repository.CertificateRepository;
import com.tim12.pk_infrastructure.repository.MasterKeyRepository;
import com.tim12.pk_infrastructure.repository.OrganizatioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class MasterKeyRotationService {

    private final MasterKeyRepository    masterKeyRepository;
    private final CertificateRepository  certificateRepository;
    private final OrganizatioRepository  organizationRepository;
    private final KeyEncryptionService   keyEncryptionService;

    /**
     * Scheduled rotation — runs every 6 months.
     * Cron: "0 0 2 1 *\6 *"  = 02:00 on the 1st of every 6th month
     */
    @Scheduled(cron = "0 0 2 1 */6 *")
    public void scheduledRotation() {
        log.info("Starting scheduled master key rotation");
        rotateMasterKey();
    }

    /**
     * Core rotation. Can also be called from an admin REST endpoint.
     *
     * Steps:
     *  1. Generate a new master key, save as PENDING.
     *  2. Re-encrypt every Organization.keyStorePassword.
     *  3. Re-encrypt every Certificate.encryptedPrivateKey.
     *  4. Mark new key ACTIVE, old key RETIRED.
     */
    @Transactional
    public void rotateMasterKey() {
        // 1. Find current active key
        MasterKey oldKeyEntity = masterKeyRepository.findByStatus(MasterKeyStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No active master key to rotate"));

        SecretKeySpec oldKey = keyEncryptionService.unwrapMasterKey(oldKeyEntity.getKeyBase64());

        // 2. Generate + persist new key as PENDING
        String wrappedNewKey = keyEncryptionService.generateAndWrapNewMasterKey();
        SecretKeySpec newKey = keyEncryptionService.unwrapMasterKey(wrappedNewKey);

        int newVersion = oldKeyEntity.getVersion() + 1;
        MasterKey newKeyEntity = MasterKey.builder()
                .version(newVersion)
                .keyBase64(wrappedNewKey)
                .status(MasterKeyStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        masterKeyRepository.save(newKeyEntity);

        log.info("Rotating from master key v{} → v{}", oldKeyEntity.getVersion(), newVersion);

        // 3a. Re-encrypt Organization keystore passwords
        var orgs = organizationRepository.findAll();
        for (var org : orgs) {
            if (org.getKeyStorePassword() != null) {
                String reEncrypted = keyEncryptionService.reEncrypt(
                        org.getKeyStorePassword(), oldKey, newKey);
                org.setKeyStorePassword(reEncrypted);
            }
        }
        organizationRepository.saveAll(orgs);
        log.info("Re-encrypted {} organization keystore passwords", orgs.size());

        // 3b. Re-encrypt Certificate private keys
        var certs = certificateRepository.findAll();
        int reEncryptedCerts = 0;
        for (var cert : certs) {
            if (cert.getEncryptedPrivateKey() != null) {
                String reEncrypted = keyEncryptionService.reEncrypt(
                        cert.getEncryptedPrivateKey(), oldKey, newKey);
                cert.setEncryptedPrivateKey(reEncrypted);
                reEncryptedCerts++;
            }
        }
        certificateRepository.saveAll(certs);
        log.info("Re-encrypted {} certificate private keys", reEncryptedCerts);

        // 4. Swap key statuses
        newKeyEntity.setStatus(MasterKeyStatus.ACTIVE);
        newKeyEntity.setActivatedAt(LocalDateTime.now());
        masterKeyRepository.save(newKeyEntity);

        oldKeyEntity.setStatus(MasterKeyStatus.RETIRED);
        oldKeyEntity.setRetiredAt(LocalDateTime.now());
        masterKeyRepository.save(oldKeyEntity);

        log.info("Master key rotation complete. v{} is now active.", newVersion);
    }
}