package com.filestorageapi.service;

import com.filestorageapi.dto.FileMetaDataDto;
import com.filestorageapi.dto.SearchResponseDto;
import com.filestorageapi.exception.FileNotFoundException;
import com.filestorageapi.repository.S3StorageRepository;
import com.filestorageapi.utils.Helpers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.filestorageapi.utils.StringValidation;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static com.filestorageapi.utils.Helpers.*;
import static com.filestorageapi.utils.StringValidation.validateFileName;
import static com.filestorageapi.utils.StringValidation.validateUsername;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3FileService implements FileService{

    private final S3StorageRepository repository;

    @Override
    public SearchResponseDto searchFiles(String userName, String searchTerm) {
        validateUsername(userName);
        StringValidation.validateSearchTerm(searchTerm);

        String prefix = Helpers.buildPrefix(userName);
        List<S3Object> allObjects = repository.listObjects(prefix);

        List<FileMetaDataDto> matched = allObjects.stream()
                .filter(obj->!isFolder(obj.key()))
                .filter(obj->fileNameMatchesTerm(obj.key(), searchTerm))
                .map(obj -> toMetadataDto(obj, prefix))
                .toList();

        log.info("Search '{}' for user '{}' returned {} result(s)", searchTerm, userName, matched.size());

        return SearchResponseDto.builder()
                .userName(userName)
                .searchTerm(searchTerm)
                .totalResults(matched.size())
                .files(matched)
                .build();
    }

    @Override
    public InputStream downloadFile(String userName, String fileName) {
        validateUsername(userName);
        validateFileName(fileName);

        String key = buildKey(userName, fileName);

        if (!repository.objectExists(key)) {
            throw new FileNotFoundException(userName, fileName);
        }

        log.info("Downloading '{}' for user '{}'", fileName, userName);
        return repository.downloadObject(key);
    }

    @Override
    public FileMetaDataDto getFileMetaData(String userName, String fileName) {
        validateUsername(userName);
        validateFileName(fileName);

        String key = buildKey(userName, fileName);

        if (!repository.objectExists(key)) {
            throw new FileNotFoundException(userName, fileName);
        }

        HeadObjectResponse head = repository.headObject(key);

        return FileMetaDataDto.builder()
                .fileName(fileName)
                .s3Key(key)
                .sizeBytes(head.contentLength())
                .contentType(head.contentType() != null ? head.contentType() : FALLBACK_CONTENT_TYPE)
                .lastModified(head.lastModified())
                .build();
    }

    @Override
    public FileMetaDataDto uploadFile(String userName, MultipartFile file) {
        validateUsername(userName);

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file must not be empty");
        }

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : file.getName()
        );
        validateFileName(originalFileName);

        String key = buildKey(userName, originalFileName);
        String contentType = file.getContentType() != null ? file.getContentType() : Helpers.FALLBACK_CONTENT_TYPE;

        try (InputStream inputStream = file.getInputStream()) {
            repository.uploadObject(key, inputStream, contentType, file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file: " + e.getMessage(), e);
        }

        log.info("Uploaded '{}' for user '{}' ({} bytes)", originalFileName, userName, file.getSize());

        return FileMetaDataDto.builder()
                .fileName(originalFileName)
                .s3Key(key)
                .sizeBytes(file.getSize())
                .contentType(contentType)
                .lastModified(java.time.Instant.now())
                .build();
    }

}
