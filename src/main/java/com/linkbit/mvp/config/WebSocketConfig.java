package com.linkbit.mvp.config;

import com.linkbit.mvp.service.JwtService;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final org.springframework.context.ApplicationContext applicationContext;

    @Value("${linkbit.cors.allowed-origins}")
    private String allowedOrigins;

    private com.linkbit.mvp.repository.LoanRepository getLoanRepository() {
        return applicationContext.getBean(com.linkbit.mvp.repository.LoanRepository.class);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        registry.addEndpoint("/ws", "/ws/loans").setAllowedOrigins(origins.toArray(String[]::new)).withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authorizationHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                        String token = authorizationHeader.substring(7);
                        try {
                            String username = jwtService.extractUsername(token);
                            if (username != null) {
                                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                if (jwtService.isTokenValid(token, userDetails)) {
                                    accessor.setUser(new UsernamePasswordAuthenticationToken(
                                            userDetails, null, userDetails.getAuthorities()));
                                }
                            }
                        } catch (JwtException | IllegalArgumentException ex) {
                            log.debug("Rejected websocket connection with invalid JWT");
                        }
                    }
                } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/topic/loans/")) {
                        String loanIdStr = destination.substring("/topic/loans/".length());
                        try {
                            java.util.UUID loanId = java.util.UUID.fromString(loanIdStr);
                            java.security.Principal principal = accessor.getUser();
                            if (principal == null || !getLoanRepository().isParticipant(loanId, principal.getName())) {
                                log.warn("Unauthorized subscription attempt to {} by {}", destination, 
                                        principal != null ? principal.getName() : "anonymous");
                                return null; // Reject the message
                            }
                        } catch (IllegalArgumentException e) {
                            // Not a UUID suffix, allow through (could be a different sub-topic)
                        }
                    }
                }
                return message;
            }
        });
    }
}
