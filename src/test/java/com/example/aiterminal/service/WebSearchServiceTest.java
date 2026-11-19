package com.example.aiterminal.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.aiterminal.config.WebSearchSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 联网搜索请求与解析测试，不访问真实外部服务。 */
class WebSearchServiceTest {

    private static final String BASE_URL = "https://open.bigmodel.cn/api/paas/v4";

    private MockRestServiceServer server;
    private RestClient.Builder clientBuilder;

    @BeforeEach
    void setUp() {
        clientBuilder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(clientBuilder).build();
    }

    @Test
    void searchesAndBuildsContextWithSafeDeduplicatedSources() {
        WebSearchService service = service(true);
        server.expect(requestTo(BASE_URL + "/web_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(jsonPath("$.search_query").value("latest news"))
                .andExpect(jsonPath("$.search_engine").value("search_std"))
                .andExpect(jsonPath("$.count").value(5))
                .andRespond(withSuccess("""
                        {"search_result":[
                          {"title":"Example","link":"https://example.com/news","content":"Latest facts","media":"Example Media","publish_date":"2026-11-18"},
                          {"title":"Duplicate","link":"https://example.com/news","content":"Ignored"},
                          {"title":"Unsafe","link":"javascript:alert(1)","content":"Ignored"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var result = service.search("latest news");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().sources().size());
        assertEquals("Example", result.get().sources().getFirst().title());
        assertEquals("https://example.com/news", result.get().sources().getFirst().url());
        assertTrue(result.get().context().contains("[1] Example"));
        assertTrue(result.get().context().contains("Latest facts"));
        assertFalse(result.get().context().contains("javascript:"));
        server.verify();
    }

    @Test
    void providerFailureDegradesToEmptyResult() {
        WebSearchService service = service(true);
        server.expect(requestTo(BASE_URL + "/web_search"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertTrue(service.search("latest news").isEmpty());
        server.verify();
    }

    @Test
    void disabledSearchDoesNotCallProvider() {
        WebSearchService service = service(false);

        assertTrue(service.search("latest news").isEmpty());
        server.verify();
    }

    private WebSearchService service(boolean enabled) {
        return new WebSearchService(clientBuilder, "test-key",
                new WebSearchSettings(enabled, "search_std", 5, "/web_search"), new ObjectMapper());
    }
}
