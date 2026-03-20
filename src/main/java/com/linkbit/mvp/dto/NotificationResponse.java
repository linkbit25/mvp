package com.linkbit.mvp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private UUID loanId;
    private String title;
    private String message;
    private boolean read;
    private LocalDateTime createdAt;
    private long unreadCount;
}
