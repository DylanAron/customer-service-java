package com.customer.storage;

/**
 * File information returned after a successful upload.
 */
public class FileInfo {
    private String filename;       // original filename
    private String storedPath;     // relative storage path (e.g. "images/2026/06/09/uuid.jpg")
    private String url;            // full URL path (e.g. "/uploads/images/2026/06/09/uuid.jpg")
    private long size;
    private String mimeType;

    public FileInfo() {}

    public FileInfo(String filename, String storedPath, String url, long size, String mimeType) {
        this.filename = filename;
        this.storedPath = storedPath;
        this.url = url;
        this.size = size;
        this.mimeType = mimeType;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
}
