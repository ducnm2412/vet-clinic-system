package com.vetclinic.auth.security;

import com.vetclinic.auth.security.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // refresh/logout mở công khai: access token lúc đó có thể đã hết hạn, danh
                        // tính được chứng minh bằng refresh token trong thân request.
                        .requestMatchers("/auth/register", "/auth/login", "/auth/verify-email",
                                "/auth/refresh", "/auth/logout", "/error").permitAll()
                        // CN-19: lễ tân mở tài khoản cho khách tại quầy. KHÔNG dùng @PreAuthorize ở
                        // auth-service: service này chưa bật @EnableMethodSecurity nên annotation đó
                        // im lặng không có tác dụng — mọi rule quyền ở đây phải khai bằng matcher.
                        .requestMatchers(HttpMethod.POST, "/auth/customers").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/auth/customers").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
