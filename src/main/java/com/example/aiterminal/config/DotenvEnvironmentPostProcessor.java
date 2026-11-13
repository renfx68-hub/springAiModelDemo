package com.example.aiterminal.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * 启动时自动加载工作目录下的 .env 文件。
 * 这样在 IDEA 中直接运行也无需手动配置环境变量；真实环境变量优先级更高，不会被覆盖。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DotenvEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path file = Paths.get(System.getProperty("user.dir", ".")).resolve(".env");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            int loaded = 0;
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int index = line.indexOf('=');
                if (index < 1) {
                    continue;
                }
                String name = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                if (name.isEmpty() || environment.getProperty(name) != null) {
                    continue;
                }
                System.setProperty(name, value);
                loaded++;
            }
            if (loaded > 0) {
                log.info("已从 {} 加载 {} 项本地配置", file, loaded);
            }
        } catch (IOException exception) {
            log.warn("读取 .env 失败：{}", exception.getMessage());
        }
    }
}
