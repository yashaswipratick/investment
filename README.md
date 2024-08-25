# investment

<!-- TOC -->
* [About The Project](#about-the-project)
* [NSE URL to fetch data from NSE website](#nse-url-to-fetch-data-from-nse-website)
* [Pre-requisites for the projects.](#pre-requisites-for-the-projects)
* [Steps to run the project locally](#steps-to-run-the-project-locally)
* [Swagger UI for API Docs](#swagger-ui-for-api-docs-)
<!-- TOC -->

# About The Project

Stock Market Analysis and P&L calculation

# NSE URL to fetch data from NSE website

1. For Live Equity Data -> https://www.nseindia.com/market-data/live-equity-market?symbol=NIFTY%20500
2. For Live Equity Data -> https://www.nseindia.com/market-data/live-equity-market?symbol=NIFTY%20200
3. For Live Equity Data -> https://www.nseindia.com/market-data/live-equity-market?symbol=NIFTY%20100
4. For Live Equity Data -> https://www.nseindia.com/market-data/live-equity-market?symbol=NIFTY%2050
5. Stock Symbol and details -> https://archives.nseindia.com/content/equities/EQUITY_L.csv
6. Fetch Stock details -> https://www.nseindia.com/api/quote-equity?symbol=ONGC


# Pre-requisites for the projects.
    # **Below are the required pre-requisites**

    1. Java 17.
    2. Maven 3.9.
    3. Cassandra 4.1.3


# Steps to run the project locally

    1. mvn clean compile
    2. Configure your cassandra details in application.yml
    3. Run Investment Application class.

# Swagger UI for API Docs 
   
 [swagger-ui](http://localhost:8080/swagger-ui.html)
