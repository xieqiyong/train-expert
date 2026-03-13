package com.databuff.digitalexpert.service.storage;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ZipArchiveService {

    private static final Pattern NAME_PATTERN = Pattern.compile("(?m)^name\\s*:\\s*(.+?)\\s*$");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?m)^description\\s*:\\s*(.+?)\\s*$");

    public SkillArchiveMetadata inspectSkillArchive(byte[] archiveBytes, String originalFilename) {
        if (archiveBytes == null || archiveBytes.length == 0) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "Skill archive is empty");
        }
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "Skill package must be a zip file");
        }
        String skillMd = extractSkillMd(archiveBytes);
        String name = extractMetadata(NAME_PATTERN, skillMd);
        String description = extractMetadata(DESCRIPTION_PATTERN, skillMd);
        if (name == null || description == null) {
            throw BusinessException.badRequest(
                    ErrorCode.SKILL_MD_MISSING_NAME_OR_DESCRIPTION,
                    "SKILL.md must contain both name and description"
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
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "SHA-256 is unavailable");
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
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "Failed to build expert package");
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
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().replace("\\", "/").endsWith("SKILL.md")) {
                    return new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException ex) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "Invalid skill zip archive");
        }
        throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "SKILL.md not found in skill package");
    }

    private String extractMetadata(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
