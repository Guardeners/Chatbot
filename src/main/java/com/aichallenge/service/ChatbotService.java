package com.aichallenge.service;

import com.aichallenge.dto.ChatbotRequestDto;
import com.aichallenge.dto.ChatbotResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Service
public class ChatbotService {

    private final RestTemplate restTemplate;
    private final ObjectMapper om = new ObjectMapper(); // JSON 파싱

    @Value("${openai.model}")
    private String defaultModel;

    @Value("${openai.api-uri}")
    private String apiUri;

    // ⚠ 시스템 프롬프트 파일명 (요청하신 철자 유지)
    @Value("classpath:prompt/system_promt.txt")
    private Resource systemPromptFile;

    // 1차 분류 전용 시스템 프롬프트
    @Value("classpath:prompt/classifier_system.txt")
    private Resource classifierPromptFile;

    // few-shot 데이터(JSONL): role:"context" 라인에서 CONTEXT_JSON을 읽음
    @Value("classpath:prompt/fewshot.jsonl")
    private Resource fewshotJsonlFile;

    // 캐시
    private String cachedSystemPrompt;
    private String cachedClassifierPrompt;
    private String cachedContextFromFewshot; // pretty JSON

    public ChatbotService(@Qualifier("openaiRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 프런트는 문장만 보낸다고 가정.
     * 1) 분류 프롬프트로 "지출_분석" | "일반" 라벨만 받는다.
     * 2) "지출_분석"일 때만 CONTEXT_JSON(= fewshot.jsonl) 붙여서 본응답 생성.
     */
    public String ask(String prompt) {
        final String question = (prompt == null ? "" : prompt.trim());

        // 1) 분류
        String label = classifyIntent(question);
        System.out.println("[INTENT_LABEL] " + label);

        // 2) 본응답용 프롬프트 조립
        String systemPrompt = loadSystemPrompt();
        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n");
        sb.append("[QUESTION]\n").append(question).append("\n\n");

        if ("지출_분석".equals(label)) {
            String contextPretty = loadContextFromFewshot();
            if (contextPretty == null) {
                return "[에러] 컨텍스트 로딩 실패: src/main/resources/prompt/fewshot.jsonl의 role:\"context\" 라인을 확인하세요.";
            }
            sb.append("[CONTEXT_JSON]\n").append(contextPretty).append("\n");
        }

        String finalPrompt = sb.toString();
        System.out.println("FINAL_PROMPT ===\n" + finalPrompt);

        // 3) 본응답 호출 (원래 DTO 흐름 유지)
        ChatbotRequestDto body = new ChatbotRequestDto(defaultModel, finalPrompt);
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

    /* ===================== 1차 분류 ===================== */

    private String classifyIntent(String question) {
        // ★ 선 키워드 게이트
        String q = question == null ? "" : question;
        String[] kws = {"카페","커피","지출","소비","평균","비교","상위","횟수","절약","줄이","얼마","p75"};
        for (String k : kws) {
            if (q.contains(k)) return "지출_분석";
        }

        String classifierSystem = loadClassifierPrompt();
        String classifyPrompt = classifierSystem + "\n\n"
                + "[USER_UTTERANCE]\n" + q + "\n";

        ChatbotRequestDto body = new ChatbotRequestDto(defaultModel, classifyPrompt);
        body.setTemperature(0.0);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ChatbotResponseDto res = restTemplate.postForObject(apiUri, new HttpEntity<>(body, headers), ChatbotResponseDto.class);
            if (res != null && res.getChoices() != null && !res.getChoices().isEmpty()
                    && res.getChoices().get(0).getMessage() != null) {
                String content = res.getChoices().get(0).getMessage().getContent();
                try {
                    JsonNode node = om.readTree(content);
                    String label = node.has("label") ? node.get("label").asText() : null;
                    if (label != null && !label.isBlank()) return label;
                } catch (Exception ignore) {
                    if (content != null && content.contains("지출_분석")) return "지출_분석";
                }
            }
        } catch (Exception e) {
            // 네트워크/401 등 실패시도 키워드로 fallback (이미 위에서 처리됨)
        }
        return "일반";
    }


    /* ===================== 로더들 ===================== */

    private String loadSystemPrompt() {
        if (cachedSystemPrompt != null) return cachedSystemPrompt;
        try {
            cachedSystemPrompt = FileCopyUtils.copyToString(
                    new InputStreamReader(systemPromptFile.getInputStream(), StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            cachedSystemPrompt =
                    "당신은 은행 앱의 재무 코치 챗봇입니다.\n" +
                    "- CONTEXT_JSON이 제공되었을 때만 그것을 근거로 분석합니다.\n" +
                    "- 한국어 존댓말로 2~5문장, 간결하게 답합니다.\n";
        }
        return cachedSystemPrompt;
    }

    private String loadClassifierPrompt() {
        if (cachedClassifierPrompt != null) return cachedClassifierPrompt;
        try {
            cachedClassifierPrompt = FileCopyUtils.copyToString(
                    new InputStreamReader(classifierPromptFile.getInputStream(), StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            // 기본 분류 규칙 (안전망)
            cachedClassifierPrompt =
                    """
                    당신의 임무는 사용자의 한 문장을 '지출_분석' 또는 '일반' 중 하나로 분류해 JSON으로만 출력하는 것입니다.
                    규칙:
                    - 카페/커피/지출/소비/평균/비교/상위/횟수/절약/줄이 등 지출 비교·절약 의도가 있으면 {"label":"지출_분석"}만 출력.
                    - 그 외에는 {"label":"일반"}만 출력.
                    - 설명/추가 텍스트 금지. JSON 한 줄만.
                    """;
        }
        return cachedClassifierPrompt;
    }

    /**
     * fewshot.jsonl에서 role:"context" 라인의 JSON을 찾아 pretty JSON으로 반환.
     * 여러 개가 있으면 첫 번째만 사용.
     * - content 가 문자열 JSON이든, 객체 JSON이든 모두 지원.
     */
    // ChatbotService.java 안의 loadContextFromFewshot() 전체를 이걸로 교체
    private String loadContextFromFewshot() {
        if (cachedContextFromFewshot != null) return cachedContextFromFewshot;

        try {
            System.out.println("fewshot exists? " + fewshotJsonlFile.exists());
            System.out.println("fewshot name: " + fewshotJsonlFile.getFilename());

            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(fewshotJsonlFile.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int ln = 0;
                while ((line = br.readLine()) != null) {
                    ln++;
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) continue;

                    // ↓ 어떤 줄을 읽었는지 그대로 보여줌 (문제 원인 즉시 확인 가능)
                    System.out.println("JSONL[" + ln + "]: " + trimmed);

                    // 라인 JSON 파싱 (문자열/객체 모두 허용)
                    JsonNode node = om.readTree(trimmed);
                    String role = node.path("role").asText("");
                    if (!"context".equalsIgnoreCase(role)) continue;

                    JsonNode contentNode = node.get("content");
                    if (contentNode == null || contentNode.isNull()) continue;

                    Object parsed = contentNode.isTextual()
                            ? om.readValue(contentNode.asText(), Object.class)   // "..." 문자열 JSON
                            : om.treeToValue(contentNode, Object.class);         // {...} 객체 JSON

                    cachedContextFromFewshot = om.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(parsed);

                    System.out.println("LOADED CONTEXT FROM JSONL ===\n" + cachedContextFromFewshot);
                    return cachedContextFromFewshot; // 첫 매칭 반환
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // 못 찾으면 null (ask()에서 에러 메시지 리턴)
    }



}
