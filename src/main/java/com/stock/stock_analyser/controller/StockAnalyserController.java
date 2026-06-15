package com.stock.stock_analyser.controller;

import com.stock.stock_analyser.dto.StockAnalysisRequest;
import com.stock.stock_analyser.dto.StockAnalysisResult;
import com.stock.stock_analyser.service.OpenAiCommentaryService;
import com.stock.stock_analyser.service.StockAnalyserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoint for stock analysis.
 *
 * POST /stock/investment/v1.0/stockAnalyser/analyse
 * {
 *   "symbol": "INFY",
 *   "lookbackDays": 250,
 *   "includeAiCommentary": true
 * }
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/stock/investment/v1.0/stockAnalyser")
public class StockAnalyserController {

    private final StockAnalyserService analyserService;
    private final OpenAiCommentaryService openAiCommentaryService;

    /**
     * Runs full technical analysis on historical data stored in Cassandra.
     */
    @PostMapping(value = "/analyse", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<StockAnalysisResult>> analyse(
            @RequestBody StockAnalysisRequest request) {

        return analyserService.analyse(request)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity
                        .badRequest()
                        .body(StockAnalysisResult.builder()
                                .symbol(request.getSymbol())
                                .dataNote("Error: " + e.getMessage())
                                .build())));
    }

    /**
     * Returns current OpenAI API key validation status.
     *
     * GET /stock/investment/v1.0/stockAnalyser/openai/key-status
     *
     * Example response:
     * {
     *   "keyValid": true,
     *   "validationMessage": "VALID",
     *   "aiCommentaryEnabled": true,
     *   "checkedAt": "2026-06-15T14:30:00Z"
     * }
     *
     * Possible validationMessage values:
     *   VALID                    – key authenticated successfully against OpenAI
     *   INVALID_HTTP_401         – key rejected (unauthorized)
     *   INVALID_HTTP_429         – key valid but rate-limited
     *   VALIDATION_ERROR         – network/runtime error during validation
     *   MISSING_OR_EMPTY_KEY_FILE – file path missing, file not found, or file is empty
     *   NOT_VALIDATED            – service has not started yet
     */
    @GetMapping(value = "/openai/key-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> openAiKeyStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("keyValid", openAiCommentaryService.isApiKeyValid());
        status.put("validationMessage", openAiCommentaryService.getApiKeyValidationMessage());
        status.put("aiCommentaryEnabled", openAiCommentaryService.isApiKeyValid());
        status.put("checkedAt", Instant.now().toString());
        return Mono.just(ResponseEntity.ok(status));
    }
}
