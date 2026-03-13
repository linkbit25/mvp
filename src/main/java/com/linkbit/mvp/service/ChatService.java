package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.NegotiationMessage;
import com.linkbit.mvp.domain.User;
import com.linkbit.mvp.dto.ChatMessage;
import com.linkbit.mvp.repository.LoanRepository;
import com.linkbit.mvp.repository.NegotiationMessageRepository;
import com.linkbit.mvp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final NegotiationMessageRepository messageRepository;
    private final LoanRepository loanRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void sendMessage(UUID loanId, UUID senderId, String messageText) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!loan.getLender().getId().equals(senderId) && !loan.getBorrower().getId().equals(senderId)) {
            throw new RuntimeException("User is not a participant in this loan");
        }

        NegotiationMessage message = NegotiationMessage.builder()
                .loan(loan)
                .sender(sender)
                .messageText(messageText)
                .isSystemMessage(false)
                .build();

        NegotiationMessage savedMessage = messageRepository.save(message);

        ChatMessage chatMessage = ChatMessage.builder()
                .loanId(loanId)
                .senderId(senderId)
                .messageText(messageText)
                .timestamp(savedMessage.getSentAt())
                .build();

        messagingTemplate.convertAndSend("/topic/loans/" + loanId, chatMessage);
    }

    @Transactional
    public void sendSystemMessage(UUID loanId, String messageText) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        // System message has no sender, or system user?
        // User table constraints sender_id NOT NULL.
        // I might need a system user or make sender_id nullable.
        // For MVP, I'll use lender as sender for system messages or create a dummy system user?
        // Or better, make sender_id nullable in NegotiationMessage entity.
        // But the schema says sender_id UUID NOT NULL.
        // I'll skip system messages persistence for now if sender is required, or use one of the participants.
        // Wait, "System actions: freeze chat...". This implies system event.
        // I'll use lender ID for system messages triggered by lender actions.
        // Or better, just broadcast system message without saving to DB if sender is problematic.
        // But requirements say "history is immutable", "stored for dispute".
        // I'll assume system messages are just notifications.
        
        // Actually, let's persist system messages using the lender or borrower who triggered the action.
        // I'll pass userId who triggered action.
    }
}
