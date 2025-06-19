package com.stock.curl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class ReadCookie {

    private static final String COOKIE_FILE_PATH =
            "/Users/yashaswipratick/projects/nse-cookie-tracker/nse-cookie.txt";

    public String readCookie() {
        try {
            String cookie = Files.readString(Path.of(COOKIE_FILE_PATH));

            System.out.println("🍪 Cookie Read from File:");
            System.out.println(cookie);

            return cookie;
        } catch (IOException e) {
            System.err.println("❌ Failed to read cookie file: " + e.getMessage());
            return null;
        }
    }
}
