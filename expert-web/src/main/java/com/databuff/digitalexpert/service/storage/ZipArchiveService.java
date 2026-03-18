package com.databuff.digitalexpert.service.storage;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ZipArchiveService {

    private static final Pattern NAME_PATTERN = Pattern.compile("(?m)^name\\s*:\\s*(.+?)\\s*$");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?m)^description\\s*:\\s*(.+?)\\s*$");
    private static final String SKILL_FILE_NAME = "SKILL.md";

    public SkillArchiveMetadata inspectSkillArchive(byte[] archiveBytes, String originalFilename) {
        if (archiveBytes == null || archiveBytes.length == 0) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能归档为空");
        }
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能包必须是 zip 文件");
        }
        String skillMd = extractSkillMd(archiveBytes);
        String name = extractMetadata(NAME_PATTERN, skillMd);
        String description = extractMetadata(DESCRIPTION_PATTERN, skillMd);
        if (name == null || description == null) {
            throw BusinessException.badRequest(
                    ErrorCode.SKILL_MD_MISSING_NAME_OR_DESCRIPTION,
                    "SKILL.md 必须同时包含 name 和 description"
            );
        }
        return new SkillArchiveMetadata(name, description);
    }

    public String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "SHA-256 不可用");
        }
    }

    public void buildExpertPackage(Path zipOutputPath,
                                   Path configJsonPath,
                                   List<PackagedFile> skillFiles,
                                   List<PackagedFile> staticPackageFiles) {
        try {
            Files.createDirectories(Objects.requireNonNull(zipOutputPath.getParent()));
            try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(zipOutputPath))) {
                addEntry(outputStream, "expert-config.json", configJsonPath);
                for (PackagedFile file : skillFiles) {
                    addEntry(outputStream, file.entryPath(), file.sourcePath());
                }
                for (PackagedFile file : staticPackageFiles) {
                    addEntry(outputStream, file.entryPath(), file.sourcePath());
                }
            }
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "构建专家发布包失败");
        }
    }

    public byte[] zipDirectory(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            throw BusinessException.badRequest(ErrorCode.TRAINING_OUTPUT_INVALID, "技能目录不存在: " + directory);
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(output)) {
            try (Stream<Path> files = Files.walk(directory)) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    String relative = directory.relativize(path).toString().replace("\\", "/");
                    try {
                        addEntry(zipOutputStream, relative, path);
                    } catch (IOException ex) {
                        throw new IllegalStateException("向 zip 添加文件失败: " + path, ex);
                    }
                });
            }
            zipOutputStream.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "压缩目录失败: " + directory);
        } catch (IllegalStateException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    private void addEntry(ZipOutputStream outputStream, String entryName, Path sourcePath) throws IOException {
        ZipEntry entry = new ZipEntry(entryName.replace("\\", "/"));
        outputStream.putNextEntry(entry);
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            inputStream.transferTo(outputStream);
        }
        outputStream.closeEntry();
    }

    private String extractSkillMd(byte[] archiveBytes) {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            String fallbackSkillMd = null;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName().replace("\\", "/");
                if (SKILL_FILE_NAME.equals(entryName)) {
                    return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
                if (fallbackSkillMd == null && entryName.endsWith("/" + SKILL_FILE_NAME)) {
                    fallbackSkillMd = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            if (fallbackSkillMd != null) {
                return fallbackSkillMd;
            }
        } catch (IOException ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能 zip 包无效");
        }
        throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能包中未找到 SKILL.md");
    }

    private String extractMetadata(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
