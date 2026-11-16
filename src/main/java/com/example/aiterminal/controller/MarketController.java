package com.example.aiterminal.controller;

import com.example.aiterminal.service.MarketService;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 美股行情查询接口（纳斯达克/道指/标普与个股），数据来自腾讯财经公开行情。 */
@RestController
@RequestMapping("/api/market")
public class MarketController {

    private static final List<String> DEFAULT_SYMBOLS = List.of("IXIC", "DJI", "INX");

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/quotes")
    public List<MarketService.MarketQuote> quotes(
            @RequestParam(name = "symbols", required = false) String symbols) {
        if (symbols == null || symbols.isBlank()) {
            return marketService.quotes(DEFAULT_SYMBOLS);
        }
        return marketService.quotes(Arrays.asList(symbols.split(",")));
    }
}
