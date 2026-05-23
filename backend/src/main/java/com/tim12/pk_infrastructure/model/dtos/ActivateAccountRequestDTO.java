package com.tim12.pk_infrastructure.model.dtos;

import lombok.Data;

@Data
public class ActivateAccountRequestDTO {
    private String token;
    private String decryptedChallenge;
}