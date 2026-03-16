package com.linkbit.mvp.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class AdminAccessManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final Set<String> adminEmails;

    public AdminAccessManager(@Value("${linkbit.admin.emails}") String configuredAdminEmails) {
        this.adminEmails = Arrays.stream(configuredAdminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext object) {
        return new AuthorizationDecision(isAdmin(authentication.get()));
    }

    public AuthorizationDecision allowPublicWhenEnabled(Supplier<Authentication> authentication, boolean enabled) {
        return new AuthorizationDecision(enabled || isAdmin(authentication.get()));
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && adminEmails.contains(authentication.getName().toLowerCase());
    }
}
