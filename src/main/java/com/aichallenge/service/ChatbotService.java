package com.aichallenge.service;

import com.aichallenge.dto.ChatbotRequestDto;
import com.aichallenge.dto.ChatbotResponseDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ChatbotService {

    private final RestTemplate restTemplate;

    @Value("${openai.model}")
    private String defaultModel;

    @Value("${openai.api-uri}")
    private String apiUri;

    public ChatbotService(@Qualifier("openaiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String ask(String prompt) {
        // DTO를 사용해 요청 생성
        ChatbotRequestDto body = new ChatbotRequestDto(defaultModel, prompt);
        body.setTemperature(0.7);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ChatbotRequestDto> entity = new HttpEntity<>(body, headers);
        ResponseEntity<ChatbotResponseDto> res =
                restTemplate.postForEntity(apiUri, entity, ChatbotResponseDto.class);

        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            return "[에러] OpenAI 호출 실패: " + res.getStatusCode();
        }
        ChatbotResponseDto dto = res.getBody();
        if (dto.getChoices() == null || dto.getChoices().isEmpty()
                || dto.getChoices().get(0).getMessage() == null) {
            return "[에러] 응답 파싱 실패";
        }
        return dto.getChoices().get(0).getMessage().getContent();
    }
}
