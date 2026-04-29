package com.tim12.pk_infrastructure.service;

import com.tim12.pk_infrastructure.model.ActivationToken;
import com.tim12.pk_infrastructure.model.Organization;
import com.tim12.pk_infrastructure.model.User;
import com.tim12.pk_infrastructure.model.dtos.CreateCaUserDTO;
import com.tim12.pk_infrastructure.model.dtos.CreatedCaUserDTO;
import com.tim12.pk_infrastructure.model.enums.Role;
import com.tim12.pk_infrastructure.repository.ActivationTokenRepository;
import com.tim12.pk_infrastructure.repository.OrganizatioRepository;
import com.tim12.pk_infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final ActivationTokenRepository tokenRepo;
    private final OrganizatioRepository orgRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    public CreatedCaUserDTO createCaUser(CreateCaUserDTO request) {
        Organization org = orgRepo.findByName(request.getOrganizationName())
                .orElseGet(() -> orgRepo.save(new Organization(request.getOrganizationName())));

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPassword(UUID.randomUUID().toString());
        user.setRole(Role.CA_USER);
        user.setOrganization(org);
        user.setEnabled(false);
        userRepo.save(user);

        String token = UUID.randomUUID().toString();
        ActivationToken at = ActivationToken.builder()
                .token(token)
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .used(false)
                .build();

        tokenRepo.save(at);

        emailService.sendActivationEmail(user.getEmail(), "http://localhost:4200/activate?token=" + token);

        return userRepo.findByEmail(request.getEmail())
                .map(u -> new CreatedCaUserDTO(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                        u.getOrganization().getId(), u.getOrganization().getName()))
                .orElseThrow(() -> new RuntimeException("User creation failed"));
    }

    public List<Organization> getAllOrganizations() {
        return orgRepo.findAll();
    }
}
