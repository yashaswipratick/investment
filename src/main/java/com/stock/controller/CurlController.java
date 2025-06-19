package com.stock.controller;

import com.stock.curl.CurlCommandGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(CurlController.ENDPOINT)
public class CurlController {

    public static final String ENDPOINT = "/stock/investment/v1.0";

    @Autowired
    private CurlCommandGenerator curlCommandGenerator;

    @GetMapping("/generate-curl")
    public String getCurl() {
        return curlCommandGenerator.generateCurlCommand("https://www.nseindia.com/api/quote-equity?symbol=APOLLOHOSP");
    }
}
