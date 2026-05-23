package com.tim12.pk_infrastructure.model.dtos;

import lombok.Data;

@Data
public class RegisterRequestDTO {
    private String email;
    private String password;
    private String confirmPassword;
    private String firstName;
    private String lastName;
    private String organizationName;

    private String publicKeyPem;
}