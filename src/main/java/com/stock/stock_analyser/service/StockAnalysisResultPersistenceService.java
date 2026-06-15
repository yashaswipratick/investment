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

        StockAnalysisResultEntity entity = StockAnalysisResultEntity.builder()
                .key(StockAnalysisResultKey.builder()
                        .symbol(result.getSymbol().toUpperCase().trim())
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
                .createdAt(Instant.now())
                .build();

        return repository.save(entity)
                .doOnNext(saved -> log.info("Persisted analysis result. symbol={}, analysisDate={}",
                        saved.getKey().getSymbol(), saved.getKey().getAnalysisDate()))
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

