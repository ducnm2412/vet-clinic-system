package com.vetclinic.product.security;

import com.vetclinic.product.security.jwt.JwtAuthFilter;
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

                        // --- Tồn kho (CN-30): STAFF/ADMIN. Phải khai TRƯỚC các rule GET công khai
                        // bên dưới, nếu không "/products/low-stock" sẽ bị permitAll nuốt mất.
                        .requestMatchers(HttpMethod.GET, "/products/low-stock").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/products/*/stock-movements").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/products/*/stock").hasAnyRole("STAFF", "ADMIN")

                        // --- Tra cứu (CN-29): công khai, trang bán hàng phải xem được khi chưa đăng nhập.
                        .requestMatchers(HttpMethod.GET, "/products", "/products/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/categories", "/categories/*").permitAll()

                        // --- Quản trị danh mục & sản phẩm (CN-27, CN-28): chỉ ADMIN.
                        // Gateway đã route /admin/** sang auth-service nên KHÔNG dùng prefix đó ở đây;
                        // phân quyền bằng HTTP method trên cùng một prefix tài nguyên.
                        .requestMatchers("/categories/**").hasRole("ADMIN")
                        .requestMatchers("/products/**").hasRole("ADMIN")

                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
