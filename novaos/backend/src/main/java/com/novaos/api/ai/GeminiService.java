package com.novaos.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.novaos.api.dto.HiringPassportDtos;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GeminiService {

    private static final Logger logger = LoggerFactory.getLogger(GeminiService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private boolean isKeyUsable() {
        return apiKey != null && !apiKey.isEmpty()
                && !apiKey.toLowerCase().contains("placeholder");
    }

    public Map<String, Object> analyzePolicyDocument(String documentText) {
        if (documentText == null || documentText.isBlank()) {
            throw new IllegalArgumentException("The uploaded policy PDF contains no readable text.");
        }
        String clipped = documentText.length() > 60000 ? documentText.substring(0, 60000) : documentText;
        String prompt = "You are the NovaOS enterprise policy reader. Analyze only the supplied company policy text. "
                + "Do not invent rules and do not activate rules. Page markers in the supplied text are authoritative. Return raw JSON exactly shaped as: "
                + "{documentSummary:string,majorPolicyAreas:string[],keyWarnings:string[],detectedDepartments:string[],detectedEmploymentTypes:string[],"
                + "approvalRules:string[],salaryBands:object[],policies:[{policyCode:string,policyName:string,category:string,description:string,"
                + "departments:string[],jobTitles:string[],allowedEmploymentTypes:string[],minSalaryLPA:number|null,"
                + "maxSalaryLPA:number|null,approvalThresholdLPA:number|null,blockThresholdLPA:number|null,requiredApproverRoles:string[],"
                + "blocking:boolean,sourceEvidence:string,sourcePage:number|null,confidence:number}]}. "
                + "Evidence must be a concise excerpt from the supplied text. Confidence is 0 to 1. "
                + "Page numbers must be null unless the text explicitly identifies a page marker.\n\nPOLICY DOCUMENT:\n" + clipped;
        try {
            JsonNode root = objectMapper.readTree(callGeminiRaw(prompt, true));
            return objectMapper.convertValue(root, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception error) {
            throw new IllegalStateException("Gemini policy extraction failed: " + error.getMessage(), error);
        }
    }

    /**
     * Low-level single-turn call to the Gemini generateContent endpoint.
     * Returns the raw model text (markdown code fences stripped down to the
     * inner JSON object when present), or null if the key is unusable or the
     * call/parse fails for any reason - callers are expected to fall back
     * gracefully rather than fabricate a result.
     */
    private String callGeminiRaw(String systemInstruction) { return callGeminiRaw(systemInstruction, false); }

    private String callGeminiRaw(String systemInstruction, boolean structuredJson) {
        if (!isKeyUsable()) {
            throw new IllegalStateException("GEMINI_API_KEY is missing or contains placeholder data.");
        }
        try {
            String url = apiUrl + "?key=" + apiKey;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", systemInstruction);
            Map<String, Object> partContainer = new HashMap<>();
            partContainer.put("parts", List.of(textPart));
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("contents", List.of(partContainer));
            if (structuredJson) requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            Map body = response.getBody();
            if (body == null) throw new IllegalStateException("Gemini returned an empty response body.");

            List candidatesList = (List) body.get("candidates");
            if (candidatesList == null || candidatesList.isEmpty()) throw new IllegalStateException("Gemini returned no candidates.");
            Map candidate = (Map) candidatesList.get(0);
            Map content = (Map) candidate.get("content");
            if (content == null) throw new IllegalStateException("Gemini returned no content.");
            List parts = (List) content.get("parts");
            if (parts == null || parts.isEmpty()) throw new IllegalStateException("Gemini returned no text parts.");
            Map part = (Map) parts.get(0);
            String resultText = (String) part.get("text");
            if (resultText == null) throw new IllegalStateException("Gemini returned a blank text response.");

            resultText = resultText.trim();
            if (resultText.startsWith("```")) {
                int start = resultText.indexOf("{");
                int end = resultText.lastIndexOf("}");
                if (start != -1 && end != -1) {
                    resultText = resultText.substring(start, end + 1);
                }
            }
            return resultText;
        } catch (Exception e) {
            logger.error("Gemini API call failed: {}", e.getMessage());
            throw new IllegalStateException("Gemini API call failed: " + e.getMessage(), e);
        }
    }


    public HiringPassportDtos.ParseResponse parseHiringInstruction(String instruction) {
        if (!isKeyUsable()) {
            throw new IllegalStateException("GEMINI_API_KEY is missing. Obtain a Gemini API key from Google AI Studio and set GEMINI_API_KEY in backend/.env.");
        }
        String prompt = "You are the NovaOS hiring instruction parser. Extract only facts explicitly present in the HR instruction. "
                + "Never infer or emit protected attributes such as age, gender, religion, caste, ethnicity, disability, marital status or nationality. "
                + "Normalize annual CTC to integer INR, date to yyyy-MM-dd, and intent to HIRE_CANDIDATE. "
                + "Put every absent mandatory field in missingFields and never invent it. Instruction:\n" + instruction;
        try {
            Map<String, Object> candidateProperties = new LinkedHashMap<>();
            candidateProperties.put("name", Map.of("type", "STRING"));
            candidateProperties.put("email", Map.of("type", "STRING"));
            candidateProperties.put("position", Map.of("type", "STRING"));
            candidateProperties.put("annualCtc", Map.of("type", "INTEGER"));
            candidateProperties.put("joiningDate", Map.of("type", "STRING"));
            candidateProperties.put("department", Map.of("type", "STRING"));
            candidateProperties.put("location", Map.of("type", "STRING"));
            candidateProperties.put("manager", Map.of("type", "STRING"));
            candidateProperties.put("probationMonths", Map.of("type", "INTEGER"));
            candidateProperties.put("requiredSkills", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")));
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "OBJECT");
            schema.put("properties", Map.of(
                    "intent", Map.of("type", "STRING", "enum", List.of("HIRE_CANDIDATE")),
                    "candidate", Map.of("type", "OBJECT", "properties", candidateProperties),
                    "missingFields", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                    "confidence", Map.of("type", "NUMBER")
            ));
            schema.put("required", List.of("intent", "candidate", "missingFields", "confidence"));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            body.put("generationConfig", Map.of("responseMimeType", "application/json", "responseSchema", schema,
                    "temperature", 0.0, "candidateCount", 1));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl + "?key=" + apiKey,
                    new HttpEntity<>(body, headers), Map.class);
            Map responseBody = response.getBody();
            if (responseBody == null) throw new IllegalStateException("Gemini returned an empty response body.");
            List candidates = (List) responseBody.get("candidates");
            if (candidates == null || candidates.isEmpty()) throw new IllegalStateException("Gemini returned no structured candidate.");
            Map content = (Map) ((Map) candidates.get(0)).get("content");
            List parts = content == null ? null : (List) content.get("parts");
            if (parts == null || parts.isEmpty()) throw new IllegalStateException("Gemini returned no structured JSON part.");
            String json = String.valueOf(((Map) parts.get(0)).get("text"));
            JsonNode root = objectMapper.readTree(json);
            JsonNode c = root.path("candidate");
            HiringPassportDtos.CandidateData candidate = new HiringPassportDtos.CandidateData(
                    nullableText(c, "name"), nullableText(c, "email"), nullableText(c, "position"),
                    c.hasNonNull("annualCtc") ? c.path("annualCtc").asLong() : null,
                    nullableText(c, "joiningDate"), nullableText(c, "department"), nullableText(c, "location"),
                    nullableText(c, "manager"), c.hasNonNull("probationMonths") ? c.path("probationMonths").asInt() : null,
                    objectMapper.convertValue(c.path("requiredSkills"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
            );
            List<String> missing = objectMapper.convertValue(root.path("missingFields"),
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return new HiringPassportDtos.ParseResponse(root.path("intent").asText("HIRE_CANDIDATE"),
                    candidate, missing == null ? List.of() : missing, root.path("confidence").asDouble(0));
        } catch (Exception e) {
            logger.error("Gemini structured hiring parse failed: {}", e.getMessage());
            throw new IllegalStateException("Gemini structured parsing failed: " + e.getMessage(), e);
        }
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) return null;
        return value.asText().trim();
    }

    // =================================================================
    // AI Command Center: single-call intent classification + extraction
    // =================================================================

    public static class CandidateFields {
        public String name;
        public String role;
        public String email;
        public String department;
        public String joiningDate;
        public String ctc;
        public Integer matchScore;
        public String summary;
        public List<String> strengths = new ArrayList<>();
        public List<String> weaknesses = new ArrayList<>();
    }

    public static class ChatIntentResult {
        public String intent = "chat"; // "hire" | "vet" | "chat"
        public String replyText;
        public CandidateFields candidate;
        public boolean liveModel; // true only if Gemini actually answered this turn
    }

    /**
     * Sends the operator's raw prompt to Gemini once, asking it to classify
     * intent, draft a reply, and (if a candidate is involved) extract/score
     * that candidate - replacing the old hardcoded "rahul"/"sophia" branches.
     */
    public ChatIntentResult handleCommand(String prompt) {
        String systemInstruction =
            "You are Nova, the AI recruitment operations assistant embedded in the NovaOS HR platform. " +
            "A recruiter just typed this into the AI Command Center:\n\"" + prompt + "\"\n\n" +
            "First classify the intent as exactly one of:\n" +
            "- \"hire\": operator wants to register a named candidate with concrete offer terms (CTC, joining date, etc).\n" +
            "- \"vet\": operator wants a named candidate's profile screened/scored against a role, no firm offer yet.\n" +
            "- \"chat\": anything else (pipeline stats, workflow questions, general platform questions - no single candidate to extract).\n\n" +
            "If intent is \"hire\" or \"vet\", extract the candidate's name and target role from the text. " +
            "Use \"Not specified\" for any field not present in the text - never invent an email or CTC that " +
            "wasn't given. Score matchScore (0-100) honestly based only on what the prompt actually states about " +
            "the candidate - if the prompt gives no real qualifications, score conservatively and say so in the " +
            "summary rather than defaulting to a high number. Give 2-3 short strengths and weaknesses grounded " +
            "in the prompt text.\n\n" +
            "Respond with ONLY raw JSON, no markdown fences, no extra commentary, in exactly this shape:\n" +
            "{\n" +
            "  \"intent\": \"hire|vet|chat\",\n" +
            "  \"replyText\": \"a concise 1-3 sentence reply for the operator\",\n" +
            "  \"candidate\": null\n" +
            "}\n" +
            "...where \"candidate\" must be a populated object (not null) whenever intent is hire or vet, shaped as:\n" +
            "{ \"name\":\"\", \"role\":\"\", \"email\":\"\", \"department\":\"\", \"joiningDate\":\"\", \"ctc\":\"\", " +
            "\"matchScore\":0, \"summary\":\"\", \"strengths\":[\"\"], \"weaknesses\":[\"\"] }";

        String raw = callGeminiRaw(systemInstruction);
        try {
            JsonNode node = objectMapper.readTree(raw);
            ChatIntentResult result = new ChatIntentResult();
            result.intent = node.path("intent").asText("chat");
            result.replyText = node.path("replyText").asText("Understood.");
            result.liveModel = true;

            JsonNode c = node.path("candidate");
            if (c.isObject()) {
                CandidateFields cf = new CandidateFields();
                cf.name = textOrNull(c, "name");
                cf.role = textOrNull(c, "role");
                cf.email = textOrNull(c, "email");
                cf.department = textOrNull(c, "department");
                cf.joiningDate = textOrNull(c, "joiningDate");
                cf.ctc = textOrNull(c, "ctc");
                cf.matchScore = c.hasNonNull("matchScore") ? c.path("matchScore").asInt() : null;
                cf.summary = textOrNull(c, "summary");
                c.path("strengths").forEach(n -> cf.strengths.add(n.asText()));
                c.path("weaknesses").forEach(n -> cf.weaknesses.add(n.asText()));
                result.candidate = cf;
            }
            return result;
        } catch (Exception e) {
            logger.error("Failed to parse Gemini chat JSON: {}", e.getMessage());
            throw new IllegalStateException("Gemini returned invalid command JSON: " + e.getMessage(), e);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asText();
    }

    // =================================================================
    // Candidate resume analysis (used by RecruitmentService)
    // =================================================================

    public static class CandidateAnalysis {
        public int matchScore;
        public String roleCompatibility;
        public List<String> strengths = new ArrayList<>();
        public List<String> weaknesses = new ArrayList<>();
    }

    /**
     * Vets a candidate profile/resume text against a job description with a
     * real Gemini call. Falls back to a keyword-overlap heuristic (clearly
     * labelled as such) if the API is unreachable.
     */
    public CandidateAnalysis analyzeCandidateResume(String candidateName, String resumeText, String targetRole) {
        logger.info("Triggering Gemini analysis for candidate: {} target role: {}", candidateName, targetRole);

        String systemInstruction =
            "Assess this candidate profile against the target role.\n" +
            "Candidate: " + candidateName + "\n" +
            "Target role: " + targetRole + "\n" +
            "Profile/notes: \"" + resumeText + "\"\n\n" +
            "Respond with ONLY raw JSON, no markdown fences, no commentary:\n" +
            "{ \"matchScore\": 0, \"roleCompatibility\": \"one sentence\", \"strengths\": [\"\"], \"weaknesses\": [\"\"] }";

        String raw = callGeminiRaw(systemInstruction);
        if (raw != null) {
            try {
                JsonNode node = objectMapper.readTree(raw);
                CandidateAnalysis analysis = new CandidateAnalysis();
                analysis.matchScore = node.path("matchScore").asInt(70);
                analysis.roleCompatibility = node.path("roleCompatibility").asText("");
                node.path("strengths").forEach(n -> analysis.strengths.add(n.asText()));
                node.path("weaknesses").forEach(n -> analysis.weaknesses.add(n.asText()));
                return analysis;
            } catch (Exception e) {
                logger.error("Failed to parse Gemini analysis JSON: {}", e.getMessage());
                throw new IllegalStateException("Gemini returned invalid candidate analysis JSON: " + e.getMessage(), e);
            }
        }

        throw new IllegalStateException("Gemini did not return a candidate analysis response.");
    }

    // =================================================================
    // Structured hiring-detail extraction (used by HiringWorkflowService)
    // Behavior unchanged - only refactored to share callGeminiRaw().
    // =================================================================

    public String generateOfferLetterText(String name, String role, String salary, String joiningDate, 
                                          String branch, String location, String managerName, String hrName) {
        logger.info("Generating offer letter text for {} as {}", name, role);

        String systemInstruction = "Generate a professional offer letter text in plain text format (no HTML, no markdown) " +
            "for the following details:\n\n" +
            "Candidate Name: " + name + "\n" +
            "Position: " + role + "\n" +
            "CTC/Salary: " + salary + "\n" +
            "Joining Date: " + joiningDate + "\n" +
            "Branch: " + branch + "\n" +
            "Location: " + location + "\n" +
            "Manager Name: " + managerName + "\n" +
            "HR Name: " + hrName + "\n\n" +
            "Create a formal, professional offer letter that includes:\n" +
            "1. Date and header\n" +
            "2. Candidate name and address\n" +
            "3. Subject line\n" +
            "4. Opening paragraph expressing the offer\n" +
            "5. Details section (role, salary, joining date, location)\n" +
            "6. Benefits and terms\n" +
            "7. Conditions and next steps\n" +
            "8. Sign-off with manager and HR signatures\n\n" +
            "Use professional tone and format suitable for printing. Return only the plain text content, no JSON.";

        String result = callGeminiRaw(systemInstruction);
        logger.info("Successfully generated offer letter from Gemini API");
        return result;
    }

    public String extractHiringDetails(String prompt) {
        logger.info("Triggering Gemini field extraction for prompt: {}", prompt);

        String systemInstruction = "Extract hiring details from the following natural-language request:\n" +
            "\"" + prompt + "\"\n\n" +
            "You must extract:\n" +
            "1. name (the candidate's name)\n" +
            "2. role (the job title / designation, e.g. Software Engineer)\n" +
            "3. joiningDate (joining date, e.g. 1 August 2026)\n" +
            "4. ctc (the CTC / compensation details, e.g. \u20B912,00,000 per annum)\n" +
            "5. email (candidate's email address)\n" +
            "6. department (candidate's department, e.g. Engineering)\n\n" +
            "Respond ONLY with a valid JSON object matching this structure (no markdown formatting, no comments, just the raw JSON):\n" +
            "{\n" +
            "  \"name\": \"Extract Name\",\n" +
            "  \"role\": \"Extract Role\",\n" +
            "  \"joiningDate\": \"Extract Joining Date\",\n" +
            "  \"ctc\": \"Extract CTC\",\n" +
            "  \"email\": \"Extract Email\",\n" +
            "  \"department\": \"Extract Department\"\n" +
            "}";

        String result = callGeminiRaw(systemInstruction);
        logger.info("Successfully extracted hiring details from Gemini API: {}", result);
        return result;
    }
}
