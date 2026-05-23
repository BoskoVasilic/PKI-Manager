package com.tim12.pk_infrastructure.model.dtos;

import lombok.Data;
import lombok.AllArgsConstructor;

@Data
@AllArgsConstructor
public class ChallengeResponseDTO {
    private String encryptedChallenge;
    private String token;
    private boolean twoFactorEnabled;
}
