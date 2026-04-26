package com.filestorageapi.controller;

/**
 * =============================================================================
 * IMPORT FIX FOR SPRING BOOT 4.x
 * =============================================================================
 *
 * Two imports changed in Spring Boot 4.x / Spring Framework 7.x:
 *
 * BEFORE (Spring Boot 3.x):
 *   import org.springframework.boot.test.mock.mockito.MockBean;
 *   import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
 *
 * AFTER (Spring Boot 4.x):
 *   import org.springframework.test.context.bean.override.mockito.MockitoBean;
 *   import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;  ← same
 *
 * @MockBean was in spring-boot-test and is now REMOVED.
 * @MockitoBean is the direct replacement, now in spring-test (core Spring).
 *
 * WebMvcTest itself didn't move — that import is fine as-is.
 * =============================================================================
 */

import com.filestorageapi.controller.FileController;
import com.filestorageapi.dto.FileMetaDataDto;
import com.filestorageapi.dto.SearchResponseDto;
import com.filestorageapi.exception.FileNotFoundException;
import com.filestorageapi.exception.GlobalExceptionHandler;
import com.filestorageapi.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FileController.class)
@Import(GlobalExceptionHandler.class)
@DisplayName("FileController web layer tests")
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ✅ @MockitoBean replaces @MockBean in Spring Boot 4.x
    @MockitoBean
    private FileService fileService;

    private static final String BASE = "/api/v1/users/{userName}/files";

    // =========================================================================
    // GET /search
    // =========================================================================
    @Nested
    @DisplayName("GET /search")
    class SearchEndpoint {

        @Test
        @DisplayName("200 OK with matching files")
        void returns200WithResults() throws Exception {
            FileMetaDataDto file = FileMetaDataDto.builder()
                    .fileName("logistics-report.pdf")
                    .s3Key("sandy/logistics-report.pdf")
                    .sizeBytes(1024L)
                    .contentType("application/pdf")
                    .lastModified(Instant.now())
                    .build();

            SearchResponseDto response = SearchResponseDto.builder()
                    .userName("sandy")
                    .searchTerm("logistics")
                    .totalResults(1)
                    .files(List.of(file))
                    .build();

            when(fileService.searchFiles("sandy", "logistics")).thenReturn(response);

            mockMvc.perform(get(BASE + "/search", "sandy")
                            .param("term", "logistics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userName").value("sandy"))
                    .andExpect(jsonPath("$.searchTerm").value("logistics"))
                    .andExpect(jsonPath("$.totalResults").value(1))
                    .andExpect(jsonPath("$.files", hasSize(1)))
                    .andExpect(jsonPath("$.files[0].fileName").value("logistics-report.pdf"));
        }

        @Test
        @DisplayName("200 OK with empty list when no files match")
        void returns200WithEmptyList() throws Exception {
            SearchResponseDto empty = SearchResponseDto.builder()
                    .userName("sandy").searchTerm("xyz").totalResults(0).files(List.of())
                    .build();
            when(fileService.searchFiles("sandy", "xyz")).thenReturn(empty);

            mockMvc.perform(get(BASE + "/search", "sandy").param("term", "xyz"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalResults").value(0));
        }

        @Test
        @DisplayName("400 Bad Request when term param is missing")
        void returns400WhenTermMissing() throws Exception {
            mockMvc.perform(get(BASE + "/search", "sandy"))
                    .andExpect(status().isBadRequest());
        }
    }

    // =========================================================================
    // GET /{fileName} — download
    // =========================================================================
    @Nested
    @DisplayName("GET /{fileName} — download")
    class DownloadEndpoint {

        @Test
        @DisplayName("200 OK with correct headers")
        void returns200WithFileStream() throws Exception {
            FileMetaDataDto meta = FileMetaDataDto.builder()
                    .fileName("Infosys Hiring- Sample Questions.pdf")
                    .s3Key("sandy/Infosys Hiring- Sample Questions.pdf")
                    .sizeBytes(7L)
                    .contentType("application/pdf")
                    .lastModified(Instant.now())
                    .build();

            when(fileService.getFileMetaData("sandy", "Infosys Hiring- Sample Questions.pdf")).thenReturn(meta);
            when(fileService.downloadFile("sandy", "Infosys Hiring- Sample Questions.pdf"))
                    .thenReturn(new ByteArrayInputStream("content".getBytes()));

            mockMvc.perform(get(BASE + "/{fileName}", "sandy", "Infosys Hiring- Sample Questions.pdf"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Disposition",
                            containsString("Infosys Hiring- Sample Questions.pdf")))
                    .andExpect(content().contentType("application/pdf"));
        }

        @Test
        @DisplayName("404 Not Found when file does not exist")
        void returns404WhenFileNotFound() throws Exception {
            when(fileService.getFileMetaData("sandy", "ghost.pdf"))
                    .thenThrow(new FileNotFoundException("sandy", "ghost.pdf"));

            mockMvc.perform(get(BASE + "/{fileName}", "sandy", "ghost.pdf"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("File Not Found"));
        }
    }

    // =========================================================================
    // GET /{fileName}/metadata
    // =========================================================================
    @Nested
    @DisplayName("GET /{fileName}/metadata")
    class MetadataEndpoint {

        @Test
        @DisplayName("200 OK with metadata fields")
        void returns200WithMetadata() throws Exception {
            FileMetaDataDto meta = FileMetaDataDto.builder()
                    .fileName("report.pdf")
                    .s3Key("sandy/report.pdf")
                    .sizeBytes(2048L)
                    .contentType("application/pdf")
                    .lastModified(Instant.parse("2024-03-01T10:00:00Z"))
                    .build();

            when(fileService.getFileMetaData("sandy", "report.pdf")).thenReturn(meta);

            mockMvc.perform(get(BASE + "/{fileName}/metadata", "sandy", "report.pdf"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.fileName").value("report.pdf"))
                    .andExpect(jsonPath("$.sizeBytes").value(2048))
                    .andExpect(jsonPath("$.contentType").value("application/pdf"));
        }

        @Test
        @DisplayName("404 when file does not exist")
        void returns404WhenFileNotFound() throws Exception {
            when(fileService.getFileMetaData("sandy", "missing.pdf"))
                    .thenThrow(new FileNotFoundException("sandy", "missing.pdf"));

            mockMvc.perform(get(BASE + "/{fileName}/metadata", "sandy", "missing.pdf"))
                    .andExpect(status().isNotFound());
        }
    }

    // =========================================================================
    // POST / — upload
    // =========================================================================
    @Nested
    @DisplayName("POST / — upload")
    class UploadEndpoint {

        @Test
        @DisplayName("201 Created with metadata in response body")
        void returns201OnSuccess() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "logistics-data.csv", "text/csv", "a,b\n1,2".getBytes()
            );

            FileMetaDataDto meta = FileMetaDataDto.builder()
                    .fileName("logistics-data.csv")
                    .s3Key("sandy/logistics-data.csv")
                    .sizeBytes(7L)
                    .contentType("text/csv")
                    .lastModified(Instant.now())
                    .build();

            when(fileService.uploadFile(eq("sandy"), any())).thenReturn(meta);

            mockMvc.perform(multipart(BASE, "sandy").file(file))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fileName").value("logistics-data.csv"))
                    .andExpect(jsonPath("$.s3Key").value("sandy/logistics-data.csv"));
        }

        @Test
        @DisplayName("400 Bad Request when service rejects the file")
        void returns400OnBadRequest() throws Exception {
            MockMultipartFile empty = new MockMultipartFile(
                    "file", "empty.txt", "text/plain", new byte[0]
            );
            when(fileService.uploadFile(eq("sandy"), any()))
                    .thenThrow(new IllegalArgumentException("Uploaded file must not be empty"));

            mockMvc.perform(multipart(BASE, "sandy").file(empty))
                    .andExpect(status().isBadRequest());
        }
    }
}