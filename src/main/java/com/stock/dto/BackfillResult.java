package com.stock.dto;
import lombok.*;
import java.time.LocalDate;
import java.util.List;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder(toBuilder = true)
@ToString
public class BackfillResult {
    private String stockSymbol;
    private LocalDate oldestDateInDb;
    private LocalDate newestDateInDb;
    private int totalRecordsInDb;
    private int newRecordsMerged;
    private List<ChunkFetchStatus> chunkStatuses;
    private double dataReliabilityScore;
    private String reliabilityTier;
    private List<String> reliabilityWarnings;
    private String overallStatus;
}
