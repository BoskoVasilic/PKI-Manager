package com.tim12.pk_infrastructure.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ActivateRequestDTO {
    private String token;
    private String password;
}
