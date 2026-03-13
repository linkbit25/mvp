package com.linkbit.mvp.controller;

import com.linkbit.mvp.dto.ChatMessage;
import com.linkbit.mvp.repository.UserRepository;
import com.linkbit.mvp.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final UserRepository userRepository;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("Unauthorized");
        }
        
        var user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        chatService.sendMessage(chatMessage.getLoanId(), user.getId(), chatMessage.getMessageText());
    }
}
