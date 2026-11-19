package com.example.aiterminal;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.aiterminal.service.WebSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 启动冒烟测试：Bean 装配错误（如构造器注入不明确）只在上下文加载时暴露。 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:context-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ApplicationContextTest {

    @Autowired
    private WebSearchService webSearchService;

    @Test
    void contextLoads() {
        assertThat(webSearchService).isNotNull();
    }
}
