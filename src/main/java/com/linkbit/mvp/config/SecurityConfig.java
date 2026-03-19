package com.linkbit.mvp.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final AdminAccessManager adminAccessManager;

    @Value("${springdoc.api-docs.enabled:false}")
    private boolean apiDocsEnabled;

    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerUiEnabled;

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Value("${linkbit.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(new AntPathRequestMatcher("/auth/register")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/auth/login")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/auth/password/**")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/**")).access((authentication, context) -> adminAccessManager.allowPublicWhenEnabled(authentication, swaggerUiEnabled))
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui.html")).access((authentication, context) -> adminAccessManager.allowPublicWhenEnabled(authentication, swaggerUiEnabled))
                .requestMatchers(new AntPathRequestMatcher("/swagger-ui/index.html")).access((authentication, context) -> adminAccessManager.allowPublicWhenEnabled(authentication, swaggerUiEnabled))
                .requestMatchers(new AntPathRequestMatcher("/api-docs/**")).access((authentication, context) -> adminAccessManager.allowPublicWhenEnabled(authentication, apiDocsEnabled))
                .requestMatchers(new AntPathRequestMatcher("/h2-console/**")).access((authentication, context) -> adminAccessManager.allowPublicWhenEnabled(authentication, h2ConsoleEnabled))
                .requestMatchers(new AntPathRequestMatcher("/admin/**")).hasRole("ADMIN")
                .requestMatchers(new AntPathRequestMatcher("/payments/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/loans/*/escrow/**")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/loans/*/deposit")).authenticated()
                .requestMatchers(new AntPathRequestMatcher("/offers", "GET")).permitAll()
                .requestMatchers(new AntPathRequestMatcher("/btc/price")).permitAll()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authenticationProvider(authenticationProvider)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
