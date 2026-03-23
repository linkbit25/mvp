package com.linkbit.mvp.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global exception handler that maps service-layer exceptions to proper HTTP responses.
 * Prevents raw exception messages from leaking in 500 responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(Exception ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleAuthExceptions(Exception ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleConflict(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleForbidden(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ProblemDetail handleNotFound(UsernameNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "User not found");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return problem(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleRuntime(RuntimeException ex) {
        String msg = ex.getMessage();
        // Map well-known messages to appropriate HTTP codes
        if (msg != null) {
            if (msg.startsWith("Unauthorized")) return problem(HttpStatus.FORBIDDEN, msg);
            if (msg.contains("not found") || msg.contains("not Found"))
                return problem(HttpStatus.NOT_FOUND, msg);
            if (msg.contains("KYC") || msg.contains("not verified"))
                return problem(HttpStatus.FORBIDDEN, msg);
        }
        // Generic server errors — do NOT expose internal detail
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}
