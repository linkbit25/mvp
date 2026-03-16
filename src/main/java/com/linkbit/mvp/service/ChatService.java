package com.linkbit.mvp.service;

import com.linkbit.mvp.domain.Loan;
import com.linkbit.mvp.domain.LoanStatus;
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
        Loan loan = getLoan(loanId);
        ensureChatIsOpen(loan);

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!loan.getLender().getId().equals(senderId) && !loan.getBorrower().getId().equals(senderId)) {
            throw new RuntimeException("User is not a participant in this loan");
        }

        publish(messageRepository.save(NegotiationMessage.builder()
                .loan(loan)
                .sender(sender)
                .messageText(messageText)
                .isSystemMessage(false)
                .build()));
    }

    @Transactional
    public void sendSystemMessage(UUID loanId, String messageText) {
        Loan loan = getLoan(loanId);
        publish(messageRepository.save(NegotiationMessage.builder()
                .loan(loan)
                .messageText(messageText)
                .isSystemMessage(true)
                .build()));
    }

    private Loan getLoan(UUID loanId) {
        return loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    private void ensureChatIsOpen(Loan loan) {
        if (loan.getStatus() != LoanStatus.NEGOTIATING) {
            throw new RuntimeException("Negotiation chat is closed for this loan");
        }
    }

    private void publish(NegotiationMessage savedMessage) {
        messagingTemplate.convertAndSend("/topic/loans/" + savedMessage.getLoan().getId(),
                ChatMessage.builder()
                        .loanId(savedMessage.getLoan().getId())
                        .senderId(savedMessage.getSender() != null ? savedMessage.getSender().getId() : null)
                        .messageText(savedMessage.getMessageText())
                        .timestamp(savedMessage.getSentAt())
                        .build());
    }
}
