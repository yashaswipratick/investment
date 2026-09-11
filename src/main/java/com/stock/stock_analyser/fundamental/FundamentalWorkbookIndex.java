package com.stock.stock_analyser.fundamental;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/** Startup/refreshed in-memory index of canonical SYMBOL.xlsx fundamental workbooks. */
@Slf4j
@Component
public class FundamentalWorkbookIndex {
    private final FundamentalDataConfiguration configuration;
    private volatile Map<String, Path> index = Map.of();
    private volatile Set<String> conflicts = Set.of();

    public FundamentalWorkbookIndex(FundamentalDataConfiguration configuration) {
        this.configuration = configuration;
    }

    @PostConstruct
    void initialize() { refresh(); }

    public synchronized void refresh() {
        Path directory = configuredDirectory();
        if (directory == null || !Files.isDirectory(directory)) {
            log.warn("Fundamental workbook directory is unavailable; retaining last known-good index: {}", directory);
            return;
        }
        try (var stream = Files.list(directory)) {
            List<Path> files = stream.filter(Files::isRegularFile).collect(Collectors.toList());
            Discovery discovery = buildIndex(files, configuration.getWorkbookExtension());
            index = Collections.unmodifiableMap(discovery.index());
            conflicts = Collections.unmodifiableSet(discovery.conflicts());
            log.info("Discovered {} fundamental workbooks and indexed {} symbols in {}",
                    discovery.workbookCount(), index.size(), directory);
            if (!conflicts.isEmpty()) log.error("Duplicate fundamental workbook symbols detected: {}", conflicts);
            if (!discovery.invalidFiles().isEmpty()) log.warn("Ignored {} invalid/non-canonical fundamental workbook files in {}", discovery.invalidFiles().size(), directory);
        } catch (IOException e) {
            log.warn("Unable to scan fundamental workbook directory; retaining last known-good index: {}: {}", directory, e.getMessage());
        }
    }

    public Optional<Path> find(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return conflicts.contains(normalized) ? Optional.empty() : Optional.ofNullable(index.get(normalized));
    }

    public Map<String, Path> snapshot() { return index; }
    public Set<String> conflicts() { return conflicts; }

    Path configuredDirectory() {
        String value = configuration.getDataDirectory();
        if (value == null || value.isBlank()) return null;
        try { return Path.of(value).toAbsolutePath().normalize(); }
        catch (RuntimeException e) { return null; }
    }

    static Discovery buildIndex(Collection<Path> files, String extension) {
        Map<String, Path> result = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        List<Path> invalid = new ArrayList<>();
        String ext = extension == null || extension.isBlank() ? ".xlsx" : extension.toLowerCase(Locale.ROOT);
        for (Path file : files == null ? List.<Path>of() : files) {
            if (file == null) continue;
            String name = file.getFileName().toString();
            if (!name.toLowerCase(Locale.ROOT).endsWith(ext)) { invalid.add(file); continue; }
            String stem = name.substring(0, name.length() - ext.length());
            if (stem.isBlank() || !stem.matches("[A-Za-z0-9._-]+")) { invalid.add(file); continue; }
            String symbol = stem.toUpperCase(Locale.ROOT);
            Path normalized = file.toAbsolutePath().normalize();
            Path existing = result.putIfAbsent(symbol, normalized);
            if (existing != null && !existing.equals(normalized)) { conflicts.add(symbol); result.remove(symbol); }
        }
        return new Discovery(result, conflicts, invalid, result.size() + conflicts.size());
    }

    record Discovery(Map<String, Path> index, Set<String> conflicts, List<Path> invalidFiles, int workbookCount) {}
}
