package com.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Scheduled service to periodically refresh NSE cookies via Playwright.
 * Ensures cookies are always fresh and available in cache.
 */
@Slf4j
@Service
@EnableScheduling
public class CookieRefreshScheduler {

    @Autowired
    private BrowserCookieService browserCookieService;

    /**
     * Refresh NSE cookies every 50 minutes.
     * Runs as a scheduled background task.
     *
     * Initial delay: 10 seconds (give app time to start up)
     * Fixed rate: 50 minutes (cookies expire in ~60 minutes)
     */
    @Scheduled(initialDelay = 10000, fixedRate = 50 * 60 * 1000)
    public void refreshNseCookiesPeriodically() {
        log.info("⏲️  Scheduled cookie refresh triggered. Fetching fresh NSE cookies via Playwright...");

        browserCookieService.fetchNseCookiesUsingBrowser()
                .subscribe(
                        cookie -> {
                            if (cookie != null && !cookie.isBlank()) {
                                log.info("✅ Scheduled cookie refresh completed successfully. Cookies cached.");
                            } else {
                                log.warn("⚠️ Scheduled cookie refresh returned empty cookie");
                            }
                        },
                        error -> log.error("❌ Scheduled cookie refresh failed: {}", error.getMessage())
                );
    }

    /**
     * Optional: Refresh on application startup for immediate availability.
     * This ensures cookies are fresh from the moment the app starts.
     */
    @Scheduled(initialDelay = 3000, fixedRate = Long.MAX_VALUE) // Run once after 3 seconds
    public void refreshNseCookiesOnStartup() {
        log.info("🚀 Application startup: Fetching initial NSE cookies via Playwright...");

        browserCookieService.fetchNseCookiesUsingBrowser()
                .subscribe(
                        cookie -> {
                            if (cookie != null && !cookie.isBlank()) {
                                log.info("✅ Startup cookie fetch completed. Fresh cookies ready.");
                            } else {
                                log.warn("⚠️ Startup cookie fetch returned empty cookie");
                            }
                        },
                        error -> log.error("❌ Startup cookie fetch failed: {}", error.getMessage())
                );
    }
}

