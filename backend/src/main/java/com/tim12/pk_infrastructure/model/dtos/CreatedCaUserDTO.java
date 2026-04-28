package com.tim12.pk_infrastructure.model.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatedCaUserDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Long organizationId;
    private String organizationName;
}
