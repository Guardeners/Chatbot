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

    @PostMapping("/chat")
    public Map<String, String> chat(@RequestBody Map<String, Object> req) {
        try {
            System.out.println("POST /api/chat raw req: " + req); // ★ 들어온 바디 확인

            if (req.containsKey("question")) {
                Object question = req.get("question");
                Object context  = req.get("context"); // Map 또는 null

                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                String promptJson = om.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(Map.of("question", question, "context", context));

                System.out.println("PROMPT_JSON going to ask():\n" + promptJson); // ★ 넘기는 JSON 확인

                String answer = chatbotService.ask(promptJson);
                return Map.of("answer", answer);
            }

            String prompt = String.valueOf(req.getOrDefault("prompt", ""));
            String answer = chatbotService.ask(prompt);
            return Map.of("answer", answer);

        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("answer", "[에러] 요청 직렬화 실패: " + e.getMessage());
        }
    }




    // 브라우저에서 빠르게 테스트하려면 GET도 하나 열어두면 편함
    @GetMapping("/chat")
    public Map<String, String> chatGet(@RequestParam String prompt) {
        String answer = chatbotService.ask(prompt);
        return Map.of("answer", answer);
    }
}