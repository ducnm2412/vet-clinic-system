package com.vetclinic.order.security;

import com.vetclinic.order.security.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()

                        // CN-36: khu xử lý đơn của nhân viên. Phải khai TRƯỚC /orders/**,
                        // nếu không rule CUSTOMER bên dưới sẽ nuốt mất.
                        .requestMatchers("/orders/manage/**").hasAnyRole("STAFF", "ADMIN")

                        // CN-32, CN-33, CN-35: giỏ hàng và đơn của chính khách.
                        .requestMatchers("/cart/**").hasRole("CUSTOMER")
                        .requestMatchers("/orders/**").hasAnyRole("CUSTOMER", "STAFF", "ADMIN")

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
