package com.example.aiterminal.service;

import com.example.aiterminal.config.MarketSettings;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * 美股行情连接器：抓取纳斯达克/道指/标普与常见美股个股报价（腾讯财经公开接口，GBK 文本）。
 * 聊天时按意图自动注入实时数据，也可通过 /api/market/quotes 直接查询。
 */
@Service
public class MarketService {

    private static final Logger log = LoggerFactory.getLogger(MarketService.class);
    /** 行情返回形如 v_usAAPL="200~苹果~AAPL.OQ~356.27~..."，用 ~ 分隔。 */
    private static final Pattern QUOTE_PATTERN = Pattern.compile("v_(\\w+)=\"([^\"]*)\"");
    /** 仅在原文大写时才识别为股票代码，避免把普通英文单词误判。 */
    private static final Pattern TICKER_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\$?([A-Z]{2,5})(?![A-Za-z0-9])");
    private static final Set<String> IGNORED_TICKERS = Set.of(
            "AI", "OK", "API", "CPU", "GPU", "USD", "CNY", "CEO", "CFO", "APP", "PC", "TV", "FAQ",
            "TTS", "ASR", "UI", "UX", "URL", "HTTP", "JSON", "SQL", "JAVA", "LLM", "GPT", "RAG",
            "PDF", "PNG", "JPG", "WAV", "MP3", "HTML", "CSS", "ID", "IP", "OS", "IT", "AND", "OR");

    private static final List<String> INDEX_SYMBOLS = List.of("IXIC", "DJI", "INX");

    /** 中文公司名 → 美股代码。 */
    private static final Map<String, String> NAME_TO_SYMBOL = new LinkedHashMap<>();

    static {
        NAME_TO_SYMBOL.put("苹果", "AAPL");
        NAME_TO_SYMBOL.put("英伟达", "NVDA");
        NAME_TO_SYMBOL.put("特斯拉", "TSLA");
        NAME_TO_SYMBOL.put("微软", "MSFT");
        NAME_TO_SYMBOL.put("亚马逊", "AMZN");
        NAME_TO_SYMBOL.put("谷歌", "GOOGL");
        NAME_TO_SYMBOL.put("脸书", "META");
        NAME_TO_SYMBOL.put("奈飞", "NFLX");
        NAME_TO_SYMBOL.put("台积电", "TSM");
        NAME_TO_SYMBOL.put("英特尔", "INTC");
        NAME_TO_SYMBOL.put("博通", "AVGO");
        NAME_TO_SYMBOL.put("美光", "MU");
        NAME_TO_SYMBOL.put("阿里巴巴", "BABA");
        NAME_TO_SYMBOL.put("京东", "JD");
        NAME_TO_SYMBOL.put("拼多多", "PDD");
        NAME_TO_SYMBOL.put("百度", "BIDU");
    }

    private static final Map<String, String> INDEX_KEYWORDS = new LinkedHashMap<>();

    static {
        INDEX_KEYWORDS.put("纳斯达克", "IXIC");
        INDEX_KEYWORDS.put("纳指", "IXIC");
        INDEX_KEYWORDS.put("道琼斯", "DJI");
        INDEX_KEYWORDS.put("道指", "DJI");
        INDEX_KEYWORDS.put("标普", "INX");
    }

    private static final List<String> MARKET_HINTS = List.of(
            "美股", "纳指", "纳斯达克", "道指", "道琼斯", "标普", "大盘", "行情", "股价", "收盘", "指数",
            "nasdaq", "dow", "s&p", "stock");

    private final RestClient restClient;
    private final boolean enabled;
    private final String baseUrl;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public MarketService(MarketSettings settings) {
        this.enabled = Boolean.TRUE.equals(settings.enabled());
        this.baseUrl = settings.baseUrl();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3_000);
        factory.setReadTimeout(5_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public record MarketQuote(String symbol, String name, String code, BigDecimal price, BigDecimal change,
            BigDecimal changePercent, BigDecimal open, BigDecimal previousClose, BigDecimal high, BigDecimal low,
            String currency, String time) {
    }

    /**
     * 抓取报价；失败时返回上一次缓存（若有），再不行返回空列表。
     */
    public List<MarketQuote> quotes(List<String> rawSymbols) {
        if (!enabled) {
            return List.of();
        }
        List<String> symbols = normalize(rawSymbols);
        if (symbols.isEmpty()) {
            return List.of();
        }
        String key = String.join(",", symbols);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.quotes();
        }
        try {
            String query = symbols.stream().map(symbol -> "us" + symbol).collect(Collectors.joining(","));
            byte[] body = restClient.get().uri(URI.create(baseUrl + "?q=" + query))
                    .retrieve().body(byte[].class);
            String text = new String(body == null ? new byte[0] : body, Charset.forName("GB18030"));
            List<MarketQuote> quotes = parse(text);
            if (!quotes.isEmpty()) {
                cache.put(key, new CacheEntry(quotes, Instant.now().plusSeconds(20)));
                return quotes;
            }
        } catch (RuntimeException exception) {
            log.warn("行情抓取失败：{}", exception.toString());
        }
        return cached == null ? List.of() : cached.quotes();
    }

    /** 判断用户是否在问行情，命中则返回可注入模型的实时数据说明。 */
    public Optional<String> context(String userText) {
        if (!enabled || userText == null || userText.isBlank()) {
            return Optional.empty();
        }
        List<String> symbols = detectSymbols(userText);
        if (symbols.isEmpty()) {
            return Optional.empty();
        }
        List<MarketQuote> quotes = quotes(symbols);
        if (quotes.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder text = new StringBuilder(
                "【外部行情数据 · 腾讯财经】以下是刚刚抓取的美股实时报价，回答时请直接使用这些数字并标注数据时间；"
                        + "行情数据仅供参考，不构成投资建议，涉及投资决策请提示用户自行判断：\n");
        for (MarketQuote quote : quotes) {
            text.append("- ").append(quote.name()).append("（").append(quote.symbol()).append("）最新 ")
                    .append(quote.price()).append(' ').append(quote.currency())
                    .append("，涨跌 ").append(quote.change()).append("（").append(quote.changePercent()).append("%）")
                    .append("，今开 ").append(quote.open()).append("，昨收 ").append(quote.previousClose())
                    .append("，最高 ").append(quote.high()).append("，最低 ").append(quote.low())
                    .append("，数据时间 ").append(quote.time()).append('\n');
        }
        return Optional.of(text.toString());
    }

    /** 从自然语言中识别指数/个股；只提到"美股/行情"时默认给三大指数。 */
    public List<String> detectSymbols(String userText) {
        List<String> symbols = new ArrayList<>();
        Matcher matcher = TICKER_PATTERN.matcher(userText);
        while (matcher.find() && symbols.size() < 4) {
            String ticker = matcher.group(1);
            if (!IGNORED_TICKERS.contains(ticker) && !symbols.contains(ticker)) {
                symbols.add(ticker);
            }
        }
        for (Map.Entry<String, String> entry : NAME_TO_SYMBOL.entrySet()) {
            if (symbols.size() >= 4) {
                break;
            }
            if (userText.contains(entry.getKey()) && !symbols.contains(entry.getValue())) {
                symbols.add(entry.getValue());
            }
        }
        String lower = userText.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : INDEX_KEYWORDS.entrySet()) {
            if (symbols.size() >= 4) {
                break;
            }
            if (userText.contains(entry.getKey()) && !symbols.contains(entry.getValue())) {
                symbols.add(entry.getValue());
            }
        }
        if (symbols.size() < 4 && (lower.contains("nasdaq") || lower.contains("dow") || lower.contains("s&p"))
                && !symbols.containsAll(INDEX_SYMBOLS)) {
            symbols.addAll(INDEX_SYMBOLS.stream().filter(symbol -> !symbols.contains(symbol)).toList());
        }
        if (symbols.isEmpty() && MARKET_HINTS.stream().anyMatch(lower::contains)) {
            return INDEX_SYMBOLS;
        }
        return symbols.size() > 4 ? symbols.subList(0, 4) : symbols;
    }

    private static List<String> normalize(List<String> rawSymbols) {
        if (rawSymbols == null) {
            return List.of();
        }
        List<String> symbols = new ArrayList<>();
        for (String raw : rawSymbols) {
            if (raw == null) {
                continue;
            }
            String symbol = raw.trim().toUpperCase(Locale.ROOT).replace("$", "");
            if (symbol.startsWith("US") && symbol.length() > 3) {
                symbol = symbol.substring(2);
            }
            if (symbol.matches("[A-Z]{1,6}") && !symbols.contains(symbol)) {
                symbols.add(symbol);
            }
            if (symbols.size() >= 6) {
                break;
            }
        }
        return symbols;
    }

    /** 解析腾讯行情文本；字段索引：1 名称 2 代码 3 现价 4 昨收 5 今开 30 时间 31 涨跌 32 涨跌幅 33 高 34 低 35 币种。 */
    private static List<MarketQuote> parse(String text) {
        List<MarketQuote> quotes = new ArrayList<>();
        for (String segment : text.split(";")) {
            Matcher matcher = QUOTE_PATTERN.matcher(segment);
            if (!matcher.find()) {
                continue;
            }
            String[] fields = matcher.group(2).split("~", -1);
            if (fields.length < 36) {
                continue;
            }
            String symbol = matcher.group(1).replaceFirst("(?i)^us", "");
            try {
                quotes.add(new MarketQuote(
                        symbol,
                        fields[1],
                        fields[2],
                        decimal(fields[3]),
                        decimal(fields[31]),
                        decimal(fields[32]),
                        decimal(fields[5]),
                        decimal(fields[4]),
                        decimal(fields[33]),
                        decimal(fields[34]),
                        fields[35].isBlank() ? "USD" : fields[35],
                        fields[30]));
            } catch (RuntimeException exception) {
                log.debug("行情字段解析失败：{}", segment, exception);
            }
        }
        return quotes;
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value.isBlank() ? "0" : value.trim());
    }

    private record CacheEntry(List<MarketQuote> quotes, Instant expiresAt) {
    }
}
