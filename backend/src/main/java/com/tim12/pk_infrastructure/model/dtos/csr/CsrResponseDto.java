
package com.tim12.pk_infrastructure.model.dtos.csr;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CsrResponseDto {
    private String serialNumber;
    private String certificatePem;
    private String keystoreBase64;    // Base64-encoded JKS (autogenerate only, one-time)
    private String keystorePassword;  // Password to open the JKS
    private String message;
}
 