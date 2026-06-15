package com.stock.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import io.netty.handler.timeout.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;


/**
 * Service to fetch NSE cookies using Playwright browser automation.
 * Provides a non-blocking, reactive interface for cookie retrieval.
 */
@Slf4j
@Service
public class BrowserCookieService {

    private static final String NSE_BASE_URL = "https://www.nseindia.com/";
    private static final int TIMEOUT_MS = 30000; // 30 seconds

    @Autowired
    private CookieCache cookieCache;

    /**
     * Fetch NSE cookies using Playwright browser automation.
     * Runs on a blocking scheduler to avoid blocking the WebFlux event loop.
     *
     * @return Mono containing the merged cookie string
     */
    public Mono<String> fetchNseCookiesUsingBrowser() {
        return Mono.fromCallable(this::performBrowserAutomation)
                .subscribeOn(Schedulers.boundedElastic()) // Use bounded elastic scheduler for blocking I/O
                .doOnSuccess(cookie -> cookieCache.put("nse_browser_cookie", cookie))
                .doOnError(e -> log.error("❌ Failed to fetch cookies via Playwright: {}", e.getMessage()))
                .onErrorReturn(""); // Return empty string on error
    }

    /**
     * Internal method to perform actual browser automation.
     * This is a blocking operation and should only be called from a blocking scheduler.
     *
     * @return Merged cookie string (e.g., "AKA_A2=A; bm_sz=...")
     */
    private String performBrowserAutomation() {
        log.info("🌐 Starting Playwright browser automation for NSE cookie fetch...");

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            BrowserContext context = browser.newContext();
            context.setDefaultTimeout(TIMEOUT_MS);
            context.setDefaultNavigationTimeout(TIMEOUT_MS);

            Page page = context.newPage();

            // Navigate to NSE homepage
            log.info("📍 Navigating to NSE homepage...");
            page.navigate(NSE_BASE_URL);

            // Wait for page to fully load (wait for any JavaScript to execute)
            page.waitForLoadState(LoadState.NETWORKIDLE);

            // Extract cookies from the page request header
            // Use JavaScript to get document.cookie which contains all accessible cookies
            try {
                Object cookieObj = page.evaluate("() => document.cookie");
                String cookieString = cookieObj != null ? cookieObj.toString() : "";

                if (cookieString.isEmpty()) {
                    log.warn("⚠️ No cookies found via document.cookie");
                    return "";
                }

                log.info("✅ Fetched cookies via Playwright from NSE");
                log.debug("🍪 Cookies: {}", cookieString);

                return cookieString;
            } catch (Exception e) {
                // Fallback: Try to extract from context (may not work with HttpOnly cookies)
                log.warn("⏳ Failed to get cookies via JavaScript, cookies may be HttpOnly");
                return "";
            } finally {
                // Clean up
                try { page.close(); } catch (Exception e) { log.warn("⚠️ Error closing page"); }
                try { context.close(); } catch (Exception e) { log.warn("⚠️ Error closing context"); }
                try { browser.close(); } catch (Exception e) { log.warn("⚠️ Error closing browser"); }
            }

        } catch (PlaywrightException | TimeoutException e) {
            log.error("❌ Playwright browser error: {}", e.getMessage(), e);
            return "";
        } catch (Exception e) {
            log.error("❌ Unexpected error during browser automation: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Get cached NSE browser cookies if available and not expired.
     * Falls back to fetching new cookies if cache miss.
     *
     * @return Mono containing the cookie string
     */
    public Mono<String> getNseCookies() {
        // Try to get from cache first
        return Mono.fromCallable(() -> cookieCache.get("nse_browser_cookie"))
                .flatMap(optionalCookie -> {
                    if (optionalCookie.isPresent()) {
                        log.info("♻️ Using cached NSE browser cookies");
                        return Mono.just(optionalCookie.get());
                    }

                    // Cache miss, fetch new cookies
                    log.info("📡 Cache miss. Fetching fresh NSE cookies via Playwright...");
                    return fetchNseCookiesUsingBrowser();
                });
    }
}

