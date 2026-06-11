package com.customer.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Local filesystem implementation of {@link FileStorageService}.
 *
 * <p>Files are stored under {@code <project-root>/filesUpload/} with the structure:
 * <pre>
 * filesUpload/
 *   images/
 *     2026/06/09/
 *       uuid.jpg
 *   files/
 *     2026/06/09/
 *       uuid.pdf
 * </pre>
 *
 * <p>The base directory is the sibling of the {@code backend/} module,
 * so it lives at the project root level alongside {@code frontend/} and {@code backend/}.
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageService.class);

    /** Daily sub-directory format: yyyy/MM/dd */
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** Images sub-directory name */
    private static final String IMAGES_DIR = "images";

    /** Files sub-directory name */
    private static final String FILES_DIR = "files";

    /**
     * Storage root path. Defaults to {@code <user.dir>/../filesUpload},
     * i.e. a {@code filesUpload/} directory sibling to the {@code backend/} module.
     */
    @Value("${storage.local.base-path:}")
    private String basePath;

    /** URL prefix for external access, e.g. /uploads */
    @Value("${storage.url-prefix:/uploads}")
    private String urlPrefix;

    /** Resolved absolute root directory */
    private Path storageRoot;

    @PostConstruct
    public void init() {
        if (basePath == null || basePath.isBlank()) {
            // 默认：JAR 包所在目录下的 filesUpload/
            String jarDir = System.getProperty("user.dir");
            basePath = jarDir + File.separator + "filesUpload";
        }
        storageRoot = Paths.get(basePath).normalize().toAbsolutePath();
        try {
            Files.createDirectories(storageRoot.resolve(IMAGES_DIR));
            Files.createDirectories(storageRoot.resolve(FILES_DIR));
            log.info("Local file storage root: {}", storageRoot);
        } catch (IOException e) {
            log.error("Failed to create storage directories at {}", storageRoot, e);
        }
    }

    @Override
    public FileInfo save(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null) originalName = "unknown";

        // Determine sub-directory: images/ or files/
        String subDir = isImageFile(file) ? IMAGES_DIR : FILES_DIR;

        // Daily sub-directory: yyyy/MM/dd
        String dayPath = LocalDate.now().format(DAY_FORMAT);

        // Unique filename
        String ext = "";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) ext = originalName.substring(dot);
        String storedName = UUID.randomUUID().toString().replace("-", "") + ext;

        // Full path: storageRoot / images|files / yyyy/MM/dd / uuid.ext
        String relativePath = subDir + "/" + dayPath + "/" + storedName;
        Path targetPath = storageRoot.resolve(subDir).resolve(dayPath).resolve(storedName);

        try {
            Files.createDirectories(targetPath.getParent());
            file.transferTo(targetPath.toFile());
            log.info("File saved: {} (size={})", targetPath, file.getSize());

            return new FileInfo(
                    originalName,
                    relativePath,
                    urlPrefix + "/" + relativePath.replace("\\", "/"),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file: " + originalName, e);
        }
    }

    @Override
    public boolean delete(String storedPath) {
        if (storedPath == null) return false;
        Path target = storageRoot.resolve(storedPath).normalize();

        // Security: ensure the resolved path is still under storageRoot
        if (!target.startsWith(storageRoot)) {
            log.warn("Attempted to delete file outside storage root: {}", target);
            return false;
        }

        try {
            boolean deleted = Files.deleteIfExists(target);
            if (deleted) log.info("File deleted: {}", target);
            return deleted;
        } catch (IOException e) {
            log.error("Failed to delete file: {}", target, e);
            return false;
        }
    }

    @Override
    public String getUrl(String storedPath) {
        if (storedPath == null) return null;
        return urlPrefix + "/" + storedPath.replace("\\", "/");
    }

    /**
     * Check if the uploaded file is an image by MIME type.
     */
    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        // Fallback: check extension
        String name = file.getOriginalFilename();
        if (name != null) {
            String lower = name.toLowerCase();
            return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                    || lower.endsWith(".png") || lower.endsWith(".gif")
                    || lower.endsWith(".bmp") || lower.endsWith(".webp")
                    || lower.endsWith(".svg");
        }
        return false;
    }
}
