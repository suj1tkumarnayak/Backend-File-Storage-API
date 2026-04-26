package com.filestorageapi.utils;

import software.amazon.awssdk.services.s3.model.S3Object;
import com.filestorageapi.dto.FileMetaDataDto;

public class Helpers {
    private static final String FOLDER_DELIMITER = "/";
    public static final String FALLBACK_CONTENT_TYPE = "application/octet-stream";

    public static String buildPrefix(String userName) {
        return userName.toLowerCase() + FOLDER_DELIMITER;
    }

    public static String buildKey(String userName, String fileName) {
        return buildPrefix(userName) + fileName;
    }

    public static boolean isFolder(String key) {
        return key.endsWith(FOLDER_DELIMITER);
    }

    public static boolean fileNameMatchesTerm(String key, String searchTerm) {
        String fileName = extractFileName(key);
        return fileName.toLowerCase().contains(searchTerm.toLowerCase());
    }

    public static String extractFileName(String key) {
        int lastSlash = key.lastIndexOf(FOLDER_DELIMITER);
        return lastSlash >= 0 ? key.substring(lastSlash + 1) : key;
    }
    public static FileMetaDataDto toMetadataDto(S3Object s3Object, String userPrefix) {
        String fileName = extractFileName(s3Object.key());

        return FileMetaDataDto.builder()
                .fileName(fileName)
                .s3Key(s3Object.key())
                .sizeBytes(s3Object.size())
                .contentType(null)   // not available in list response without a HEAD per object
                .lastModified(s3Object.lastModified())
                .build();
    }
}
