package com.aichallenge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotMessage {
    private Long id;
    private Long user_id;
    private String context;
    private int isFromChat;
    private LocalDateTime created_at;

}
