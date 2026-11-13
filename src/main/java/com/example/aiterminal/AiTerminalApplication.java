package com.example.aiterminal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** AI 聊天终端应用入口。 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AiTerminalApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiTerminalApplication.class, args);
    }
}
