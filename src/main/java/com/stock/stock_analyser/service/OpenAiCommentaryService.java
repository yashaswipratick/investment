package com.stock.stock_analyser.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.stock_analyser.dto.InvestmentRecommendation;
import com.stock.stock_analyser.dto.TechnicalSignals;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.resolver.NoopAddressResolverGroup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import javax.net.ssl.SSLException;
import java.net.URI;
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
        this.webClient    = buildProxyAwareWebClient();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Builds a WebClient that works both on the Walmart corporate network
     * (routes via HTTPS_PROXY) and on a personal machine (direct connection).
     *
     * Reactor Netty does NOT auto-read system proxy env vars — it must be
     * wired explicitly, same pattern as StockHistoryDataHttpEntryLoader.
     */
    private WebClient buildProxyAwareWebClient() {
        HttpClient httpClient = HttpClient.create().followRedirect(true);

        // ── Corporate proxy (reads HTTPS_PROXY / HTTP_PROXY env vars) ─────────
        String proxyEnv = System.getenv("HTTPS_PROXY");
        if (proxyEnv == null || proxyEnv.isBlank()) proxyEnv = System.getenv("HTTP_PROXY");
        if (proxyEnv == null || proxyEnv.isBlank()) proxyEnv = System.getenv("https_proxy");
        if (proxyEnv == null || proxyEnv.isBlank()) proxyEnv = System.getenv("http_proxy");

        if (proxyEnv != null && !proxyEnv.isBlank()) {
            try {
                URI proxyUri   = URI.create(proxyEnv);
                String pHost   = proxyUri.getHost();
                int    pPort   = proxyUri.getPort() > 0 ? proxyUri.getPort() : 8080;
                String noProxy = resolveNoProxy();
                httpClient = httpClient.proxy(proxy -> proxy
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(pHost)
                        .port(pPort)
                        .nonProxyHosts(noProxy)
                );
                log.info("OpenAI WebClient: routing via proxy {}:{}", pHost, pPort);
            } catch (Exception e) {
                log.warn("OpenAI WebClient: failed to parse proxy '{}': {}", proxyEnv, e.getMessage());
            }
        }

        // ── Trust corporate SSL inspection certificate ─────────────────────────
        try {
            SslContext ssl = SslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .build();
            httpClient = httpClient.secure(spec -> spec.sslContext(ssl));
        } catch (SSLException e) {
            log.warn("OpenAI WebClient: SSL context setup failed: {}", e.getMessage());
        }

        // ── DNS: let the proxy resolve hostnames, not local DNS ──────────────
        // Walmart's corporate DNS doesn't resolve external domains like api.openai.com.
        // By default Reactor Netty resolves hostnames locally before sending to proxy,
        // causing "Failed to resolve 'api.openai.com'" errors.
        // NoopAddressResolverGroup skips local DNS entirely — the proxy handles resolution.
        // This only applies when a proxy is actually configured above.
        if (proxyEnv != null && !proxyEnv.isBlank()) {
            httpClient = httpClient.resolver(NoopAddressResolverGroup.INSTANCE);
            log.debug("OpenAI WebClient: local DNS resolution disabled (proxy handles it)");
        }

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private String resolveNoProxy() {
        String noProxy = System.getenv("NO_PROXY");
        if (noProxy == null || noProxy.isBlank()) noProxy = System.getenv("no_proxy");
        return (noProxy != null && !noProxy.isBlank())
                ? noProxy.replace(",", "|")
                : "localhost|127.0.0.1|*.walmart.com|*.walmartlabs.com|*.wal-mart.com";
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
        log.debug("[OpenAI][{}] Sending prompt to {} (model={}, serviceTier={}):\n{}",
                  symbol, OPENAI_URL, modelName, serviceTier, prompt);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelName);
        body.put("input", List.of(
                Map.of("role", "system", "content", buildSystemPrompt()),
                Map.of("role", "user",   "content", prompt)
        ));
        // Reasoning models (gpt-5, o-series) consume hidden "thinking" tokens
        // BEFORE generating the visible response. Those internal tokens count
        // against max_output_tokens, leaving little room for the actual commentary.
        // Use a much higher limit for reasoning models so the 4-section output fits.
        int maxTokens = isReasoningModel(modelName) ? 2000 : 600;
        body.put("max_output_tokens", maxTokens);
        log.debug("[OpenAI][{}] max_output_tokens={} (reasoningModel={})", symbol, maxTokens, isReasoningModel(modelName));

        // temperature is NOT supported by reasoning/flagship models like gpt-5, o3, o4-mini.
        // Only add it for classic chat-completion models (gpt-4o, gpt-4-turbo, gpt-3.5-turbo etc.)
        if (supportsTemperature(modelName)) {
            body.put("temperature", 0.3);
        }

        if (serviceTier != null && !serviceTier.isBlank()) {
            body.put("service_tier", serviceTier);
        }

        return sendResponsesRequest(body)
                .doOnNext(rawJson -> log.debug("[OpenAI][{}] Raw JSON response: {}", symbol, shrink(rawJson, 2000)))
                .map(this::extractContent)
                .doOnNext(commentary -> {
                    log.info("[OpenAI][{}] Commentary extracted successfully ({} chars). Content:\n{}",
                             symbol, commentary.length(), commentary);
                })
                .onErrorResume(WebClientResponseException.class, ex -> {
                    log.error("OpenAI call failed for {}: status={} body={}",
                            symbol, ex.getStatusCode().value(), shrink(ex.getResponseBodyAsString()));

                    // Retry once stripping unsupported params (service_tier, temperature).
                    // gpt-5 and reasoning models reject temperature; some accounts reject service_tier.
                    if (ex.getStatusCode().value() == 400
                            && (body.containsKey("service_tier") || body.containsKey("temperature"))) {
                        Map<String, Object> fallbackBody = new LinkedHashMap<>(body);
                        fallbackBody.remove("service_tier");
                        fallbackBody.remove("temperature");
                        log.warn("Retrying OpenAI call for {} without service_tier/temperature (model={})", symbol, modelName);
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
        return shrink(text, 300);
    }

    private String shrink(String text, int maxLen) {
        if (text == null) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...[truncated]";
    }

    // ── Prompt builders ────────────────────────────────────────────────────────

    /**
     * System prompt — gives GPT a precise persona, output format, and constraints
     * so the response is structured, factual, and actionable every time.
     */
    private String buildSystemPrompt() {
        return """
                You are a senior equity research analyst with 15+ years of experience in Indian equity markets (NSE/BSE).
                Your role is to translate quantitative technical signals into clear, actionable investment commentary
                for both retail and institutional investors.

                STRICT OUTPUT FORMAT — always return exactly these 4 sections, no more, no less:

                📊 TREND SUMMARY
                One sentence describing the current price trend using the moving averages and ADX data.

                🎯 ENTRY & EXIT PLAN
                State the recommended action (BUY/HOLD/SELL/AVOID), the ideal entry price range, the stop-loss level,
                and the profit target. Explain WHY these levels make sense given the technical data.

                ⚠️ KEY RISKS
                Two or three specific risks that could invalidate the bullish/bearish thesis.
                Be concrete — mention RSI levels, volume patterns, or key support/resistance breaks.

                💡 FINAL VERDICT
                One clear sentence summarising whether this is a high-conviction trade or a wait-and-watch situation,
                and what the single most important trigger to watch is.

                RULES:
                - Use INR (₹) for all prices
                - Be specific — use exact price numbers, not vague ranges
                - Never say "it depends" or give non-committal answers
                - Do NOT repeat the raw indicator numbers — interpret them in plain English
                - Total response must be under 250 words
                """;
    }

    /**
     * User prompt — provides all computed indicators in a clean, labelled format
     * so GPT has precise context without ambiguity.
     */
    private String buildPrompt(String symbol, TechnicalSignals t, InvestmentRecommendation r) {
        // Price change period label (6M / 1Y / 2Y / 3Y) and value
        String changePeriod = t.getPriceChangePeriodLabel() != null ? t.getPriceChangePeriodLabel() : "6M";
        String changePct    = t.getPriceChangePct() != null
                ? String.format("%.1f%%", t.getPriceChangePct()) : "N/A";

        // Volume trend label
        String volTrend = t.getVolumeTrend() != null ? t.getVolumeTrend() : "N/A";

        // ADX interpretation
        String adxInterpret;
        if (t.getAdx14() == null)      adxInterpret = "N/A";
        else if (t.getAdx14() > 40)    adxInterpret = "STRONG TREND";
        else if (t.getAdx14() > 25)    adxInterpret = "MODERATE TREND";
        else                           adxInterpret = "WEAK/SIDEWAYS";

        return String.format(
                """
                ═══ STOCK: %s (NSE India) ═══

                PRICE SNAPSHOT
                  Current Price   : ₹%.2f
                  52-Week High    : ₹%.2f  |  52-Week Low: ₹%.2f
                  Price vs 52W High: %.1f%%  (negative = below peak)
                  %s Price Change : %s

                MOMENTUM INDICATORS
                  RSI-14          : %.1f  → %s
                  MACD Line       : %.3f  |  Signal: %.3f  |  Histogram: %.3f  → %s
                  Volume Trend    : %s  (5-day avg vs 20-day avg)
                  Volume Spike    : %s

                TREND & MOVING AVERAGES
                  SMA20 / SMA50 / SMA200 : ₹%.2f / ₹%.2f / ₹%.2f
                  MA Signal       : %s  (e.g. GOLDEN_CROSS = bullish long-term)
                  ADX-14          : %.1f  → %s
                  Trend Direction : %s

                VOLATILITY (BOLLINGER BANDS 20-period, 2σ)
                  Upper Band      : ₹%.2f
                  Middle Band     : ₹%.2f  (20-day SMA)
                  Lower Band      : ₹%.2f
                  Price Position  : %s

                SUPPORT & RESISTANCE (50-day range)
                  Support Level   : ₹%.2f
                  Resistance Level: ₹%.2f

                ALGORITHMIC RECOMMENDATION
                  Action          : %s
                  Confidence Score: %d / 100
                  Entry Zone      : ₹%.2f – ₹%.2f
                  Target Price    : ₹%.2f  (Upside: %.1f%%)
                  Stop-Loss       : ₹%.2f  (Downside: %.1f%%)
                  Risk/Reward     : %.2f
                  Rationale       : %s

                Based on ALL the above data, provide your structured analysis using the exact 4-section format
                defined in your system instructions.
                """,
                symbol,
                safe(t.getCurrentPrice()),
                safe(t.getFiftyTwoWeekHigh()), safe(t.getFiftyTwoWeekLow()), safe(t.getPriceVs52WeekHighPct()),
                changePeriod, changePct,
                safe(t.getRsi14()), nullStr(t.getRsiSignal()),
                safe(t.getMacdLine()), safe(t.getMacdSignal()), safe(t.getMacdHistogram()), nullStr(t.getMacdSignalType()),
                volTrend,
                t.isVolumeSpike() ? "YES — current volume > 1.5× 20-day avg" : "No",
                safe(t.getSma20()), safe(t.getSma50()), safe(t.getSma200()),
                nullStr(t.getMaSignal()),
                safe(t.getAdx14()), adxInterpret,
                nullStr(t.getTrendDirection()),
                safe(t.getBbUpper()), safe(t.getBbMiddle()), safe(t.getBbLower()), nullStr(t.getBbSignal()),
                safe(t.getSupportLevel()), safe(t.getResistanceLevel()),
                r.getAction(), r.getConfidenceScore(),
                safe(r.getEntryPriceLow()), safe(r.getEntryPriceHigh()),
                safe(r.getTargetPrice()), safe(r.getPotentialUpsidePct()),
                safe(r.getStopLossPrice()), safe(r.getPotentialDownsidePct()),
                safe(r.getRiskRewardRatio()),
                r.getRationale() != null ? shrink(r.getRationale(), 400) : "N/A"
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

    /**
     * Extracts the assistant text from the OpenAI /v1/responses JSON.
     *
     * gpt-5 / reasoning model response structure:
     * {
     *   "status": "completed",
     *   "output_text": "",            ← always empty for gpt-5
     *   "output": [
     *     { "type": "reasoning", "content": [] },   ← skip
     *     { "type": "message",   "content": [
     *         { "type": "output_text", "text": "..." }   ← extract this
     *     ]}
     *   ]
     * }
     *
     * Classic chat-completion models (gpt-4o etc.) populate "output_text" directly.
     */
    private String extractContent(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Check status — warn if incomplete (max_output_tokens too small)
            String status = root.path("status").asText("");
            if ("incomplete".equals(status)) {
                String reason = root.at("/incomplete_details/reason").asText("unknown");
                log.warn("OpenAI response was incomplete. reason={} — consider increasing max_output_tokens", reason);
            }

            // Path 1: Classic models populate output_text at root level
            String outputText = root.path("output_text").asText("").trim();
            if (!outputText.isBlank()) {
                log.debug("extractContent: found via root.output_text");
                return outputText;
            }

            // Path 2: gpt-5 / reasoning models — iterate output array,
            // skip "reasoning" type items, extract text from "message" type items
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    String itemType = item.path("type").asText("");
                    if (!"message".equals(itemType)) {
                        continue;  // skip reasoning / other internal items
                    }
                    JsonNode content = item.path("content");
                    if (content.isArray()) {
                        for (JsonNode c : content) {
                            String text = c.path("text").asText("").trim();
                            if (!text.isBlank()) {
                                log.debug("extractContent: found via output[type=message].content[].text");
                                return text;
                            }
                        }
                    }
                }
            }

            // Convert iterator to list for readable log output
            List<String> fieldNames = new java.util.ArrayList<>();
            root.fieldNames().forEachRemaining(fieldNames::add);
            log.warn("extractContent: no text found in response. status={} responseFields={}",
                     status, fieldNames);
            return "No commentary available.";
        } catch (Exception e) {
            log.error("Failed to parse OpenAI response: {}", e.getMessage());
            return "AI commentary unavailable.";
        }
    }

    /**
     * Returns true if the model is a reasoning/flagship model.
     * Reasoning models (gpt-5, o1, o3, o4) have two key differences:
     *   1. They do NOT accept the temperature parameter.
     *   2. They consume hidden "thinking" tokens before generating visible output,
     *      so max_output_tokens must be much higher (2000+) to leave room for the response.
     */
    private boolean isReasoningModel(String model) {
        if (model == null) return false;
        String m = model.toLowerCase();
        return m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4")
            || m.equals("gpt-5") || m.startsWith("gpt-5-");
    }

    /** Returns true if the model accepts the temperature parameter. */
    private boolean supportsTemperature(String model) {
        return !isReasoningModel(model);
    }

    private double safe(Double v) { return v != null ? v : 0.0; }
    private String nullStr(String s) { return s != null ? s : "N/A"; }
}
