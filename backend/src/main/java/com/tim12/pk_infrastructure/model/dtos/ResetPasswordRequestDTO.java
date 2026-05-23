package com.tim12.pk_infrastructure.model.dtos;

import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    private String token;
    private String decryptedChallenge;
    private String newPassword;
    private String confirmNewPassword;
}