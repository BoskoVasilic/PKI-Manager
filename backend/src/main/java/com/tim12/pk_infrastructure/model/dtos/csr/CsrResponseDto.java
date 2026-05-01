package com.tim12.pk_infrastructure.model.dtos.csr;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO za CSR operacije.
 *
 * Za autogenerate: sadrži i certificatePem i privateKeyPem
 *   - privateKeyPem se vraća JEDNOM i ne čuva se na serveru.
 *
 * Za upload CSR: sadrži samo certificatePem (privatni ključ je kod korisnika).
 */
@Data
@Builder
public class CsrResponseDto {

    private String serialNumber;
    private String certificatePem;

    /**
     * Prisutan SAMO kod autogenerate opcije.
     * Ključ se vraća jednom i nije sačuvan na serveru.
     */
    private String privateKeyPem;

    private String message;
}