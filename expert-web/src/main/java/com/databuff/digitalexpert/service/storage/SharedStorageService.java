package com.databuff.digitalexpert.service.storage;

import com.databuff.digitalexpert.common.BusinessException;
import com.databuff.digitalexpert.config.ExpertProperties;
import com.databuff.digitalexpert.dao.enums.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SharedStorageService {

    @Autowired
    private ExpertProperties properties;

    public Path getStaticPackage() {
        return Paths.get(properties.getStaticPackage()).toAbsolutePath().normalize();
    }

    public Path resolveSkillDirectory(Long skillId) {
        return getStaticPackage().resolve("skills").resolve(String.valueOf(skillId)).normalize();
    }

    public Path resolveStaticPackageDirectory(Long staticPackageId) {
        return getStaticPackage().resolve("static_packages").resolve(String.valueOf(staticPackageId)).normalize();
    }

    public Path resolveExpertRoot(Long expertId) {
        return getStaticPackage().resolve("experts").resolve(String.valueOf(expertId)).normalize();
    }

    public Path resolveExpertCurrentDirectory(Long expertId) {
        return resolveExpertRoot(expertId).resolve("current").normalize();
    }

    public Path resolveExpertStagingDirectory(Long expertId, String taskId) {
        return resolveExpertRoot(expertId).resolve("staging").resolve(taskId).normalize();
    }

    public Path resolveTemporaryDirectory(String category) {
        return getStaticPackage().resolve("tmp").resolve(category).normalize();
    }

    public void recreateDirectory(Path directory) {
        deleteRecursively(directory);
        createDirectories(directory);
    }

    public void createDirectories(Path directory) {
        ensureWithinSharedRoot(directory);
        try {
            Files.createDirectories(directory);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "创建目录失败: " + directory);
        }
    }

    public void writeBytes(Path target, byte[] data) {
        ensureWithinSharedRoot(target);
        createDirectories(target.getParent());
        try {
            Files.write(target, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "写入文件失败: " + target);
        }
    }

    public void moveFile(Path source, Path target) {
        ensureWithinSharedRoot(source);
        ensureWithinSharedRoot(target);
        createDirectories(target.getParent());
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR,
                    "移动文件失败: " + source + " -> " + target);
        }
    }

    public void ensureFileExists(Path path) {
        ensureWithinSharedRoot(path);
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw BusinessException.badRequest(ErrorCode.PACKAGE_PATH_MISSING, "文件不存在: " + path);
        }
    }

    public boolean exists(Path path) {
        ensureWithinSharedRoot(path);
        return Files.exists(path);
    }

    public String toStoragePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    public void promoteExpertDirectory(Path stagingDirectory, Path currentDirectory) {
        ensureWithinSharedRoot(stagingDirectory);
        ensureWithinSharedRoot(currentDirectory);
        Path expertRoot = currentDirectory.getParent();
        Path backupDirectory = expertRoot.resolve("backup-" + System.nanoTime()).normalize();
        try {
            createDirectories(expertRoot);
            if (Files.exists(currentDirectory)) {
                Files.move(currentDirectory, backupDirectory, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(stagingDirectory, currentDirectory, StandardCopyOption.REPLACE_EXISTING);
            deleteRecursively(backupDirectory);
        } catch (IOException ex) {
            tryRestoreBackup(currentDirectory, backupDirectory);
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "提升专家目录失败");
        } finally {
            if (Files.exists(stagingDirectory)) {
                deleteRecursively(stagingDirectory);
            }
        }
    }

    public void deleteRecursively(Path path) {
        if (path == null) {
            return;
        }
        ensureWithinSharedRoot(path);
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(current -> {
                try {
                    Files.deleteIfExists(current);
                } catch (IOException ex) {
                    throw new IllegalStateException("删除路径失败 " + current, ex);
                }
            });
        } catch (IOException ex) {
            throw BusinessException.internal(ErrorCode.INTERNAL_ERROR, "删除路径失败: " + path);
        }
    }

    private void tryRestoreBackup(Path currentDirectory, Path backupDirectory) {
        if (!Files.exists(backupDirectory) || Files.exists(currentDirectory)) {
            return;
        }
        try {
            Files.move(backupDirectory, currentDirectory, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // 尽力恢复备份，不再抛出异常。
        }
    }

    private void ensureWithinSharedRoot(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        Path root = getStaticPackage();
        if (!normalized.startsWith(root)) {
            throw BusinessException.badRequest(ErrorCode.INVALID_REQUEST, "路径超出共享根目录: " + normalized);
        }
    }
}
