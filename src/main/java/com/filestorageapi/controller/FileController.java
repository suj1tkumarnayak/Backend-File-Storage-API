package com.filestorageapi.controller;

import com.filestorageapi.dto.FileMetaDataDto;
import com.filestorageapi.dto.SearchResponseDto;
import com.filestorageapi.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;

import java.io.InputStream;

@RestController
@Validated
@Slf4j
@RequiredArgsConstructor
@RequestMapping("api/v1/users/{userName}/files")
@Tag(name = "File Storage", description = "Search, Upload, and download user files stored in s3")
public class FileController {
    private final FileService fileService;

    @Operation(
            summary = "Search files by name",
            description = "Returns all files in the user's S3 folder whose names contain the given search term (case-insensitive)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Search completed (empty list if nothing found)"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server or S3 error", content = @Content)
    })
    @GetMapping("/search")
    public ResponseEntity<SearchResponseDto> searchFiles(
            @Parameter(description = "User name (folder owner)", example = "sandy")
            @PathVariable @NotBlank String userName,

            @Parameter(description = "Substring to search for in file names", example = "logistics")
            @RequestParam @NotBlank String term
    ) {
        log.info("Search request: user='{}', term='{}'", userName, term);
        SearchResponseDto response = fileService.searchFiles(userName, term);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Download a file",
            description = "Streams the content of a specific file from the user's S3 folder."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "File content streamed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters", content = @Content),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server or S3 error", content = @Content)
    })
    @GetMapping("/{fileName}")
    public ResponseEntity<InputStreamResource> downloadFile(
            @Parameter(description = "User name (folder owner)", example = "sandy")
            @PathVariable @NotBlank String userName,

            @Parameter(description = "Exact file name to download", example = "logistics-report-2024.pdf")
            @PathVariable @NotBlank String fileName
    ) {
        log.info("Download request: user='{}', file='{}'", userName, fileName);

        // Fetch metadata first (single HEAD call) to set correct content-type
        FileMetaDataDto metadata = fileService.getFileMetaData(userName, fileName);
        InputStream fileStream = fileService.downloadFile(userName, fileName);

        MediaType mediaType = parseMediaType(metadata.getContentType());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName).build()
        );
        headers.setContentLength(metadata.getSizeBytes());

        return ResponseEntity.ok()
                .headers(headers)
                .body(new InputStreamResource(fileStream));
    }

    @Operation(
            summary = "Get file metadata",
            description = "Returns metadata (size, content-type, last-modified) for a specific file without downloading its content."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metadata returned"),
            @ApiResponse(responseCode = "404", description = "File not found", content = @Content)
    })
    @GetMapping("/{fileName}/metadata")
    public ResponseEntity<FileMetaDataDto> getFileMetadata(
            @PathVariable @NotBlank String userName,
            @PathVariable @NotBlank String fileName
    ) {
        log.info("Metadata request: user='{}', file='{}'", userName, fileName);
        return ResponseEntity.ok(fileService.getFileMetaData(userName, fileName));
    }

    @Operation(
            summary = "Upload a file",
            description = "Uploads a file into the user's S3 folder. The file name is taken from the multipart part. Existing files with the same name are overwritten."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request (empty file, bad name, etc.)", content = @Content),
            @ApiResponse(responseCode = "500", description = "Server or S3 error", content = @Content)
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileMetaDataDto> uploadFile(
            @Parameter(description = "User name (folder owner)", example = "sandy")
            @PathVariable @NotBlank String userName,

            @Parameter(description = "File to upload", required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schema = @Schema(type = "string", format = "binary")))
            @RequestPart("file") MultipartFile file
    ) {
        log.info("Upload request: user='{}', originalName='{}'", userName, file.getOriginalFilename());
        FileMetaDataDto metadata = fileService.uploadFile(userName, file);
        return ResponseEntity.status(201).body(metadata);
    }

    private MediaType parseMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
