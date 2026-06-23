package com.stock.stock_analyser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.TechnicalSignals;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls OpenAI Responses API to produce human-readable investment commentary.
 *
 * Configuration in application.yml:
 * openai:
 *   api-key: /absolute/path/to/openai-key.txt
 *   model-name: gpt-5.5
 *   service-tier: priority
 */
@Slf4j
@Service
public class OpenAiCommentaryService {

    private static final String OPENAI_URL = "https://api.openai.com/v1/responses";
    private static final String OPENAI_VALIDATE_URL = "https://api.openai.com/v1/models";
    private static final Duration KEY_VALIDATION_TIMEOUT = Duration.ofSeconds(8);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /** Path of the text file containing the OpenAI API key. */
    @Value("${openai.api-key:}")
    private String apiKeyFilePath;

    @Value("${openai.model-name:gpt-5.5}")
    private String modelName;

    @Value("${openai.service-tier:priority}")
    private String serviceTier;

    private String resolvedApiKey = "";
    private boolean apiKeyValid = false;
    private String apiKeyValidationMessage = "NOT_VALIDATED";

    public OpenAiCommentaryService() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }

    @jakarta.annotation.PostConstruct
    public void validateApiKey() {
        resolvedApiKey = loadApiKeyFromFile(apiKeyFilePath);

        if (resolvedApiKey.isBlank()) {
            apiKeyValid = false;
            apiKeyValidationMessage = "MISSING_OR_EMPTY_KEY_FILE";
            log.warn("OpenAI API key file is not readable/empty (openai.api-key={}). AI commentary will be skipped.", apiKeyFilePath);
            return;
        }

        String masked = resolvedApiKey.length() > 12
                ? resolvedApiKey.substring(0, 8) + "****" + resolvedApiKey.substring(resolvedApiKey.length() - 4)
                : "****";

        apiKeyValid = validateKeyAgainstOpenAi(resolvedApiKey);

        log.info("OpenAI config loaded. key={}, model={}, serviceTier={}, keyFilePath={}, keyValid={}, keyValidationMessage={}",
                masked, modelName, serviceTier, apiKeyFilePath, apiKeyValid, apiKeyValidationMessage);
    }

    public Mono<String> generateCommentary(String symbol, TechnicalSignals technical,
                                           InvestmentRecommendation recommendation) {
        if (resolvedApiKey.isBlank() || !apiKeyValid) {
            log.info("OpenAI API key unavailable/invalid. Skipping AI commentary for {}", symbol);
            return Mono.just("");
        }

        String prompt = buildPrompt(symbol, technical, recommendation);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("input", List.of(
                Map.of("role", "system",
                        "content", "You are an expert stock market analyst specializing in Indian equity markets (NSE). "
                                + "Provide concise, actionable investment analysis. "
                                + "Keep response under 150 words."),
                Map.of("role", "user", "content", prompt)
        ));
        body.put("max_output_tokens", 300);
        body.put("temperature", 0.4);

        if (serviceTier != null && !serviceTier.isBlank()) {
            body.put("service_tier", serviceTier);
        }

        return sendResponsesRequest(body)
                .map(this::extractContent)
                .doOnNext(c -> log.info("OpenAI commentary generated for {}", symbol))
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("OpenAI call failed for {}: status={} body={}",
                            symbol, ex.getStatusCode().value(), shrink(ex.getResponseBodyAsString()));

                    // Some accounts/models reject service_tier on /v1/responses; retry once without it.
                    if (ex.getStatusCode().value() == 400 && body.containsKey("service_tier")) {
                        Map<String, Object> fallbackBody = new LinkedHashMap<>(body);
                        fallbackBody.remove("service_tier");
                        log.warn("Retrying OpenAI call for {} without service_tier", symbol);
                        return sendResponsesRequest(fallbackBody)
                                .map(this::extractContent)
                                .onErrorResume(retryEx -> {
                                    log.error("OpenAI retry failed for {}: {}", symbol, retryEx.getMessage());
                                    return Mono.just("AI commentary unavailable.");
                                });
                    }

                    return Mono.just("AI commentary unavailable.");
                })
                .onErrorResume(e -> {
                    log.error("OpenAI call failed for {}: {}", symbol, e.getMessage());
                    return Mono.just("AI commentary unavailable.");
                });
    }

    public boolean isApiKeyValid() {
        return apiKeyValid;
    }

    public String getApiKeyValidationMessage() {
        return apiKeyValidationMessage;
    }

    private String loadApiKeyFromFile(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "";
        }

        try {
            String content = Files.readString(Path.of(filePath)).trim();
            return content.isBlank() ? "" : content;
        } catch (Exception e) {
            log.error("Failed to read OpenAI API key file from {}: {}", filePath, e.getMessage());
            return "";
        }
    }

    private boolean validateKeyAgainstOpenAi(String key) {
        try {
            webClient.get()
                    .uri(OPENAI_VALIDATE_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(KEY_VALIDATION_TIMEOUT)
                    .block();
            apiKeyValidationMessage = "VALID";
            return true;
        } catch (WebClientResponseException e) {
            apiKeyValidationMessage = "INVALID_HTTP_" + e.getStatusCode().value();
            log.warn("OpenAI key validation failed. status={}, body={}",
                    e.getStatusCode().value(), shrink(e.getResponseBodyAsString()));
            return false;
        } catch (Exception e) {
            apiKeyValidationMessage = "VALIDATION_ERROR";
            log.warn("OpenAI key validation failed due to runtime error: {}", e.getMessage());
            return false;
        }
    }

    private String shrink(String text) {
        if (text == null) return "";
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }

    private String buildPrompt(String symbol, TechnicalSignals t, InvestmentRecommendation r) {
        return String.format(
                "Stock: %s (NSE India)\n" +
                        "Current Price: INR %.2f\n" +
                        "52-Week High: INR %.2f, Low: INR %.2f (Price vs 52W High: %.1f%%)\n\n" +
                        "Technical Signals:\n" +
                        "  RSI-14: %.1f (%s)\n" +
                        "  MACD: %.2f | Signal: %.2f | Histogram: %.2f (%s)\n" +
                        "  SMA20: %.2f | SMA50: %.2f | SMA200: %.2f (%s)\n" +
                        "  Bollinger: Upper=%.2f, Middle=%.2f, Lower=%.2f (%s)\n" +
                        "  Trend: %s | ADX: %.1f | Volume Spike: %s\n\n" +
                        "Recommendation: %s (Confidence: %d%%)\n" +
                        "Entry Zone: INR %.2f - INR %.2f\n" +
                        "Target: INR %.2f | Stop-Loss: INR %.2f\n" +
                        "Upside: %.1f%% | Downside: %.1f%% | R/R: %.2f\n\n" +
                        "In 2-3 sentences, provide a clear investment opinion for a retail investor. " +
                        "Mention key catalysts or risks.",
                symbol,
                safe(t.getCurrentPrice()),
                safe(t.getFiftyTwoWeekHigh()), safe(t.getFiftyTwoWeekLow()), safe(t.getPriceVs52WeekHighPct()),
                safe(t.getRsi14()), nullStr(t.getRsiSignal()),
                safe(t.getMacdLine()), safe(t.getMacdSignal()), safe(t.getMacdHistogram()), nullStr(t.getMacdSignalType()),
                safe(t.getSma20()), safe(t.getSma50()), safe(t.getSma200()), nullStr(t.getMaSignal()),
                safe(t.getBbUpper()), safe(t.getBbMiddle()), safe(t.getBbLower()), nullStr(t.getBbSignal()),
                nullStr(t.getTrendDirection()), safe(t.getAdx14()), t.isVolumeSpike(),
                r.getAction(), r.getConfidenceScore(),
                safe(r.getEntryPriceLow()), safe(r.getEntryPriceHigh()),
                safe(r.getTargetPrice()), safe(r.getStopLossPrice()),
                safe(r.getPotentialUpsidePct()), safe(r.getPotentialDownsidePct()), safe(r.getRiskRewardRatio())
        );
    }

    private Mono<String> sendResponsesRequest(Map<String, Object> body) {
        return webClient.post()
                .uri(OPENAI_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + resolvedApiKey)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class);
    }

    private String extractContent(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            String outputText = root.path("output_text").asText("");
            if (outputText != null && !outputText.isBlank()) {
                return outputText;
            }

            String directText = root.at("/output/0/content/0/text").asText("");
            if (directText != null && !directText.isBlank()) {
                return directText;
            }

            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode c : content) {
                            String text = c.path("text").asText("");
                            if (text != null && !text.isBlank()) {
                                return text;
                            }
                        }
                    }
                }
            }

            return "No commentary available.";
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", e.getMessage());
            return "AI commentary unavailable.";
        }
    }

    private double safe(Double v) { return v != null ? v : 0.0; }
    private String nullStr(String s) { return s != null ? s : "N/A"; }
}
