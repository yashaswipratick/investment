package com.stock.service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BrowserCookieService {


    private static final String NSE_BASE_URL = "https://www.nseindia.com/";
    private static final int TIMEOUT_MS = 60000;

    @Autowired
    private CookieCache cookieCache;

    /**
     * Fetch fresh NSE cookies using Playwright.
     */
    public Mono<String> fetchNseCookiesUsingBrowser() {

        return Mono.fromCallable(this::performBrowserAutomation)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(cookie -> {

                    if (cookie != null && !cookie.isBlank()) {

                        cookieCache.put(
                                "nse_browser_cookie",
                                cookie
                        );

                        log.info("✅ NSE cookies cached successfully");
                    } else {
                        log.warn("⚠️ Empty cookie returned from browser");
                    }
                })
                .doOnError(error ->
                        log.error(
                                "❌ Failed to fetch NSE cookies",
                                error))
                .onErrorReturn("");
    }

    /**
     * Return cached cookie if available.
     */
    public Mono<String> getNseCookies() {

        return Mono.fromCallable(
                        () -> cookieCache.get("nse_browser_cookie"))
                .flatMap(optionalCookie -> {

                    if (optionalCookie.isPresent()
                            && optionalCookie.get() != null
                            && !optionalCookie.get().isBlank()) {

                        log.info("♻️ Using cached NSE cookies");

                        return Mono.just(optionalCookie.get());
                    }

                    log.info("📡 Cache miss. Fetching fresh NSE cookies...");

                    return fetchNseCookiesUsingBrowser();
                });
    }

    /**
     * Actual Playwright automation.
     */
    private String performBrowserAutomation() {

        log.info("🌐 Starting Playwright browser automation...");

        try (Playwright playwright = Playwright.create()) {

            Browser browser =
                    playwright.chromium().launch(
                            new BrowserType.LaunchOptions()
                                    .setHeadless(false)
                            // change to true later
                    );

            BrowserContext context =
                    browser.newContext(
                            new Browser.NewContextOptions()
                                    .setUserAgent(
                                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                                    "Chrome/137.0.0.0 Safari/537.36")
                                    .setViewportSize(
                                            1920,
                                            1080)
                    );

            context.setDefaultTimeout(TIMEOUT_MS);
            context.setDefaultNavigationTimeout(TIMEOUT_MS);

            Page page = context.newPage();

            try {

                log.info("📍 Navigating to NSE homepage...");

                page.navigate(
                        NSE_BASE_URL,
                        new Page.NavigateOptions()
                                .setWaitUntil(
                                        WaitUntilState.DOMCONTENTLOADED)
                );

                log.info("⏳ Waiting for NSE scripts...");
                page.waitForTimeout(10000);

                log.info(
                        "Current URL = {}",
                        page.url());

                try {
                    log.info(
                            "Page Title = {}",
                            page.title());
                } catch (Exception ignored) {
                }

                List<Cookie> cookies =
                        context.cookies();

                log.info(
                        "🍪 Cookies found = {}",
                        cookies.size());

                cookies.forEach(cookie ->
                        log.info(
                                "COOKIE => {}",
                                cookie.name)
                );

                String cookieString =
                        cookies.stream()
                                .map(c ->
                                        c.name + "=" + c.value)
                                .collect(
                                        Collectors.joining("; "));

                if (cookieString.isBlank()) {

                    log.warn(
                            "⚠️ No cookies found in browser context");

                    try {

                        String html =
                                page.content();

                        log.warn(
                                "Page sample:\n{}",
                                html.substring(
                                        0,
                                        Math.min(
                                                1000,
                                                html.length())));
                    } catch (Exception ignored) {
                    }

                    return "";
                }

                log.info(
                        "✅ Successfully extracted NSE cookies");

                return cookieString;

            } finally {

                try {
                    page.close();
                } catch (Exception ignored) {
                }

                try {
                    context.close();
                } catch (Exception ignored) {
                }

                try {
                    browser.close();
                } catch (Exception ignored) {
                }
            }

        } catch (Exception e) {

            log.error(
                    "❌ Playwright browser automation failed",
                    e);

            return "";
        }
    }


}
