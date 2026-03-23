package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.Notification;
import com.linkbit.mvp.dto.NotificationResponse;
import com.linkbit.mvp.repository.NotificationRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * Emits a notification to both the borrower and lender of a loan.
     * Called after every state machine transition.
     */
    @Transactional
    public void createForBothParties(Loan loan, String title, String message) {
        try {
            notificationRepository.save(Notification.builder()
                    .userId(loan.getBorrower().getId())
                    .loanId(loan.getId())
                    .title(title)
                    .message(message)
                    .build());

            notificationRepository.save(Notification.builder()
                    .userId(loan.getLender().getId())
                    .loanId(loan.getId())
                    .title(title)
                    .message(message)
                    .build());
        } catch (Exception e) {
            // Notification failures must never crash the primary transaction
            log.error("Failed to create notification for loan {}: {}", loan.getId(), e.getMessage());
        }
    }

    /**
     * Emits a notification directly to a specific user.
     */
    @Transactional
    public void createForUser(UUID userId, String title, String message) {
        try {
            notificationRepository.save(Notification.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .build());
        } catch (Exception e) {
            log.error("Failed to create notification for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(String email) {
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"))
                .getId();
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .loanId(n.getLoanId())
                        .title(n.getTitle())
                        .message(n.getMessage())
                        .read(n.isRead())
                        .createdAt(n.getCreatedAt())
                        .unreadCount(unreadCount)
                        .build())
                .toList();
    }

    @Transactional
    public void markAllRead(String email) {
        UUID userId = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"))
                .getId();
        notificationRepository.markAllReadByUserId(userId);
    }
}
