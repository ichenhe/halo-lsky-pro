package me.chenhe.halo.lskypro.client;

/**
 * @param size in KB
 */
public record UploadResponse(
    String key,
    String name,
    String origin_name,
    String extension,
    String sha1,
    float size,
    String mimetype,
    Links links
) {
}