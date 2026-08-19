package com.vetclinic.auth.service;

import com.vetclinic.auth.domain.Role;
import com.vetclinic.auth.domain.RoleName;
import com.vetclinic.auth.domain.User;
import com.vetclinic.auth.domain.UserStatus;
import com.vetclinic.auth.dto.AuthResponse;
import com.vetclinic.auth.dto.LoginRequest;
import com.vetclinic.auth.dto.RegisterRequest;
import com.vetclinic.auth.exception.EmailAlreadyExistsException;
import com.vetclinic.auth.exception.InvalidCredentialsException;
import com.vetclinic.auth.repository.RoleRepository;
import com.vetclinic.auth.repository.UserRepository;
import com.vetclinic.auth.security.jwt.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Role defaultRole = roleRepository.findByName(RoleName.CUSTOMER)
                .orElseThrow(() -> new IllegalStateException("Default role CUSTOMER not seeded"));

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .status(UserStatus.ACTIVE)
                .roles(Set.of(defaultRole))
                .build();

        userRepository.save(user);

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .toList();

        String accessToken = jwtUtil.generateAccessToken(user.getEmail(), roleNames);
        String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

        return new AuthResponse(accessToken, refreshToken);
    }
}
