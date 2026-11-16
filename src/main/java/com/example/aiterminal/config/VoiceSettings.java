package com.example.aiterminal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 语音配置：ASR 转写与 TTS 合成（默认智谱 glm-asr / glm-tts，OpenAI 兼容路径）。 */
@ConfigurationProperties(prefix = "app.voice")
public record VoiceSettings(
        String asrModel,
        String ttsModel,
        String voice,
        String transcriptionsPath,
        String speechPath) {

    public VoiceSettings {
        if (transcriptionsPath == null || transcriptionsPath.isBlank()) {
            transcriptionsPath = "/audio/transcriptions";
        }
        if (speechPath == null || speechPath.isBlank()) {
            speechPath = "/audio/speech";
        }
    }
}
