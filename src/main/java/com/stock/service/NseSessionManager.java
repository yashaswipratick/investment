package com.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.nio.file.Files;

@Slf4j
@Service
public class NseSessionManager {


    private static final String BASE_URL = "https://www.nseindia.com/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/137.0.0.0 Safari/537.36";

    private static final String ACCEPT_LANGUAGE =
            "en-GB,en-US;q=0.9,en;q=0.8";

    private static final String ACCEPT_ENCODING =
            "gzip, deflate";

    /**
     * File should exist at:
     * src/main/resources/cookie.txt
     */
    private static final String COOKIE_CLASSPATH_RESOURCE = "cookie.txt";

    @Autowired
    private BrowserCookieService browserCookieService;

    /**
     * Read optional cookie file.
     */
    public String readCookieFromFile() {

        try {

            ClassPathResource resource =
                    new ClassPathResource(COOKIE_CLASSPATH_RESOURCE);

            if (!resource.exists()) {
                log.info("cookie.txt not found. Continuing with browser cookies only.");
                return "";
            }

            String content =
                    new String(Files.readAllBytes(resource.getFile().toPath()))
                            .trim();

            if (!content.isBlank()) {
                log.info("Loaded cookie.txt ({} chars)", content.length());
            }

            return content;

        } catch (IOException e) {

            log.warn("Unable to read cookie.txt: {}", e.getMessage());

            return "";
        }
    }

    /**
     * Main cookie provider.
     *
     * Uses Playwright generated cookies.
     * Optionally appends cookie.txt contents.
     */
    public Mono<String> generateCookieUsingBrowserAutomation() {

        return browserCookieService.getNseCookies()
                .map(browserCookie -> {

                    if (browserCookie == null || browserCookie.isBlank()) {

                        log.warn("⚠️ Playwright returned empty cookie");

                        return "";
                    }

                    log.info("✅ Using Playwright generated NSE cookies");

                    String fileCookie = readCookieFromFile();

                    if (fileCookie == null || fileCookie.isBlank()) {

                        log.info("✅ Using browser cookies only");

                        return browserCookie;
                    }

                    String mergedCookie =
                            browserCookie + "; " + fileCookie;

                    log.info("✅ Browser cookie + cookie.txt merged");

                    return mergedCookie;
                })
                .doOnError(error ->
                        log.error("❌ Failed to obtain browser cookies",
                                error))
                .onErrorReturn("");
    }

    /**
     * Retained only if some legacy code still calls it.
     * Delegates directly to browser automation.
     */
    public Mono<String> generateCookieWithFileAppended() {
        return generateCookieUsingBrowserAutomation();
    }

    /**
     * Retained only if some old service still invokes it.
     * Delegates directly to browser automation.
     */
    public Mono<String> generateFreshSessionCookie() {
        return generateCookieUsingBrowserAutomation();
    }

    /**
     * Standard NSE WebClient builder.
     */
    public WebClient buildWebClient() {

        HttpClient httpClient = HttpClient.create()
                .followRedirect(true);

        return WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(
                        new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept-Language", ACCEPT_LANGUAGE)
                .defaultHeader("Accept-Encoding", ACCEPT_ENCODING)
                .build();
    }


}
