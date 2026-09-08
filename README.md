# PKI-Manager

PKI-Manager is a web-based Public Key Infrastructure management system for creating, issuing, managing, downloading, and revoking digital certificates.

The application provides a centralized platform for managing certificate authorities, users, organizations, certificate signing requests, certificate lifecycles, and cryptographic keys through a secure web interface.

## Overview

The system is built around an X.509 certificate hierarchy consisting of:

- Root certificates
- Intermediate certificate authority certificates
- End-entity certificates issued to users and organizations

Users can register, activate their accounts, generate or upload Certificate Signing Requests, request certificates, view issued certificates, and download certificates in several standard formats.

Administrators and Certificate Authority users can issue certificates, manage organizations, inspect certificate data, revoke certificates, and access Certificate Revocation Lists.

## Main Features

### User Management

- User registration with email-based account activation
- Secure password storage using BCrypt
- Password reset through email verification
- Password change functionality
- User profile management
- Role-based access control
- Separate permissions for regular users, CA users, and administrators

### Authentication and Authorization

- Stateless authentication using JSON Web Tokens
- Access and refresh token support
- JWT authentication filter for protected API requests
- Role-based authorization using Spring Security
- Optional TOTP-based two-factor authentication
- Secure account activation and password recovery challenges
- HTTPS support for encrypted client-server communication

### Certificate Management

The application supports the complete certificate lifecycle:

- Root certificate creation
- Intermediate CA certificate issuance
- End-entity certificate issuance
- Certificate inspection and metadata display
- Certificate ownership and organization association
- Certificate status tracking
- Certificate revocation
- Retrieval of revoked certificates
- Organization-specific certificate views
- Certificate issuer selection
- Certificate downloads in multiple formats

Supported certificate formats include:

- PEM
- CER
- PKCS#12 (`.p12`)
- Java KeyStore (`.jks`)

Supported certificate types are `ROOT`, `INTERMEDIATE`, and `END_ENTITY`. Certificate statuses include `ACTIVE` and `REVOKED`.

### Certificate Signing Requests

Users can obtain certificates through two CSR workflows:

1. The application can generate a key pair and CSR automatically.
2. Users can generate their own key pair and upload an externally created CSR.

The CSR workflow allows users to select an available certificate authority and submit the required certificate subject information.

### Certificate Revocation

Authorized users can revoke issued certificates by providing a revocation reason. The system updates the certificate status, stores the revocation reason and time, and makes revoked certificates available to administrators.

The application also generates Certificate Revocation Lists through a dedicated CRL endpoint using the standard `application/pkix-crl` media type.

### Key Protection

Private keys and other sensitive PKI data are protected using AES-256-GCM encryption. The backend includes a bootstrap key, database-stored master keys, master key rotation, re-encryption during rotation, and secure random password and key generation.

Administrators can check the active master key status and initiate master key rotation.

## User Roles

### Regular User

Regular users can register and activate an account, log in, manage their profile, change their password, generate or upload a CSR, request certificates, view their own certificates, and download certificate files.

### Certificate Authority User

CA users can view certificates issued by their organization, view available certificate issuers, issue certificates, manage certificate issuance for their organization, revoke certificates when authorized, and download organization certificates.

### Administrator

Administrators can create CA users, manage organizations, issue certificates, view all certificates, inspect individual certificates, view revoked certificates, access revocation information, and rotate or inspect the PKI master key.

## Application Architecture

The repository is divided into two main applications.

### Backend

The backend is a Spring Boot REST API responsible for authentication, authorization, user and organization management, certificate issuance, CSR processing, revocation, CRL generation, key encryption and rotation, database persistence, email notifications, and two-factor authentication.

It follows a layered architecture containing REST controllers, services, JPA repositories, domain models, data transfer objects, security components, and certificate-generation utilities.

### Frontend

The frontend is a single-page Angular application containing login, registration, activation, password recovery, dashboard, certificate generation, CSR upload, certificate issuance, certificate listing, certificate downloads, profile management, administrator, CA, and revoked-certificate views.

Angular route guards prevent unauthorized users from accessing protected pages, while an HTTP interceptor attaches authentication tokens to API requests.

## Technology Stack

### Backend

- Java 17
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- Spring Validation
- Spring Mail
- PostgreSQL
- JSON Web Tokens
- Bouncy Castle
- Lombok
- Gradle

### Frontend

- Angular
- TypeScript
- RxJS
- Angular Router
- Angular Forms
- Tailwind CSS
- zxcvbn password-strength estimation

### Security and Cryptography

- X.509 certificates
- Public-key cryptography
- Bouncy Castle cryptographic providers
- JWT access and refresh tokens
- BCrypt password hashing
- AES-GCM encryption
- TOTP-based two-factor authentication
- TLS 1.2 and TLS 1.3

## Repository Structure

```text
.
├── backend/
│   ├── src/main/java/com/tim12/pk_infrastructure/
│   │   ├── certificates/       # Certificate generation utilities
│   │   ├── controller/         # REST API controllers
│   │   ├── model/              # Entities, DTOs, and enums
│   │   ├── repository/         # Database repositories
│   │   ├── security/           # JWT and Spring Security configuration
│   │   └── service/            # Application and business logic
│   ├── src/main/resources/     # Application configuration
│   ├── keystores/              # PKI keystore directory
│   ├── secrets/                # Local secret files
│   ├── build.gradle
│   └── docker-compose.yml
├── frontend/
│   ├── src/app/
│   │   ├── components/         # Reusable UI components
│   │   ├── pages/              # Main application pages
│   │   ├── services/           # API and cryptography services
│   │   ├── guards/             # Route authorization guards
│   │   └── interceptors/       # HTTP authentication interceptor
│   ├── public/
│   ├── angular.json
│   └── package.json
└── README.md
```

## REST API Areas

- `/api/auth` - registration, login, activation, token refresh, and password recovery
- `/api/auth/2fa` - two-factor authentication setup and verification
- `/api/certificates` - certificate issuance, retrieval, revocation, and downloads
- `/api/certificates/csr` - CSR generation and upload
- `/api/crl` - Certificate Revocation List retrieval
- `/api/admin` - administrator operations and CA user management
- `/api/admin/master-key` - master key status and rotation

## Database

The application uses PostgreSQL for persistent storage. The database stores users, organizations, certificates, certificate subjects, certificate issuers, activation tokens, master keys, certificate statuses, and revocation information.

A PostgreSQL 15 container is provided through Docker Compose for local development.

## Security Model

Security is a central part of the application design. The system uses HTTPS, stateless JWT authentication, access and refresh tokens, BCrypt password hashing, role-based method authorization, protected certificate and key endpoints, AES-GCM encryption for sensitive key material, secure email-based account activation, TOTP two-factor authentication, organization-based certificate access, and certificate revocation with CRL generation.

Sensitive values such as database credentials, JWT secrets, TLS keystore credentials, mail credentials, and the PKI master key are loaded through environment variables rather than hard-coded in the application.

## Intended Use

TrustForge PKI Infrastructure is intended for organizations that need to establish a private certificate hierarchy, issue certificates to users and services, manage certificate authorities, control certificate access by role and organization, export certificates for external systems, revoke compromised or invalid certificates, protect cryptographic key material, and provide a centralized certificate management interface.

The project can be used as an educational PKI implementation, an internal certificate management platform, or a foundation for a larger enterprise certificate authority system.

## Project Information

This project was developed for the SIIT Information Security course.

### Team 12

1. Boško Vasilić - SV48/2023
2. Sara Stojkov - SV38/2023
3. Marko Milutin - SV40/2023
