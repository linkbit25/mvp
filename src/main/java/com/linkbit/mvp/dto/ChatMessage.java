package com.linkbit.mvp.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class ChatMessage {

    @JsonProperty("loan_id")
    private UUID loanId;

    @JsonProperty("sender_id")
    private UUID senderId;

    @JsonProperty("message_text")
    private String messageText;

    @JsonProperty("timestamp")
    private LocalDateTime timestamp;
}
