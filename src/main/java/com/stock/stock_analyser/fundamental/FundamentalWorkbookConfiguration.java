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
        if (symbol == null) return null;
        return workbooks.get(symbol.trim().toUpperCase(Locale.ROOT));
    }
}
