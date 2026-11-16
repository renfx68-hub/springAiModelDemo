package com.example.aiterminal.controller;

import com.example.aiterminal.exception.ApiException;
import com.example.aiterminal.service.VoiceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 语音接口：麦克风录音转文字（ASR）与回复朗读（TTS）。 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final VoiceService voiceService;

    public VoiceController(VoiceService voiceService) {
        this.voiceService = voiceService;
    }

    public record VoiceTextResponse(String text) {
    }

    public record SpeakRequest(
            @NotBlank @Size(max = 2000, message = "朗读文本过长") String text) {
    }

    @PostMapping("/transcribe")
    public VoiceTextResponse transcribe(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "音频内容为空");
        }
        try {
            return new VoiceTextResponse(voiceService.transcribe(file.getBytes(), file.getOriginalFilename()));
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "音频读取失败");
        }
    }

    @PostMapping("/speak")
    public ResponseEntity<byte[]> speak(@Valid @RequestBody SpeakRequest request) {
        VoiceService.SpeakAudio audio = voiceService.speak(request.text().trim());
        return ResponseEntity.ok()
                .contentType(audio.mediaType())
                .body(audio.data());
    }
}
