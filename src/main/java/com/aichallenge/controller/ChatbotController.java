package com.aichallenge.controller;

import com.aichallenge.service.ChatbotService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    // POST: { "prompt": "질문" }
    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, String> req) {
        String prompt = req.getOrDefault("prompt", "");
        String answer = chatbotService.ask(prompt);
        return Map.of("answer", answer);
    }

    // 브라우저에서 빠르게 테스트하려면 GET도 하나 열어두면 편함
    @GetMapping("/chat")
    public Map<String, String> chatGet(@RequestParam String prompt) {
        String answer = chatbotService.ask(prompt);
        return Map.of("answer", answer);
    }
}
