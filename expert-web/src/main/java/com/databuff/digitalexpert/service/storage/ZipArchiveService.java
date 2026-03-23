package com.databuff.digitalexpert.service.storage;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Service;

@Service
public class ZipArchiveService {

    private static final Pattern NAME_PATTERN = Pattern.compile("(?m)^name\\s*:\\s*(.+?)\\s*$");
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile("(?m)^description\\s*:\\s*(.+?)\\s*$");
    private static final String SKILL_FILE_NAME = "SKILL.md";
    private static final String ZIP_SUFFIX = ".zip";

    public SkillArchiveMetadata inspectSkillArchive(byte[] archiveBytes, String originalFilename) {
        if (archiveBytes == null || archiveBytes.length == 0) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能归档不能为空");
        }
        if (originalFilename == null || !originalFilename.toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX)) {
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
            String rootDirectory = resolveArchiveRootDirectory(zipOutputPath.getFileName());
            try (OutputStream fileOutputStream = Files.newOutputStream(zipOutputPath);
                 ZipArchiveOutputStream outputStream = createZipOutputStream(fileOutputStream)) {
                addDirectoryEntry(outputStream, rootDirectory + "/");
                addDirectoryEntry(outputStream, rootDirectory + "/skills/");
                addDirectoryEntry(outputStream, rootDirectory + "/static_packages/");
                addFileEntry(outputStream, rootDirectory + "/expert-config.json", configJsonPath);
                for (PackagedFile file : skillFiles) {
                    expandZipFile(outputStream, rootDirectory + "/" + normalizeEntryPath(file.entryPath()), file.sourcePath());
                }
                for (PackagedFile file : staticPackageFiles) {
                    addFileEntry(outputStream, rootDirectory + "/" + normalizeEntryPath(file.entryPath()), file.sourcePath());
                }
                outputStream.finish();
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
             ZipArchiveOutputStream zipOutputStream = createZipOutputStream(output);
             Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String relative = normalizeEntryPath(directory.relativize(path).toString());
                try {
                    addFileEntry(zipOutputStream, relative, path);
                } catch (IOException ex) {
                    throw new IllegalStateException("向 zip 添加文件失败: " + path, ex);
                }
            });
            zipOutputStream.finish();
            return output.toByteArray();
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "压缩目录失败: " + directory);
        } catch (IllegalStateException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, ex.getMessage());
        }
    }

    public void extractZipToDirectory(Path archivePath, Path targetDirectory, boolean stripWrapperDirectory) {
        if (archivePath == null || !Files.isRegularFile(archivePath)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能包文件不存在: " + archivePath);
        }
        if (targetDirectory == null) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "技能输出目录不能为空");
        }
        try {
            Files.createDirectories(targetDirectory);
            try (ZipFile zipFile = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8)) {
                String wrapperDirectory = stripWrapperDirectory ? resolveWrapperDirectory(zipFile) : null;
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String entryName = normalizeEntryPath(entry.getName());
                    if (entryName.isBlank()) {
                        continue;
                    }
                    if (wrapperDirectory != null && entryName.startsWith(wrapperDirectory + "/")) {
                        entryName = entryName.substring(wrapperDirectory.length() + 1);
                    }
                    if (entryName.isBlank()) {
                        continue;
                    }
                    Path outputPath = resolveExtractOutputPath(targetDirectory, entryName);
                    if (entry.isDirectory()) {
                        Files.createDirectories(outputPath);
                        continue;
                    }
                    Files.createDirectories(outputPath.getParent());
                    try (InputStream inputStream = zipFile.getInputStream(entry)) {
                        Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "解压技能包失败: " + archivePath);
        }
    }

    private ZipArchiveOutputStream createZipOutputStream(OutputStream outputStream) {
        ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(outputStream);
        zipOutputStream.setEncoding(StandardCharsets.UTF_8.name());
        zipOutputStream.setUseLanguageEncodingFlag(true);
        zipOutputStream.setFallbackToUTF8(true);
        zipOutputStream.setCreateUnicodeExtraFields(
                ZipArchiveOutputStream.UnicodeExtraFieldPolicy.ALWAYS
        );
        return zipOutputStream;
    }

    private void addDirectoryEntry(ZipArchiveOutputStream outputStream, String entryName) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(ensureDirectoryEntry(entryName));
        outputStream.putArchiveEntry(entry);
        outputStream.closeArchiveEntry();
    }

    private void addFileEntry(ZipArchiveOutputStream outputStream, String entryName, Path sourcePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(sourcePath)) {
            addStreamEntry(outputStream, entryName, inputStream);
        }
    }

    private void addStreamEntry(ZipArchiveOutputStream outputStream, String entryName, InputStream inputStream) throws IOException {
        ZipArchiveEntry entry = new ZipArchiveEntry(normalizeEntryPath(entryName));
        outputStream.putArchiveEntry(entry);
        inputStream.transferTo(outputStream);
        outputStream.closeArchiveEntry();
    }

    private void expandZipFile(ZipArchiveOutputStream outputStream, String baseEntryPath, Path archivePath) throws IOException {
        try (ZipFile zipFile = new ZipFile(archivePath.toFile(), StandardCharsets.UTF_8)) {
            String wrapperDirectory = resolveWrapperDirectory(zipFile);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeEntryPath(entry.getName());
                if (wrapperDirectory != null && entryName.startsWith(wrapperDirectory + "/")) {
                    entryName = entryName.substring(wrapperDirectory.length() + 1);
                }
                if (entryName.isBlank()) {
                    continue;
                }
                try (InputStream inputStream = zipFile.getInputStream(entry)) {
                    addStreamEntry(outputStream, baseEntryPath + "/" + entryName, inputStream);
                }
            }
        }
    }

    private String resolveWrapperDirectory(ZipFile zipFile) {
        String wrapperDirectory = null;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String entryName = normalizeEntryPath(entry.getName());
            int slashIndex = entryName.indexOf('/');
            if (slashIndex < 0) {
                return null;
            }
            String currentWrapper = entryName.substring(0, slashIndex);
            if (wrapperDirectory == null) {
                wrapperDirectory = currentWrapper;
                continue;
            }
            if (!wrapperDirectory.equals(currentWrapper)) {
                return null;
            }
        }
        return wrapperDirectory;
    }

    private Path resolveExtractOutputPath(Path targetDirectory, String entryName) {
        Path normalizedTargetDirectory = targetDirectory.toAbsolutePath().normalize();
        Path outputPath = normalizedTargetDirectory.resolve(entryName).normalize();
        if (!outputPath.startsWith(normalizedTargetDirectory)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "技能包中存在非法文件路径: " + entryName);
        }
        return outputPath;
    }

    private String extractSkillMd(byte[] archiveBytes) {
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(archiveBytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            String fallbackSkillMd = null;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = normalizeEntryPath(entry.getName());
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

    private String resolveArchiveRootDirectory(Path fileName) {
        String rawName = fileName == null ? "expert-package" : fileName.toString();
        String baseName = rawName.toLowerCase(Locale.ROOT).endsWith(ZIP_SUFFIX)
                ? rawName.substring(0, rawName.length() - ZIP_SUFFIX.length())
                : rawName;
        String normalized = normalizeEntryPath(baseName);
        return normalized.isBlank() ? "expert-package" : normalized;
    }

    private String ensureDirectoryEntry(String entryName) {
        String normalized = normalizeEntryPath(entryName);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private String normalizeEntryPath(String rawPath) {
        if (rawPath == null) {
            return "";
        }
        String normalized = rawPath.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank()) {
            return "";
        }
        Path normalizedPath = Path.of(normalized).normalize();
        String result = normalizedPath.toString().replace('\\', '/');
        if (result.equals(".") || result.equals("")) {
            return "";
        }
        if (result.equals("..") || result.startsWith("../")) {
            throw BusinessException.badRequest(ErrorCode.INVALID_SKILL_PACKAGE, "zip 中存在非法文件路径: " + rawPath);
        }
        return result;
    }

    private String extractMetadata(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }
}
