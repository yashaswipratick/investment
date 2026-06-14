package com.stock.curl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Service
public class CurlCommandGenerator {

    private final ReadCookie cookie;

    String cookieBackup = "";

    public CurlCommandGenerator(ReadCookie cookie) {
        this.cookie = cookie;
    }

    public String generateCurlCommand(String url) {
        String curlCommand = buildCurlCommand(url);
        String jsonResponse = executeCurlCommand(curlCommand);
        return extractJsonFromResponse(jsonResponse);
    }

    private String executeCurlCommand(String curlCommand) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", curlCommand);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("❌ Curl failed with exit code: {}", exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("❌ Curl command interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ Error executing curl command: {}", e.getMessage());
        }
        return output.toString();
    }

    private String extractJsonFromResponse(String response) {
        int startIndex = response.indexOf("{");
        if (startIndex != -1) {
            String json = response.substring(startIndex);
            log.debug("✅ Extracted JSON (length={})", json.length());
            return json;
        } else {
            log.error("❌ JSON start not found in curl response.");
            return null;
        }
    }

    private String buildCurlCommand(String url) {
        return "curl --location '" + url + "' \\\n" +
                "--header 'accept: */*' \\\n" +
                "--header 'accept-language: en-GB,en-US;q=0.9,en;q=0.8' \\\n" +
                "--header 'priority: u=1, i' \\\n" +
                "--header 'referer: " + url + "' \\\n" +
                "--header 'sec-ch-ua: \"Google Chrome\";v=\"135\", \"Not-A.Brand\";v=\"8\", \"Chromium\";v=\"135\"' \\\n" +
                "--header 'sec-ch-ua-mobile: ?0' \\\n" +
                "--header 'sec-ch-ua-platform: \"macOS\"' \\\n" +
                "--header 'sec-fetch-dest: empty' \\\n" +
                "--header 'sec-fetch-mode: cors' \\\n" +
                "--header 'sec-fetch-site: same-origin' \\\n" +
                "--header 'user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36' \\\n" +
                "--header 'Cookie: " + getCookie() + "'";
    }


    public String getCookie() {
        String cookieDetails = cookie.readCookie(); // never null; returns "" if file missing
        // Refresh backup if: backup is empty, cookie changed, or cookie looks fresh (>500 chars)
        if (StringUtils.isEmpty(cookieBackup)
                || !StringUtils.endsWithIgnoreCase(cookieDetails, "; AKA_A2=A")
                || (StringUtils.isNotEmpty(cookieDetails) && cookieDetails.length() > 500)) {
            cookieBackup = cookieDetails;
        }
        return cookieBackup;
    }
}
