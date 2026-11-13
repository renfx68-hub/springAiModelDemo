package com.example.aiterminal.controller;

import com.example.aiterminal.exception.ApiException;
import com.example.aiterminal.service.ImageStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户上传图片的读取接口；文件名不可猜测且带浏览器缓存。 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageStorageService imageStorage;

    public ImageController(ImageStorageService imageStorage) {
        this.imageStorage = imageStorage;
    }

    @GetMapping("/{name}")
    public ResponseEntity<byte[]> get(@PathVariable("name") String name) {
        Path path = imageStorage.resolve(name);
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imageStorage.mimeOf(name)))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(30)))
                .body(bytes);
    }
}
