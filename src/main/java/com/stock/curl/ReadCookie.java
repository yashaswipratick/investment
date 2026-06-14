package com.stock.curl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
public class ReadCookie {

    private final String cookieFilePath;

    /**
     * Cookie file path is configurable via application.yml: nse.cookie.file-path
     * Defaults to ~/nse-cookie.txt if not set.
     */
    public ReadCookie(
            @Value("${nse.cookie.file-path:#{systemProperties['user.home']}/nse-cookie.txt}")
            String cookieFilePath) {
        this.cookieFilePath = cookieFilePath;
    }

    public String readCookie() {
        try {
            String cookie = Files.readString(Path.of(cookieFilePath)).trim();
            log.info("🍪 Cookie read successfully from: {}", cookieFilePath);
            return cookie;
        } catch (IOException e) {
            log.warn("❌ Failed to read cookie file [{}]: {}. Proceeding without cookie.", cookieFilePath, e.getMessage());
            return "";
        }
    }
}
