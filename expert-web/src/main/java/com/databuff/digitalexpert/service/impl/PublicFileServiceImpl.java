package com.databuff.digitalexpert.service.impl;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import com.databuff.digitalexpert.service.PublicFileService;
import com.databuff.digitalexpert.service.storage.SharedStorageService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class PublicFileServiceImpl implements PublicFileService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;
    private static final String PUBLIC_IMAGE_PREFIX = "/api/v1/files/public/images";
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{8}");
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("[a-f0-9\\-]{36}\\.(png|jpg|jpeg|webp)");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp");

    @Autowired
    private SharedStorageService sharedStorageService;

    @Override
    public String uploadImage(MultipartFile file) {
        byte[] bytes = readImageBytes(file);
        String extension = resolveImageExtension(file.getOriginalFilename());
        validateImageContent(extension, bytes);

        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileName = UUID.randomUUID() + "." + extension;
        Path target = sharedStorageService.getStaticPackage()
                .resolve("uploads")
                .resolve("images")
                .resolve(date)
                .resolve(fileName)
                .normalize();
        sharedStorageService.writeBytes(target, bytes);
        return PUBLIC_IMAGE_PREFIX + "/" + date + "/" + fileName;
    }

    @Override
    public PublicImageResource loadImage(String date, String fileName) {
        String normalizedDate = normalizeDate(date);
        String normalizedFileName = normalizePublicFileName(fileName);
        Path imagePath = sharedStorageService.getStaticPackage()
                .resolve("uploads")
                .resolve("images")
                .resolve(normalizedDate)
                .resolve(normalizedFileName)
                .normalize();
        sharedStorageService.ensureFileExists(imagePath);
        return new PublicImageResource(imagePath, resolveMediaType(normalizedFileName));
    }

    private byte[] readImageBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片文件不能为空");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片大小不能超过 5MB");
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "读取图片文件失败");
        }
    }

    private String resolveImageExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片文件名不能为空");
        }
        String fileName = extractFileName(originalFilename);
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片文件后缀不合法");
        }
        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "仅支持 png、jpg、jpeg、webp 图片");
        }
        return extension;
    }

    private void validateImageContent(String extension, byte[] bytes) {
        boolean valid = switch (extension) {
            case "png" -> hasPngSignature(bytes);
            case "jpg", "jpeg" -> hasJpegSignature(bytes);
            case "webp" -> hasWebpSignature(bytes);
            default -> false;
        };
        if (!valid) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片文件内容与后缀不匹配");
        }
    }

    private String normalizeDate(String date) {
        if (date == null || !DATE_PATTERN.matcher(date).matches()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片日期路径不合法");
        }
        return date;
    }

    private String normalizePublicFileName(String fileName) {
        if (fileName == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片文件名不合法");
        }
        String normalized = extractFileName(fileName).toLowerCase(Locale.ROOT);
        if (!FILE_NAME_PATTERN.matcher(normalized).matches()) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "图片文件名不合法");
        }
        return normalized;
    }

    private String extractFileName(String value) {
        String normalized = value.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        return slashIndex < 0 ? normalized : normalized.substring(slashIndex + 1);
    }

    private MediaType resolveMediaType(String fileName) {
        if (fileName.endsWith(".png")) {
            return MediaType.IMAGE_PNG;
        }
        if (fileName.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        return MediaType.IMAGE_JPEG;
    }

    private boolean hasPngSignature(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private boolean hasJpegSignature(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean hasWebpSignature(byte[] bytes) {
        return bytes.length >= 12
                && "RIFF".equals(new String(bytes, 0, 4, StandardCharsets.US_ASCII))
                && "WEBP".equals(new String(bytes, 8, 4, StandardCharsets.US_ASCII));
    }
}
