package com.stock.stock_analyser.fundamental;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Resolves an NSE symbol to a discovered workbook while enforcing directory containment. */
@Component
public class FundamentalWorkbookResolver {
    private final FundamentalWorkbookIndex index;

    public FundamentalWorkbookResolver(FundamentalWorkbookIndex index) {
        this.index = index;
    }

    public Optional<Path> resolve(String symbol) {
        if (symbol == null || symbol.isBlank()) return Optional.empty();
        Path base = index.configuredDirectory();
        if (base == null) return Optional.empty();
        return index.find(symbol.trim().toUpperCase(Locale.ROOT))
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .filter(path -> path.startsWith(base));
    }

    public void refresh() { index.refresh(); }
}
