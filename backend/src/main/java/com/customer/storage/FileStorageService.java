package com.customer.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * File storage abstraction.
 *
 * <p>The default implementation saves files to the local filesystem
 * under {@code filesUpload/} (at the project root level).
 * To switch to MinIO (or S3-compatible storage) in the future,
 * create a new implementation and mark it as {@code @Primary}.</p>
 */
public interface FileStorageService {

    /**
     * Save an uploaded file to storage.
     *
     * @param file the uploaded file (never null)
     * @return file info with access URL
     */
    FileInfo save(MultipartFile file);

    /**
     * Delete a file from storage.
     *
     * @param storedPath the relative storage path returned by {@link #save(MultipartFile)}
     * @return true if successfully deleted
     */
    boolean delete(String storedPath);

    /**
     * Build the external-access URL for a stored path.
     *
     * @param storedPath relative storage path
     * @return full URL string
     */
    String getUrl(String storedPath);
}
