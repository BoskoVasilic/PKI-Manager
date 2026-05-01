package com.tim12.pk_infrastructure.controller;

import com.tim12.pk_infrastructure.model.Organization;
import com.tim12.pk_infrastructure.model.dtos.CreateCaUserDTO;
import com.tim12.pk_infrastructure.model.dtos.CreatedCaUserDTO;
import com.tim12.pk_infrastructure.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping(value = "ca-users", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createCaUser(@RequestBody CreateCaUserDTO request) {
            CreatedCaUserDTO caUser = adminService.createCaUser(request);
            return new ResponseEntity<>(caUser, HttpStatus.CREATED);
    }

    @GetMapping("/organizations")
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        List<Organization> organizations = adminService.getAllOrganizations();
        return new ResponseEntity<>(organizations, HttpStatus.OK);
    }
}
