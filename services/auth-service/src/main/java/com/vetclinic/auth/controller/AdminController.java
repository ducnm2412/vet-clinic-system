package com.vetclinic.auth.controller;

import com.vetclinic.auth.dto.CreateStaffAccountRequest;
import com.vetclinic.auth.dto.MessageResponse;
import com.vetclinic.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createStaffAccount(@Valid @RequestBody CreateStaffAccountRequest request) {
        return authService.createStaffAccount(request);
    }
}
