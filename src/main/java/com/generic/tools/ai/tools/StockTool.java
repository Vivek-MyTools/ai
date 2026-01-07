package com.generic.tools.ai.tools;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.logging.Logger;

import dev.langchain4j.agent.tool.Tool;
import yahoofinance.Stock;
import yahoofinance.YahooFinance;

public class StockTool {

	private static final Logger LOG = Logger.getLogger(StockTool.class.getName());
	
    @Tool("Get the price of a stock by its ticker symbol (e.g., AAPL, GOOG, MSFT).")
    public String getStockPrice(String ticker) {
        // Sanitize input (remove spaces/quotes if LLM hallucinates them)
        String symbol = ticker.replaceAll("[^a-zA-Z]", "").toUpperCase();
        
        LOG.info("   --> 📈 Stock Tool: getStockPrice(" + symbol + ")");
       
        try {
            Stock stock = YahooFinance.get(symbol);
            
            if (stock == null || stock.getQuote() == null || stock.getQuote().getPrice() == null) {
                return "Error: Could not find stock data for symbol '" + symbol + "'.";
            }

            BigDecimal price = stock.getQuote().getPrice();
            String currency = stock.getCurrency();
            String name = stock.getName();

            // Return a concise string for the LLM
            return String.format("%s (%s): %.2f %s", name, symbol, price, currency);

        } catch (IOException e) {
            return "Error fetching stock price: " + e.getMessage();
        }
    }
}