package com.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NseSessionManager {

    private static final String BASE_URL = "https://www.nseindia.com/";
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-GB,en-US;q=0.9,en;q=0.8";
    private static final String ACCEPT_ENCODING = "gzip, deflate";


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

