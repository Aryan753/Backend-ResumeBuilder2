package com.resumebuilder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumebuilder.dto.AtsScoreDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeminiAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String apiUrl;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30)).build();

    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.startsWith("${")) {
            log.error("GROQ_API_KEY environment variable is NOT set! AI features will not work.");
        } else {
            log.info("Groq AI Service initialized. Model: {}", model);
        }
    }

    public String optimizeResumeForAts(String resumeData, String jobDescription, boolean isFresher) {
        String prompt = buildOptimizationPrompt(resumeData, jobDescription, isFresher);
        return extractJsonFromResponse(callGroqApi(prompt), resumeData);
    }

    public AtsScoreDto calculateAtsScore(String resumeText, String jobDescription) {
        return parseAtsScoreResponse(callGroqApi(buildAtsScorePrompt(resumeText, jobDescription)));
    }

    public String extractResumeDataFromText(String rawText) {
        return extractJsonFromResponse(callGroqApi(buildExtractionPrompt(rawText)), "{}");
    }

    public String buildAtsResume(String userInputJson, String jobDescription, boolean isFresher) {
        return extractJsonFromResponse(callGroqApi(buildResumeGenerationPrompt(userInputJson, jobDescription, isFresher)), userInputJson);
    }

    private String callGroqApi(String prompt) {
        try {
            // Groq uses OpenAI-compatible chat format
            String requestBody = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
                put("model", model);
                put("temperature", 0.3);
                put("max_tokens", 4096);
                put("messages", List.of(
                        new java.util.HashMap<>() {{
                            put("role", "user");
                            put("content", prompt);
                        }}
                ));
            }});

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                return root.path("choices").get(0).path("message").path("content").asText();
            }

            log.error("Groq API error {}: {}", response.statusCode(), response.body());
            return "";

        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage());
            return "";
        }
    }

    private String buildOptimizationPrompt(String resumeData, String jobDescription, boolean isFresher) {
        String levelHint = isFresher
                ? "This is a FRESHER resume. Focus on: education, academic projects, internships, certifications, skills. Do NOT add fake work experience."
                : "This is an EXPERIENCED professional resume. Focus on: achievements, impact metrics, leadership, technical depth.";
        return "You are an expert ATS resume optimizer for the Indian job market.\n"
                + levelHint + "\n"
                + "Rules: Keep all facts intact. Enhance bullet points with strong action verbs. Add relevant keywords from job description naturally. Return ONLY valid JSON in the same structure as input.\n"
                + "Job Description:\n" + (jobDescription != null ? jobDescription : "General professional role") + "\n"
                + "Resume Data (JSON):\n" + resumeData + "\n"
                + "Return ONLY the optimized JSON, no explanation, no markdown.";
    }

    private String buildAtsScorePrompt(String resumeText, String jobDescription) {
        return "You are an ATS expert. Analyze this resume against the job description.\n"
                + "Return ONLY a JSON object with this EXACT structure:\n"
                + "{\"score\":75.5,\"grade\":\"B+\",\"strengths\":[\"s1\",\"s2\"],\"improvements\":[\"i1\",\"i2\"],\"missingKeywords\":[\"k1\",\"k2\"],\"summary\":\"Brief 2-line summary\"}\n"
                + "Score: 90-100=A+, 80-89=A, 70-79=B+, 60-69=B, 50-59=C, below 50=D\n"
                + "Job Description:\n" + (jobDescription != null ? jobDescription : "General professional resume") + "\n"
                + "Resume Text:\n" + resumeText + "\nReturn ONLY the JSON.";
    }

    private String buildExtractionPrompt(String rawText) {
        return "Extract resume information from this text and return as JSON.\n"
                + "Return ONLY valid JSON with this structure (no markdown):\n"
                + "{\"personalInfo\":{\"name\":\"\",\"email\":\"\",\"phone\":\"\",\"location\":\"\",\"linkedin\":\"\",\"github\":\"\",\"website\":\"\"},"
                + "\"summary\":\"\","
                + "\"experience\":[{\"company\":\"\",\"position\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"current\":false,\"location\":\"\",\"description\":[]}],"
                + "\"education\":[{\"institution\":\"\",\"degree\":\"\",\"field\":\"\",\"startDate\":\"\",\"endDate\":\"\",\"grade\":\"\"}],"
                + "\"skills\":{\"technical\":[],\"soft\":[],\"languages\":[],\"tools\":[]},"
                + "\"projects\":[{\"name\":\"\",\"description\":\"\",\"technologies\":[],\"link\":\"\"}],"
                + "\"certifications\":[{\"name\":\"\",\"issuer\":\"\",\"date\":\"\"}],"
                + "\"achievements\":[]}\n"
                + "Resume Text:\n" + rawText + "\nReturn ONLY the JSON.";
    }

    private String buildResumeGenerationPrompt(String userInputJson, String jobDescription, boolean isFresher) {
        String levelHint = isFresher
                ? "FRESHER resume: Highlight education (CGPA/percentage), academic projects, skills, certifications, internships. Do NOT fabricate work experience. For projects, use strong action verbs. Summary should mention seeking first opportunity."
                : "EXPERIENCED resume: Use strong action verbs (Led, Developed, Achieved, Implemented, Scaled). Add quantifiable metrics (%, numbers, revenue, team size). Summary should be punchy and highlight years of experience.";
        return "You are an expert resume writer for the Indian job market. Create a highly ATS-optimized resume.\n"
                + levelHint + "\n"
                + "Include relevant keywords from job description. Return ONLY valid JSON in SAME structure as input.\n"
                + "Job Description:\n" + (jobDescription != null && !jobDescription.isEmpty() ? jobDescription : "General software/tech professional role") + "\n"
                + "User Resume Data:\n" + userInputJson + "\nReturn ONLY the enhanced JSON.";
    }

    private String extractJsonFromResponse(String response, String fallback) {
        if (response == null || response.isEmpty()) return fallback;
        try {
            String cleaned = response.trim();
            if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
            else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception e) {
            log.error("JSON parse failed: {}", e.getMessage());
            return fallback;
        }
    }

    private AtsScoreDto parseAtsScoreResponse(String response) {
        try {
            String json = extractJsonFromResponse(response, "{}");
            JsonNode node = objectMapper.readTree(json);
            List<String> strengths = new ArrayList<>(), improvements = new ArrayList<>(), missing = new ArrayList<>();
            node.path("strengths").forEach(n -> strengths.add(n.asText()));
            node.path("improvements").forEach(n -> improvements.add(n.asText()));
            node.path("missingKeywords").forEach(n -> missing.add(n.asText()));
            return AtsScoreDto.builder()
                    .score(node.path("score").asDouble(50.0))
                    .grade(node.path("grade").asText("C"))
                    .strengths(strengths).improvements(improvements).missingKeywords(missing)
                    .summary(node.path("summary").asText("Resume analyzed successfully."))
                    .build();
        } catch (Exception e) {
            return AtsScoreDto.builder().score(50.0).grade("C")
                    .strengths(List.of("Resume uploaded"))
                    .improvements(List.of("Add job description for detailed analysis"))
                    .missingKeywords(List.of())
                    .summary("Add job description for accurate scoring.").build();
        }
    }
}