package com.stock.stock_analyser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.repository.StockAnalysisResultRepository;
import com.stock.stock_analyser.dto.StockAnalysisResult;
import com.stock.stock_analyser.dto.StockAnalysisResultEntity;
import com.stock.stock_analyser.dto.key.StockAnalysisResultKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAnalysisResultPersistenceService {

    private final StockAnalysisResultRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Mono<StockAnalysisResult> persist(StockAnalysisResult result) {
        if (result == null || result.getSymbol() == null || result.getSymbol().isBlank()) {
            return Mono.justOrEmpty(result);
        }

        LocalDate analysisDate = result.getAnalysisDate() != null ? result.getAnalysisDate() : LocalDate.now();

        String periodLabel = result.getPeriodLabel() != null ? result.getPeriodLabel() : "1Y";

        StockAnalysisResultEntity entity = StockAnalysisResultEntity.builder()
                .key(StockAnalysisResultKey.builder()
                        .symbol(result.getSymbol().toUpperCase().trim())
                        .periodLabel(periodLabel)
                        .analysisDate(analysisDate)
                        .build())
                .totalDataPoints(result.getTotalDataPoints())
                .dataFrom(result.getDataFrom())
                .dataTo(result.getDataTo())
                .requiredFrom(result.getRequiredFrom())
                .windowStatus(result.getWindowStatus())
                .windowMessage(result.getWindowMessage())
                .dataNote(result.getDataNote())
                .technicalJson(toJson(result.getTechnical()))
                .recommendationJson(toJson(result.getRecommendation()))
                .projectionsJson(toJson(result.getProjections()))
                .entryTimingJson(toJson(result.getEntryTiming()))
                .stopLossStrategyJson(toJson(result.getStopLossStrategy()))
                .createdAt(Instant.now())
                .build();

        // Log the full result before persisting so it's visible in logs even if Cassandra fails
        log.info("[PERSIST][{} | {}] About to persist analysis result:\n" +
                 "  Window    : {} | {}\n" +
                 "  Data Range: {} → {} ({} candles)\n" +
                 "  Action    : {} | Confidence: {}%\n" +
                 "  Entry     : ₹{} – ₹{} | Target: ₹{} | SL: ₹{}\n" +
                 "  R/R       : {} | Upside: {}% | Downside: {}%\n" +
                 "  AI Commentary ({} chars): {}",
                result.getSymbol(), periodLabel,
                result.getWindowStatus(), result.getWindowMessage() != null ? result.getWindowMessage().substring(0, Math.min(80, result.getWindowMessage().length())) + "..." : "N/A",
                result.getDataFrom(), result.getDataTo(), result.getTotalDataPoints(),
                result.getRecommendation() != null ? result.getRecommendation().getAction()          : "N/A",
                result.getRecommendation() != null ? result.getRecommendation().getConfidenceScore() : 0,
                result.getRecommendation() != null ? result.getRecommendation().getEntryPriceLow()   : 0,
                result.getRecommendation() != null ? result.getRecommendation().getEntryPriceHigh()  : 0,
                result.getRecommendation() != null ? result.getRecommendation().getTargetPrice()     : 0,
                result.getRecommendation() != null ? result.getRecommendation().getStopLossPrice()   : 0,
                result.getRecommendation() != null ? result.getRecommendation().getRiskRewardRatio() : 0,
                result.getRecommendation() != null ? result.getRecommendation().getPotentialUpsidePct()   : 0,
                result.getRecommendation() != null ? result.getRecommendation().getPotentialDownsidePct() : 0,
                result.getRecommendation() != null && result.getRecommendation().getAiCommentary() != null
                        ? result.getRecommendation().getAiCommentary().length() : 0,
                result.getRecommendation() != null ? result.getRecommendation().getAiCommentary() : "none"
        );

        return repository.save(entity)
                .doOnNext(saved -> log.info("[PERSIST][{} | {}] Successfully saved to Cassandra. analysisDate={}",
                        saved.getKey().getSymbol(), saved.getKey().getPeriodLabel(), saved.getKey().getAnalysisDate()))
                .thenReturn(result)
                .onErrorResume(e -> {
                    log.error("Failed to persist analysis result for symbol={}: {}", result.getSymbol(), e.getMessage());
                    return Mono.just(result);
                });
    }

    private String toJson(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize analysis field to JSON: {}", e.getMessage());
            return "";
        }
    }
}

