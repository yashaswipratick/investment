package com.stock.curl;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;

@Service
public class CurlCommandGenerator {

    @Autowired
    private NseClientConfig nseClientConfig;

    @Autowired
    private ReadCookie cookie;

    String COOKIE_BACKUP = "";

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
                System.err.println("❌ Curl failed with exit code: " + exitCode);
            }

        } catch (Exception e) {
            System.err.println("❌ Error executing curl command: " + e.getMessage());
        }

        return output.toString();
    }

    private String extractJsonFromResponse(String response) {
        int startIndex = response.indexOf("{");
        if (startIndex != -1) {
            String json = response.substring(startIndex);
            System.out.println("✅ Pure Extracted JSON:");
            System.out.println(json);
            return json;
        } else {
            System.err.println("❌ JSON start not found.");
            return null;
        }
    }

    private String buildCurlCommand(String url) {
        return "curl --location '" + url +"' \\\n" +
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
                "--header 'Cookie: "+getCookie()+"'";
    }

    private String getCookie() {
        // Fetch cookie from config or an injected file reader class
        String cookieDetails = cookie.readCookie();
        if (COOKIE_BACKUP.isEmpty() || (StringUtils.isNotEmpty(cookieDetails) && cookieDetails.length() > 500)) {
            COOKIE_BACKUP = cookie.readCookie();
        }
        return COOKIE_BACKUP;
    }
}
