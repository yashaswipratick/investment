package com.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NseSessionManager {

    private static final String BASE_URL = "https://www.nseindia.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-GB,en-US;q=0.9,en;q=0.8";
    private static final String ACCEPT_ENCODING = "gzip, deflate";
    
    // Cookie file: src/main/resources/cookie.txt
    // "classpath:cookie.txt" tells Spring: "Look in src/main/resources/ (the classpath)"
    private static final String COOKIE_CLASSPATH_RESOURCE = "classpath:cookie.txt";

    @Autowired
    private BrowserCookieService browserCookieService;

    /**
     * Reads cookie from: src/main/resources/cookie.txt
     * 
     * How ClassPathResource resolves the path:
     * - "classpath:cookie.txt" tells Spring to look in the classpath
     * - Classpath includes: src/main/resources/ (development) or JAR content (production)
     * - Spring automatically finds it in either location
     * 
     * Examples:
     * - Development: src/main/resources/cookie.txt ✅
     * - Production JAR: BOOT-INF/classes/cookie.txt inside JAR ✅
     * - Docker: Finds it inside the container's JAR ✅
     * 
     * Returns empty string if the file is missing or empty.
     */
    public String readCookieFromFile() {
        try {
            // ClassPathResource with "classpath:" prefix explicitly tells Spring
            // to look in src/main/resources/ on the classpath
            ClassPathResource resource = new ClassPathResource(COOKIE_CLASSPATH_RESOURCE);
            
            if (!resource.exists()) {
                log.warn("⚠️ Cookie file NOT found at: src/main/resources/cookie.txt");
                return "";
            }
            
            String content = new String(Files.readAllBytes(resource.getFile().toPath())).trim();
            
            if (!content.isEmpty()) {
                log.info("✅ Loaded cookie from src/main/resources/cookie.txt ({} chars)", content.length());
            }
            
            return content;
        } catch (IOException e) {
            log.warn("⚠️ Error reading src/main/resources/cookie.txt: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Generates a fresh NSE session cookie by making a warm-up GET request to NSE homepage.
     * This extracts the Set-Cookie headers and returns them as a single cookie string.
     * No file dependency needed - cookies are generated on-demand from NSE.
     *
     * @return Mono containing the NSE session cookie string (e.g., "AKA_A2=A; bm_sz=...")
     */
    public Mono<String> generateFreshSessionCookie() {
        WebClient client = buildWebClient();

        return client.get()
                .retrieve()
                .toBodilessEntity()
                .map(entity -> {
                    // Extract Set-Cookie headers
                    List<String> setCookieHeaders = entity.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE);

                    if (setCookieHeaders.isEmpty()) {
                        log.warn("NSE warm-up call returned no Set-Cookie headers. Using empty cookie.");
                        return "";
                    }

                    // Merge all Set-Cookie values into a single string
                    // Each Set-Cookie header is like: "AKA_A2=A; Path=/; HttpOnly"
                    // We extract just the cookie name=value part before the first semicolon
                    String mergedCookie = setCookieHeaders.stream()
                            .map(setCookie -> {
                                // Extract just "AKA_A2=A" from "AKA_A2=A; Path=/; HttpOnly"
                                int semiIndex = setCookie.indexOf(';');
                                return semiIndex > 0
                                        ? setCookie.substring(0, semiIndex).trim()
                                        : setCookie.trim();
                            })
                            .collect(Collectors.joining("; "));

                    log.info("✅ Generated fresh NSE session cookie from warm-up call ({})", mergedCookie.length());
                    return mergedCookie;
                })
                .doOnError(e -> log.warn("⚠️ Failed to generate fresh NSE session cookie: {}. Using empty cookie.", e.getMessage()))
                .onErrorResume(e -> Mono.just("")); // Return empty cookie if warm-up fails
    }

    /**
     * Generates a fresh NSE session cookie and appends the contents of cookie.txt.
     * Dynamic cookie is placed first; file cookie is appended after a semicolon.
     */
    public Mono<String> generateCookieWithFileAppended() {
        return generateFreshSessionCookie().map(dynamicCookie -> {
            String fileCookie = readCookieFromFile();
            if (fileCookie.isEmpty()) {
                return dynamicCookie;
            }
            if (dynamicCookie.isEmpty()) {
                return fileCookie;
            }
            return dynamicCookie + "; " + fileCookie;
        });
    }

    /**
     * Get NSE cookies using Playwright browser automation with cache support.
     * Falls back to file-based cookie and fresh warm-up call if needed.
     *
     * Priority order:
     * 1. Cached Playwright browser cookies (fast, fresh)
     * 2. Append static file cookie (if available)
     * 3. Fall back to fresh warm-up call (last resort)
     *
     * @return Mono containing merged cookie string
     */
    public Mono<String> generateCookieUsingBrowserAutomation() {
        return browserCookieService.getNseCookies()
                .flatMap(browserCookie -> {
                    log.info("🔄 Using Playwright browser-fetched cookies as primary source");
                    String fileCookie = readCookieFromFile();

                    if (fileCookie.isEmpty()) {
                        log.info("✅ Browser-fetched cookie (no file cookie to append)");
                        return Mono.just(browserCookie);
                    }

                    if (browserCookie.isEmpty()) {
                        log.warn("⚠️ Browser cookie empty, using file cookie only");
                        return Mono.just(fileCookie);
                    }

                    String merged = browserCookie + "; " + fileCookie;
                    log.info("✅ Merged browser cookie + file cookie");
                    return Mono.just(merged);
                })
                .doOnError(e -> log.warn("⚠️ Browser cookie fetch failed ({}), falling back to fresh warm-up", e.getMessage()))
                .onErrorResume(e -> {
                    log.info("🔄 Falling back to fresh NSE warm-up call...");
                    return generateCookieWithFileAppended();
                });
    }

    /**
     * Builds a WebClient for NSE warm-up calls (direct connection, no proxy).
     */
    private WebClient buildWebClient() {
        HttpClient httpClient = HttpClient.create().followRedirect(true);


        return WebClient.builder()
                .baseUrl(BASE_URL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept-Language", ACCEPT_LANGUAGE)
                .defaultHeader("Accept-Encoding", ACCEPT_ENCODING)
                .build();
    }

}
