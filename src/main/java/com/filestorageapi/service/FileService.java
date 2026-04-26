package com.filestorageapi.service;

import com.filestorageapi.dto.FileMetaDataDto;
import com.filestorageapi.dto.SearchResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileService {
    SearchResponseDto searchFiles(String username, String searchTerm);

    InputStream downloadFile(String username, String searchTerm);

    FileMetaDataDto getFileMetaData(String username, String fileName);

    FileMetaDataDto uploadFile(String username, MultipartFile file);
}
