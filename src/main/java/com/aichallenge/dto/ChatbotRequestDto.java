package com.aichallenge.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ChatbotRequestDto {
    private String model;
    private List<Message> messages;
    private double temperature;

    public ChatbotRequestDto(String model,String prompt) {
        this.model = model;
        this.temperature = 0.7;
        this.messages = new ArrayList<Message>();
        this.messages.add(new Message("user", prompt));
    }
}
