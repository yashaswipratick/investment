package com.stock.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for NSE cookies with TTL (Time-To-Live).
 * Thread-safe using ConcurrentHashMap.
 */
@Slf4j
@Component
public class CookieCache {

    private static class CachedCookie {
        String value;
        long expiryTime; // Instant when the cookie expires

        CachedCookie(String value, long ttlMillis) {
            this.value = value;
            this.expiryTime = System.currentTimeMillis() + ttlMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() >= expiryTime;
        }
    }

    private final ConcurrentHashMap<String, CachedCookie> cache = new ConcurrentHashMap<>();

    // Default cache TTL: 55 minutes (NSE cookies typically expire after 60 minutes)
    private static final long DEFAULT_TTL_MILLIS = 55L * 60 * 1000;

    /**
     * Store a cookie with default TTL (55 minutes).
     *
     * @param key   Cache key (e.g., "nse_browser_cookie")
     * @param value Cookie string
     */
    public void put(String key, String value) {
        put(key, value, DEFAULT_TTL_MILLIS);
    }

    /**
     * Store a cookie with custom TTL.
     *
     * @param key      Cache key
     * @param value    Cookie string
     * @param ttlMillis Time-to-live in milliseconds
     */
    public void put(String key, String value, long ttlMillis) {
        if (key == null || value == null || value.isBlank()) {
            log.warn("⚠️ Attempted to cache invalid cookie. Key: {}, Value empty: {}", key, value == null || value.isBlank());
            return;
        }
        cache.put(key, new CachedCookie(value, ttlMillis));
        log.info("✅ Cookie cached with key '{}'. TTL: {} minutes", key, ttlMillis / (60 * 1000));
    }

    /**
     * Retrieve a cookie if present and not expired.
     *
     * @param key Cache key
     * @return Optional containing the cookie value if valid, empty otherwise
     */
    public Optional<String> get(String key) {
        CachedCookie cached = cache.get(key);

        if (cached == null) {
            log.debug("❌ Cache miss for key '{}'", key);
            return Optional.empty();
        }

        if (cached.isExpired()) {
            log.info("⏰ Cache expired for key '{}'. Removing from cache.", key);
            cache.remove(key);
            return Optional.empty();
        }

        long remainingMillis = cached.expiryTime - System.currentTimeMillis();
        long remainingMinutes = remainingMillis / (60 * 1000);
        log.info("✅ Cache hit for key '{}'. Remaining TTL: {} minutes", key, remainingMinutes);

        return Optional.of(cached.value);
    }

    /**
     * Clear a specific cache entry.
     *
     * @param key Cache key
     */
    public void evict(String key) {
        cache.remove(key);
        log.info("🗑️  Cache evicted for key '{}'", key);
    }

    /**
     * Clear all cached cookies.
     */
    public void clear() {
        cache.clear();
        log.info("🗑️  All cookies cleared from cache");
    }

    /**
     * Get cache size (includes expired entries not yet cleaned).
     *
     * @return Number of entries in cache
     */
    public int size() {
        return cache.size();
    }
}

