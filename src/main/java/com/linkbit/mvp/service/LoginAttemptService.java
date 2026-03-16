package com.linkbit.mvp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class LoginAttemptService {

    private final int maxAttempts;
    private final Duration window;
    private final ConcurrentMap<String, Deque<Instant>> attemptsByEmail = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${linkbit.security.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${linkbit.security.login-rate-limit.window-minutes:15}") long windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.window = Duration.ofMinutes(windowMinutes);
    }

    public boolean isBlocked(String email) {
        Deque<Instant> attempts = attemptsByEmail.get(normalize(email));
        if (attempts == null) {
            return false;
        }
        synchronized (attempts) {
            purgeExpired(attempts);
            return attempts.size() >= maxAttempts;
        }
    }

    public void recordFailure(String email) {
        Deque<Instant> attempts = attemptsByEmail.computeIfAbsent(normalize(email), key -> new ArrayDeque<>());
        synchronized (attempts) {
            purgeExpired(attempts);
            attempts.addLast(Instant.now());
        }
    }

    public void reset(String email) {
        attemptsByEmail.remove(normalize(email));
    }

    private void purgeExpired(Deque<Instant> attempts) {
        Instant cutoff = Instant.now().minus(window);
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
            attempts.removeFirst();
        }
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
