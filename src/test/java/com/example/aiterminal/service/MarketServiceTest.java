package com.example.aiterminal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.aiterminal.config.MarketSettings;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 行情意图识别（纯逻辑，不触网）：中文公司名、指数关键词、显式代码与兜底三大指数。 */
class MarketServiceTest {

    private final MarketService service = new MarketService(new MarketSettings(true, "https://qt.gtimg.cn"));

    @Test
    void detectsIndexKeywords() {
        assertTrue(service.detectSymbols("纳斯达克现在多少点？").contains("IXIC"));
        assertTrue(service.detectSymbols("道指今天涨了吗").contains("DJI"));
        assertTrue(service.detectSymbols("标普500走势").contains("INX"));
    }

    @Test
    void detectsChineseCompanyNamesAndTickers() {
        assertTrue(service.detectSymbols("英伟达最近怎么样").contains("NVDA"));
        assertTrue(service.detectSymbols("苹果和特斯拉对比一下").containsAll(List.of("AAPL", "TSLA")));
        assertTrue(service.detectSymbols("AAPL 现在多少钱").contains("AAPL"));
    }

    @Test
    void ignoresOrdinaryEnglishWordsAndUsesIndexFallback() {
        assertEquals(List.of(), service.detectSymbols("帮我写个 SQL 查询"));
        assertEquals(List.of("IXIC", "DJI", "INX"), service.detectSymbols("今天美股行情怎么样"));
    }

    @Test
    void plainChatHasNoMarketContext() {
        assertTrue(service.context("帮我写一个登录接口").isEmpty());
    }
}
