package com.stock.service;

import com.stock.dto.*;
import com.stock.dto.key.StockHistoryKey;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Fetches multi-year stock history from NSE in safe calendar-month chunks and
 * appends only the MISSING portion to Cassandra.
 *
 * ═══ Algorithm ═══════════════════════════════════════════════════════════════
 * 1. Load the stock's existing record from DB.
 * 2. Find the oldest date already stored  (= coverage boundary).
 * 3. Build 6-month calendar chunks from (today - yearsBack) up to
 *    (oldest-in-db - 1 day).  If the DB is empty, chunks span the full window.
 * 4. Fetch each chunk from NSE  *** sequentially ***  with a configurable
 *    inter-call delay to avoid rate-limiting.
 * 5. After all chunks, validate fill-rate and detect large date gaps.
 * 6. Merge only NEW dates into the existing DB record and save once.
 * ═════════════════════════════════════════════════════════════════════════════
 */
@Slf4j
@Service
public class HistoricalDataBackfillService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    /** Fill-rate below this value (%) causes a chunk to be flagged PARTIAL. */
    private static final double LOW_FILL_RATE_THRESHOLD = 70.0;

    /** Consecutive missing weekdays that raises a GAP warning. */
    private static final int GAP_WARNING_THRESHOLD = 7;

    @Autowired
    private StockHistoryDataService stockHistoryDataService;

    @Autowired
    private StockHistoryDataIntegrator stockHistoryDataIntegrator;

    @Autowired
    private SectorWiseStockDataIntegrator sectorWiseStockDataIntegrator;

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Backfill history for a single stock symbol.
     */
    public Mono<BackfillResult> backfillStock(BackfillRequest request) {
        String symbol      = request.getStockSymbol();
        String series      = blankDefault(request.getSeries(), "EQ");
        int    yearsBack   = positiveOrDefault(request.getYearsBack(),   3);
        int    chunkMonths = positiveOrDefault(request.getChunkMonths(), 6);
        long   chunkDelay  = positiveOrDefault(request.getDelaySecondsBetweenChunks(), 3);

        LocalDate targetStart = LocalDate.now().minusYears(yearsBack);
        LocalDate today       = LocalDate.now();

        log.info("[Backfill] START symbol={} series={} yearsBack={} chunkMonths={} targetStart={}",
                symbol, series, yearsBack, chunkMonths, targetStart);

        return stockHistoryDataService.get(StockHistoryKey.builder().key(symbol).build())
                .defaultIfEmpty(emptyHistory(symbol))
                .flatMap(existing -> {
                    TreeMap<LocalDate, StockHistoryDetails> existingMap = safeMap(existing);

                    LocalDate oldestInDb = existingMap.isEmpty() ? null : existingMap.firstKey();
                    LocalDate fetchUpTo  = (oldestInDb != null) ? oldestInDb.minusDays(1) : today;

                    // Already fully covered?
                    if (oldestInDb != null && !oldestInDb.isAfter(targetStart)) {
                        log.info("[Backfill] {} already has data back to {} (target {}). Nothing to fetch.",
                                symbol, oldestInDb, targetStart);
                        return buildResult(symbol, existingMap, Collections.emptyList(), 0, targetStart, today);
                    }

                    List<Pair<LocalDate, LocalDate>> chunks = buildChunks(targetStart, fetchUpTo, chunkMonths);
                    log.info("[Backfill] {} → {} chunk(s) from {} to {}", symbol, chunks.size(), targetStart, fetchUpTo);

                    // Mutable accumulators (safe inside a single reactive chain)
                    List<ChunkFetchStatus> chunkStatuses  = new ArrayList<>();
                    TreeMap<LocalDate, StockHistoryDetails> accumulated = new TreeMap<>();

                    return Flux.fromIterable(chunks)
                            // concatMap = strictly sequential (one chunk at a time)
                            .concatMap(chunk -> fetchChunk(symbol, series, chunk, chunkStatuses, accumulated)
                                    .delayElement(Duration.ofSeconds(chunkDelay)))
                            .collectList()
                            .flatMap(ignored -> mergeAndSave(symbol, existingMap, accumulated, chunkStatuses, targetStart, today));
                });
    }

    /**
     * Backfill every stock in a sector, one stock at a time.
     */
    public Flux<BackfillResult> backfillSector(String sector, BackfillRequest request) {
        long stockDelay = positiveOrDefault(request.getDelaySecondsBetweenStocks(), 5);

        return sectorWiseStockDataIntegrator.get(sector)
                .flatMapMany(sectorData -> Flux.fromIterable(sectorData.getStocks()))
                .concatMap(symbol -> {
                    BackfillRequest stockReq = request.toBuilder().stockSymbol(symbol).build();
                    log.info("[Backfill] sector={} → processing stock={}", sector, symbol);
                    return backfillStock(stockReq).delayElement(Duration.ofSeconds(stockDelay));
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chunk building  (static – easy to unit-test)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Splits the closed interval [from, to] into consecutive calendar-month
     * windows of {@code chunkMonths} length.
     * Returns an empty list when {@code from} is after {@code to}.
     */
    public static List<Pair<LocalDate, LocalDate>> buildChunks(
            LocalDate from, LocalDate to, int chunkMonths) {

        List<Pair<LocalDate, LocalDate>> chunks = new ArrayList<>();
        if (from.isAfter(to)) return chunks;

        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate end = cursor.plusMonths(chunkMonths).minusDays(1);
            if (end.isAfter(to)) end = to;
            chunks.add(Pair.of(cursor, end));
            cursor = end.plusDays(1);
        }
        return chunks;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chunk fetch helper
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<ChunkFetchStatus> fetchChunk(
            String symbol,
            String series,
            Pair<LocalDate, LocalDate> chunk,
            List<ChunkFetchStatus> chunkStatuses,
            TreeMap<LocalDate, StockHistoryDetails> accumulated) {

        String from = chunk.getLeft().format(FMT);
        String to   = chunk.getRight().format(FMT);

        log.info("[Backfill] {} fetching chunk {} → {}", symbol, from, to);

        StockHistoryRequest req = StockHistoryRequest.builder()
                .stockSymbol(symbol)
                .series(series)
                .from(from)
                .to(to)
                .build();

        return stockHistoryDataIntegrator.fetchAndSaveFromNextApi(req)
                .map(fetched -> {
                    int count    = fetched.getStockHistoryDetails() != null
                                   ? fetched.getStockHistoryDetails().size() : 0;
                    int expected = countWeekdays(chunk.getLeft(), chunk.getRight());
                    double fill  = expected > 0 ? (count * 100.0 / expected) : 0;

                    if (fetched.getStockHistoryDetails() != null) {
                        accumulated.putAll(fetched.getStockHistoryDetails());
                    }

                    String status = count == 0         ? "NSE_ERROR"
                                  : fill < LOW_FILL_RATE_THRESHOLD ? "PARTIAL"
                                  : "SUCCESS";

                    log.info("[Backfill] {} chunk {}-{}: count={} expected={} fillRate={}% status={}",
                            symbol, from, to, count, expected, round1(fill), status);

                    ChunkFetchStatus cs = ChunkFetchStatus.builder()
                            .fromDate(from).toDate(to)
                            .recordsFetched(count)
                            .expectedTradingDays(expected)
                            .fillRatePct(round1(fill))
                            .status(status)
                            .build();
                    chunkStatuses.add(cs);
                    return cs;
                })
                .onErrorResume(err -> {
                    log.error("[Backfill] {} chunk {}-{} error: {}", symbol, from, to, err.getMessage());
                    ChunkFetchStatus cs = ChunkFetchStatus.builder()
                            .fromDate(from).toDate(to)
                            .recordsFetched(0)
                            .expectedTradingDays(countWeekdays(chunk.getLeft(), chunk.getRight()))
                            .fillRatePct(0)
                            .status("NSE_ERROR")
                            .message(err.getMessage())
                            .build();
                    chunkStatuses.add(cs);
                    return Mono.just(cs);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Merge + Save
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<BackfillResult> mergeAndSave(
            String symbol,
            TreeMap<LocalDate, StockHistoryDetails> existingMap,
            TreeMap<LocalDate, StockHistoryDetails> accumulated,
            List<ChunkFetchStatus> chunkStatuses,
            LocalDate windowStart,
            LocalDate windowEnd) {

        // Only insert dates that are NOT already in the DB (existing wins)
        int newCount = 0;
        for (Map.Entry<LocalDate, StockHistoryDetails> e : accumulated.entrySet()) {
            if (!existingMap.containsKey(e.getKey())) {
                existingMap.put(e.getKey(), e.getValue());
                newCount++;
            }
        }
        log.info("[Backfill] {} merging {} new record(s) into DB (total after merge: {})",
                symbol, newCount, existingMap.size());

        StockHistory merged = StockHistory.builder()
                .key(StockHistoryKey.builder().key(symbol).build())
                .stockHistoryDetails(existingMap)
                .build();

        final int finalNewCount = newCount;
        return stockHistoryDataService.save(merged)
                .flatMap(saved -> buildResult(symbol, existingMap, chunkStatuses, finalNewCount, windowStart, windowEnd))
                .onErrorResume(err -> {
                    log.error("[Backfill] {} DB save error: {}", symbol, err.getMessage());
                    return buildResult(symbol, existingMap, chunkStatuses, finalNewCount, windowStart, windowEnd);
                });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Reliability analysis + result assembly
    // ─────────────────────────────────────────────────────────────────────────

    private Mono<BackfillResult> buildResult(
            String symbol,
            TreeMap<LocalDate, StockHistoryDetails> dbMap,
            List<ChunkFetchStatus> chunkStatuses,
            int newRecordsMerged,
            LocalDate windowStart,
            LocalDate windowEnd) {

        List<String> warnings = new ArrayList<>();

        // ── Overall fill-rate for the requested window ──────────────────────
        int totalExpected = countWeekdays(windowStart, windowEnd);
        long totalActual = dbMap.entrySet().stream()
                .filter(e -> !e.getKey().isBefore(windowStart) && !e.getKey().isAfter(windowEnd))
                .count();
        double fillRate = totalExpected > 0 ? (totalActual * 100.0 / totalExpected) : 0;

        if (fillRate < LOW_FILL_RATE_THRESHOLD) {
            warnings.add(String.format(
                    "Overall fill-rate %.1f%% is below %.0f%% (window %s → %s, expected ~%d trading days, got %d)",
                    fillRate, LOW_FILL_RATE_THRESHOLD, windowStart, windowEnd, totalExpected, totalActual));
        }

        // ── Gap detection ────────────────────────────────────────────────────
        if (dbMap.size() > 1) {
            List<LocalDate> sorted = new ArrayList<>(dbMap.keySet());
            Collections.sort(sorted);
            for (int i = 1; i < sorted.size(); i++) {
                int gap = countWeekdays(sorted.get(i - 1).plusDays(1), sorted.get(i).minusDays(1));
                if (gap > GAP_WARNING_THRESHOLD) {
                    warnings.add(String.format(
                            "Large gap: %d weekdays missing between %s and %s",
                            gap, sorted.get(i - 1), sorted.get(i)));
                }
            }
        }

        // ── Per-chunk warnings ───────────────────────────────────────────────
        chunkStatuses.stream()
                .filter(cs -> "PARTIAL".equals(cs.getStatus()) || "NSE_ERROR".equals(cs.getStatus()))
                .forEach(cs -> warnings.add(String.format(
                        "Chunk %s → %s  status=%s  fillRate=%.1f%%",
                        cs.getFromDate(), cs.getToDate(), cs.getStatus(), cs.getFillRatePct())));

        // ── Reliability score ────────────────────────────────────────────────
        double score = Math.min(100, fillRate);
        score = Math.max(0, score - warnings.size() * 5.0);

        String tier   = score >= 85 ? "RELIABLE"
                      : score >= 60 ? "PARTIALLY_RELIABLE"
                      : "UNRELIABLE";
        String status = warnings.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_WARNINGS";

        BackfillResult result = BackfillResult.builder()
                .stockSymbol(symbol)
                .oldestDateInDb(dbMap.isEmpty() ? null : dbMap.firstKey())
                .newestDateInDb(dbMap.isEmpty() ? null : dbMap.lastKey())
                .totalRecordsInDb(dbMap.size())
                .newRecordsMerged(newRecordsMerged)
                .chunkStatuses(chunkStatuses)
                .dataReliabilityScore(round1(score))
                .reliabilityTier(tier)
                .reliabilityWarnings(warnings)
                .overallStatus(status)
                .build();

        log.info("[Backfill] {} DONE  total={} new={}  reliability={} score={}  warnings={}",
                symbol, dbMap.size(), newRecordsMerged, tier, round1(score), warnings.size());

        return Mono.just(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utility helpers  (package-private for unit tests)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Counts weekday (Mon–Fri) days in the closed interval [from, to].
     * Returns 0 when from > to.
     */
    static int countWeekdays(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) return 0;
        int count = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                count++;
            }
        }
        return count;
    }

    private static StockHistory emptyHistory(String symbol) {
        return StockHistory.builder()
                .key(StockHistoryKey.builder().key(symbol).build())
                .stockHistoryDetails(new TreeMap<>())
                .build();
    }

    private static TreeMap<LocalDate, StockHistoryDetails> safeMap(StockHistory h) {
        return (h.getStockHistoryDetails() != null)
                ? h.getStockHistoryDetails()
                : new TreeMap<>();
    }

    private static String blankDefault(String v, String def) {
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int positiveOrDefault(int v, int def) {
        return v > 0 ? v : def;
    }

    private static long positiveOrDefault(long v, long def) {
        return v > 0 ? v : def;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}

