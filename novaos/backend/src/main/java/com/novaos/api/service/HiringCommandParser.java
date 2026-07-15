package com.novaos.api.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaos.api.dto.HiringRequestDtos.CandidateInput;
import com.novaos.api.dto.HiringRequestDtos.ParseResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class HiringCommandParser {
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();
    private final HiringWorkflowRules rules;
    private final String apiKey;
    private final String apiUrl;

    public HiringCommandParser(HiringWorkflowRules rules, @Value("${gemini.api.key:}") String apiKey,
            @Value("${gemini.api.url:}") String apiUrl) {
        this.rules = rules; this.apiKey = apiKey; this.apiUrl = apiUrl;
    }

    public ParseResponse parse(String instruction) {
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("placeholder"))
            throw new IllegalStateException("GEMINI_API_KEY is missing. Obtain one from Google AI Studio and set it in backend/.env.");
        try {
            Map<String, Object> fields = new LinkedHashMap<>();
            for (String field : List.of("candidateName", "candidateEmail", "jobTitle", "department", "joiningDate",
                    "reportingManagerName", "hiringManagerName", "location", "employmentType"))
                fields.put(field, Map.of("type", "STRING", "nullable", true));
            fields.put("annualPackageLPA", Map.of("type", "NUMBER", "nullable", true));
            fields.put("annualSalaryAmount", Map.of("type", "INTEGER", "nullable", true));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "OBJECT");
            schema.put("properties", Map.of("candidate", Map.of("type", "OBJECT", "properties", fields),
                    "confidence", Map.of("type", "NUMBER")));
            schema.put("required", List.of("candidate", "confidence"));
            String prompt = "Extract only explicitly stated facts from this hiring instruction. Never infer protected attributes. "
                    + "Normalize annual salary to LPA and integer INR and joining date to yyyy-MM-dd. "
                    + "Reporting manager and hiring manager are different. Use null for absent fields. Instruction:\n" + instruction;
            Map<String, Object> body = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", schema,
                            "temperature", 0.0, "candidateCount", 1));
            HttpHeaders headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            Map response = http.postForObject(apiUrl + "?key=" + apiKey, new HttpEntity<>(body, headers), Map.class);
            List candidates = response == null ? null : (List) response.get("candidates");
            Map content = candidates == null || candidates.isEmpty() ? null : (Map) ((Map) candidates.get(0)).get("content");
            List parts = content == null ? null : (List) content.get("parts");
            if (parts == null || parts.isEmpty()) throw new IllegalStateException("Gemini returned no structured JSON.");
            JsonNode root = json.readTree(String.valueOf(((Map) parts.get(0)).get("text")));
            JsonNode c = root.path("candidate");
            Double lpa = number(c, "annualPackageLPA");
            Long amount = c.hasNonNull("annualSalaryAmount") ? c.path("annualSalaryAmount").asLong() : null;
            if (amount == null && lpa != null) amount = Math.round(lpa * 100000d);
            if (lpa == null && amount != null) lpa = amount / 100000d;
            CandidateInput input = new CandidateInput(text(c,"candidateName"), text(c,"candidateEmail"), text(c,"jobTitle"),
                    text(c,"department"), lpa, amount, text(c,"joiningDate"), text(c,"reportingManagerName"),
                    text(c,"hiringManagerName"), text(c,"location"), text(c,"employmentType"));
            List<String> missing = rules.missing(input);
            String followUp = missing.isEmpty() ? null : "To prepare the hiring request, please provide:\n"
                    + java.util.stream.IntStream.range(0, missing.size())
                    .mapToObj(i -> (i + 1) + ". " + label(missing.get(i))).collect(java.util.stream.Collectors.joining("\n"));
            double confidence=Math.max(0,Math.min(1,root.path("confidence").asDouble(0)));
            if(!missing.isEmpty()&&confidence>=1)confidence=.99;
            return new ParseResponse("CREATE_HIRING_REQUEST", input, missing, followUp, confidence);
        } catch (Exception e) {
            throw new IllegalStateException("Gemini structured parsing failed: " + e.getMessage(), e);
        }
    }

    private String text(JsonNode n, String key) { var v=n.path(key); return v.isMissingNode()||v.isNull()||v.asText().isBlank()?null:v.asText().trim(); }
    private Double number(JsonNode n, String key) { return n.hasNonNull(key) ? n.path(key).asDouble() : null; }
    private String label(String key) { return switch (key) {
        case "candidateName" -> "Candidate name"; case "candidateEmail" -> "Candidate email"; case "jobTitle" -> "Job title";
        case "annualPackageLPA" -> "Annual package"; case "joiningDate" -> "Joining date";
        case "reportingManagerName" -> "Reporting manager"; case "hiringManagerName" -> "Hiring manager"; default -> key;
    }; }
}
