package com.vetclinic.profile.security;

import com.vetclinic.profile.security.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
                        // Danh sách bác sĩ công khai — trang đặt lịch cần hiển thị được kể cả khách
                        // chưa đăng nhập. Đây là endpoint /profile/doctors riêng (số nhiều), KHÔNG phải
                        // /profile/doctor/me — "me" luôn cần xác thực vì phải biết "me" là ai.
                        .requestMatchers(HttpMethod.GET, "/profile/doctors").permitAll()
                        // Staff/Admin tra cứu 1 khách hàng cụ thể — path "by-id" tách biệt hẳn khỏi
                        // "me" nên không lo bị rule /profile/customer/** phía dưới nuốt mất.
                        .requestMatchers(HttpMethod.GET, "/profile/customer/by-id/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/profile/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/profile/doctor/**").hasRole("DOCTOR")
                        // Danh sách nhân viên chỉ Admin xem được — /profile/staff/** cũng khớp path
                        // /profile/staff (không có /me) nên rule cụ thể này phải đứng trước.
                        .requestMatchers(HttpMethod.GET, "/profile/staff").hasRole("ADMIN")
                        .requestMatchers("/profile/staff/**").hasAnyRole("STAFF", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
