package com.filestorageapi.utils;

import org.springframework.util.StringUtils;

public class StringValidation {
    public static void validateUsername(String userName){
        if (!StringUtils.hasText(userName)) {
            throw new IllegalArgumentException("userName must not be blank");
        }
        if (userName.contains("..") || userName.contains("/")) {
            throw new IllegalArgumentException("userName contains illegal characters");
        }
    }
    public static void validateSearchTerm(String searchTerm) {
        if (!StringUtils.hasText(searchTerm)) {
            throw new IllegalArgumentException("searchTerm must not be blank");
        }
    }
    public static void validateFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("fileName must not be blank");
        }
        if (fileName.contains("..") || fileName.contains("/")) {
            throw new IllegalArgumentException("fileName contains illegal characters");
        }
    }
}
