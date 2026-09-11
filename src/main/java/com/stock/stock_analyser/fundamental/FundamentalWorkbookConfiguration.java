package com.stock.stock_analyser.fundamental;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Centralized, externally configurable mapping from NSE symbol to fundamental workbook filename. */
@Component
@ConfigurationProperties(prefix = "fundamental")
public class FundamentalWorkbookConfiguration {
    private String dataDirectory;
    private Map<String, String> workbooks = new LinkedHashMap<>();

    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
    public Map<String, String> getWorkbooks() { return workbooks; }
    public void setWorkbooks(Map<String, String> workbooks) { this.workbooks = workbooks == null ? new LinkedHashMap<>() : new LinkedHashMap<>(workbooks); }

    public String getWorkbookForSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        String configured = workbooks.get(normalized);
        if (configured != null && !configured.isBlank()) return configured;
        // New stocks follow the canonical convention SYMBOL.xlsx and therefore do not
        // require an application.yml change. Explicit mappings remain available for
        // legacy/human-friendly filenames already present in the repository.
        return normalized + ".xlsx";
    }

    public boolean hasExplicitWorkbookMapping(String symbol) {
        if (symbol == null) return false;
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return workbooks.containsKey(normalized) && workbooks.get(normalized) != null && !workbooks.get(normalized).isBlank();
    }
}
