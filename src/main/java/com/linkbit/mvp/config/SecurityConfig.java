package com.linkbit.mvp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/auth/register")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/auth/login")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/auth/password/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/v3/api-docs/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/payments/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/admin/payments/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/loans/*/escrow/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/loans/*/deposit")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/admin/collateral/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/admin/loans/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/btc/price")).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
