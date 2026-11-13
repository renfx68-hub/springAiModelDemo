package com.example.aiterminal.service;

import com.example.aiterminal.exception.ApiException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 用户上传图片的本地存储：写入 data/uploads 目录，返回通过 /api/images/{name} 访问的 URL。
 * 仅接受常见图片格式，解码后限制大小，文件名使用随机 UUID 防止路径穿越。
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);
    private static final Set<String> ALLOWED_MIME = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    private final Path root;

    public ImageStorageService(@Value("${app.upload.dir:data/uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new IllegalStateException("无法创建图片目录：" + root, exception);
        }
    }

    /** 保存 Base64 图片（允许带 data:image/...;base64, 前缀），返回访问 URL。 */
    public String save(String base64OrDataUri) {
        String mime = "image/png";
        String base64 = base64OrDataUri;
        if (base64OrDataUri.startsWith("data:")) {
            int semicolon = base64OrDataUri.indexOf(';');
            int comma = base64OrDataUri.indexOf(',');
            if (semicolon < 0 || comma <= semicolon
                    || !"base64".equalsIgnoreCase(base64OrDataUri.substring(semicolon + 1, comma))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "图片编码格式不合法");
            }
            mime = base64OrDataUri.substring(5, semicolon).toLowerCase();
            base64 = base64OrDataUri.substring(comma + 1);
        }
        if (!ALLOWED_MIME.contains(mime)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "仅支持 PNG/JPEG/WebP/GIF 格式图片");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片 Base64 解码失败");
        }
        return saveBytes(mime, bytes);
    }

    /** 保存原始图片字节（用于文生图结果落盘），返回访问 URL。 */
    public String save(byte[] bytes, String mime) {
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片内容为空");
        }
        return saveBytes(mime, bytes);
    }

    private String saveBytes(String mime, byte[] bytes) {
        if (bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片内容为空");
        }
        if (bytes.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "图片不能超过 8MB");
        }
        String name = UUID.randomUUID() + "." + extensionOf(mime);
        try {
            Files.write(root.resolve(name), bytes);
        } catch (IOException exception) {
            log.error("保存图片失败", exception);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败，请稍后重试");
        }
        return "/api/images/" + name;
    }

    /** 校验并解析图片文件路径，防止路径穿越与非法文件名。 */
    public Path resolve(String name) {
        if (name == null || !name.matches("[0-9a-fA-F-]{36}\\.(png|jpg|webp|gif)")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        Path path = root.resolve(name).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "图片不存在");
        }
        return path;
    }

    public String mimeOf(String name) {
        return switch (name.substring(name.lastIndexOf('.') + 1)) {
            case "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            default -> "image/png";
        };
    }

    /** 按 /api/images/{name} 形式的 URL 读取图片字节；文件缺失时返回 null。 */
    public byte[] loadBytes(String imageUrl) {
        try {
            return Files.readAllBytes(resolve(fileNameOf(imageUrl)));
        } catch (IOException exception) {
            return null;
        }
    }

    public String mimeOfUrl(String imageUrl) {
        return mimeOf(fileNameOf(imageUrl));
    }

    private static String fileNameOf(String imageUrl) {
        return imageUrl.substring(imageUrl.lastIndexOf('/') + 1);
    }

    private static String extensionOf(String mime) {
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "png";
        };
    }
}
