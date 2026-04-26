package com.filestorageapi.service;

/**
 * =============================================================================
 * HOW THIS TEST CLASS WORKS — READ THIS FIRST
 * =============================================================================
 *
 * WHAT IS A UNIT TEST?
 *   A unit test checks ONE class in isolation. It does NOT connect to AWS,
 *   does NOT start Spring, and does NOT need internet. It runs in milliseconds.
 *
 * WHAT IS MOCKING?
 *   S3FileService depends on S3StorageRepository. Instead of using the real
 *   repository (which would call AWS), we create a FAKE version with Mockito.
 *   We tell the fake: "when listObjects('sandy/') is called, return THIS list".
 *   Then we check that the service does the right thing with that list.
 *
 * ANNOTATIONS USED:
 *   @ExtendWith(MockitoExtension.class) — activates Mockito for this class
 *   @Mock        — creates a fake (mock) of S3StorageRepository
 *   @InjectMocks — creates a real S3FileService and injects the mock into it
 *   @Test        — marks a method as a test case
 *   @DisplayName — human-readable name shown in the test report
 *   @Nested      — groups related tests together (search tests, upload tests, etc.)
 * =============================================================================
 */

import com.filestorageapi.dto.FileMetaDataDto;
import com.filestorageapi.dto.SearchResponseDto;
import com.filestorageapi.exception.FileNotFoundException;
import com.filestorageapi.repository.S3StorageRepository;
import com.filestorageapi.service.S3FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileService unit tests")
class S3FileServiceTest {

    @Mock
    private S3StorageRepository storageRepository;

    @InjectMocks
    private S3FileService service;

    private static final String USER   = "sandy";
    private static final String PREFIX = "sandy/";

    // =========================================================================
    // GROUP 1: searchFiles() tests
    // =========================================================================
    @Nested
    @DisplayName("searchFiles()")
    class SearchFilesTests {

        @Test
        @DisplayName("returns matching files when search term found in file name")
        void returnsMatchedFiles() {
            List<S3Object> fakeS3Objects = List.of(
                    makeS3Object("sandy/logistics-report-2024.pdf", 1024L),
                    makeS3Object("sandy/logistics-invoice.xlsx",    2048L),
                    makeS3Object("sandy/salary-slip.pdf",            512L)  // should NOT match
            );
            when(storageRepository.listObjects(PREFIX)).thenReturn(fakeS3Objects);

            SearchResponseDto result = service.searchFiles(USER, "logistics");

            assertThat(result.getUserName()).isEqualTo("sandy");
            assertThat(result.getSearchTerm()).isEqualTo("logistics");
            assertThat(result.getTotalResults()).isEqualTo(2);
            assertThat(result.getFiles())
                    .extracting(FileMetaDataDto::getFileName)
                    .containsExactlyInAnyOrder(
                            "logistics-report-2024.pdf",
                            "logistics-invoice.xlsx"
                    );
        }

        @Test
        @DisplayName("search is case-insensitive — 'LOGISTICS' matches 'logistics'")
        void searchIsCaseInsensitive() {
            when(storageRepository.listObjects(PREFIX)).thenReturn(List.of(
                    makeS3Object("sandy/LOGISTICS-REPORT.pdf", 100L),
                    makeS3Object("sandy/salary.pdf",           100L)
            ));

            SearchResponseDto result = service.searchFiles(USER, "logistics");

            assertThat(result.getTotalResults()).isEqualTo(1);
            assertThat(result.getFiles().get(0).getFileName()).isEqualTo("LOGISTICS-REPORT.pdf");
        }

        @Test
        @DisplayName("returns empty list when no files match — totalResults is 0")
        void returnsEmptyListWhenNoMatch() {
            when(storageRepository.listObjects(PREFIX)).thenReturn(
                    List.of(makeS3Object("sandy/salary-slip.pdf", 512L))
            );

            SearchResponseDto result = service.searchFiles(USER, "logistics");

            assertThat(result.getTotalResults()).isZero();
            assertThat(result.getFiles()).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when user folder is completely empty")
        void returnsEmptyWhenBucketEmpty() {
            when(storageRepository.listObjects(PREFIX)).thenReturn(List.of());

            SearchResponseDto result = service.searchFiles(USER, "logistics");

            assertThat(result.getTotalResults()).isZero();
        }

        @Test
        @DisplayName("skips S3 folder placeholder objects (keys ending with /)")
        void skipsFolderPlaceholderObjects() {
            when(storageRepository.listObjects(PREFIX)).thenReturn(List.of(
                    makeS3Object("sandy/",                   0L),   // folder placeholder — skip
                    makeS3Object("sandy/logistics-file.pdf", 100L)  // real file — keep
            ));

            SearchResponseDto result = service.searchFiles(USER, "logistics");

            assertThat(result.getTotalResults()).isEqualTo(1);
        }

        @Test
        @DisplayName("calls listObjects exactly once with the user-scoped prefix")
        void callsListObjectsOnceWithCorrectPrefix() {
            when(storageRepository.listObjects(PREFIX)).thenReturn(List.of());

            service.searchFiles(USER, "anything");

            verify(storageRepository, times(1)).listObjects(PREFIX);
            verifyNoMoreInteractions(storageRepository);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("throws IllegalArgumentException when userName is blank/null")
        void throwsForBlankUserName(String blankName) {
            assertThatThrownBy(() -> service.searchFiles(blankName, "term"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("throws IllegalArgumentException when searchTerm is blank/null")
        void throwsForBlankSearchTerm(String blankTerm) {
            assertThatThrownBy(() -> service.searchFiles(USER, blankTerm))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // =========================================================================
    // GROUP 2: downloadFile() tests
    // =========================================================================
    @Nested
    @DisplayName("downloadFile()")
    class DownloadFileTests {

        @Test
        @DisplayName("returns InputStream when file exists in S3")
        void returnsInputStreamWhenFileExists() {
            String key = "sandy/logistics-report.pdf";
            InputStream expectedStream = new ByteArrayInputStream("pdf content".getBytes());

            when(storageRepository.objectExists(key)).thenReturn(true);
            when(storageRepository.downloadObject(key)).thenReturn(expectedStream);

            InputStream result = service.downloadFile(USER, "logistics-report.pdf");

            assertThat(result).isSameAs(expectedStream);
            verify(storageRepository).downloadObject(key);
        }

        @Test
        @DisplayName("throws FileNotFoundException when file does not exist")
        void throwsWhenFileNotFound() {
            when(storageRepository.objectExists("sandy/ghost.pdf")).thenReturn(false);

            assertThatThrownBy(() -> service.downloadFile(USER, "ghost.pdf"))
                    .isInstanceOf(FileNotFoundException.class)
                    .hasMessageContaining("ghost.pdf")
                    .hasMessageContaining("sandy");
        }

        @Test
        @DisplayName("does NOT call downloadObject when file does not exist")
        void doesNotDownloadWhenFileAbsent() {
            when(storageRepository.objectExists(any())).thenReturn(false);

            assertThatThrownBy(() -> service.downloadFile(USER, "ghost.pdf"))
                    .isInstanceOf(FileNotFoundException.class);

            verify(storageRepository, never()).downloadObject(any());
        }
    }

    // =========================================================================
    // GROUP 3: getFileMetaData() tests
    // =========================================================================
    @Nested
    @DisplayName("getFileMetaData()")
    class GetFileMetaDataTests {

        @Test
        @DisplayName("returns correctly populated DTO when file exists")
        void returnsMetadataWhenFileExists() {
            String key = "sandy/report.pdf";
            Instant now = Instant.now();

            HeadObjectResponse fakeHead = HeadObjectResponse.builder()
                    .contentLength(4096L)
                    .contentType("application/pdf")
                    .lastModified(now)
                    .build();

            when(storageRepository.objectExists(key)).thenReturn(true);
            when(storageRepository.headObject(key)).thenReturn(fakeHead);

            FileMetaDataDto dto = service.getFileMetaData(USER, "report.pdf");

            assertThat(dto.getFileName()).isEqualTo("report.pdf");
            assertThat(dto.getS3Key()).isEqualTo(key);
            assertThat(dto.getSizeBytes()).isEqualTo(4096L);
            assertThat(dto.getContentType()).isEqualTo("application/pdf");
            assertThat(dto.getLastModified()).isEqualTo(now);
        }

        @Test
        @DisplayName("falls back to 'application/octet-stream' when S3 has no content-type")
        void fallsBackToOctetStreamWhenNoContentType() {
            String key = "sandy/unknown-file";
            when(storageRepository.objectExists(key)).thenReturn(true);
            when(storageRepository.headObject(key)).thenReturn(
                    HeadObjectResponse.builder()
                            .contentLength(100L)
                            .contentType(null)
                            .lastModified(Instant.now())
                            .build()
            );

            FileMetaDataDto dto = service.getFileMetaData(USER, "unknown-file");

            assertThat(dto.getContentType()).isEqualTo("application/octet-stream");
        }

        @Test
        @DisplayName("throws FileNotFoundException when file does not exist")
        void throwsWhenFileNotFound() {
            when(storageRepository.objectExists("sandy/ghost.txt")).thenReturn(false);

            assertThatThrownBy(() -> service.getFileMetaData(USER, "ghost.txt"))
                    .isInstanceOf(FileNotFoundException.class);
        }
    }

    // =========================================================================
    // GROUP 4: uploadFile() tests
    // =========================================================================
    @Nested
    @DisplayName("uploadFile()")
    class UploadFileTests {

        @Test
        @DisplayName("uploads file to correct S3 key and returns metadata")
        void uploadsFileAndReturnsMetadata() {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file",
                    "logistics-data.csv",
                    "text/csv",
                    "col1,col2\n1,2".getBytes()
            );

            FileMetaDataDto result = service.uploadFile(USER, multipartFile);

            verify(storageRepository).uploadObject(
                    eq("sandy/logistics-data.csv"),
                    any(InputStream.class),
                    eq("text/csv"),
                    eq(multipartFile.getSize())
            );

            assertThat(result.getFileName()).isEqualTo("logistics-data.csv");
            assertThat(result.getS3Key()).isEqualTo("sandy/logistics-data.csv");
            assertThat(result.getContentType()).isEqualTo("text/csv");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for empty file")
        void throwsForEmptyFile() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "empty.txt", "text/plain", new byte[0]
            );

            assertThatThrownBy(() -> service.uploadFile(USER, emptyFile))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("empty");
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null file")
        void throwsForNullFile() {
            assertThatThrownBy(() -> service.uploadFile(USER, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("uses 'application/octet-stream' when content-type is not provided")
        void fallsBackToOctetStreamContentType() {
            MockMultipartFile fileWithNoContentType = new MockMultipartFile(
                    "file", "data.bin",
                    null,
                    "binarydata".getBytes()
            );

            service.uploadFile(USER, fileWithNoContentType);

            verify(storageRepository).uploadObject(
                    eq("sandy/data.bin"),
                    any(InputStream.class),
                    eq("application/octet-stream"),
                    anyLong()
            );
        }
    }

    // =========================================================================
    // Helper
    // =========================================================================
    private S3Object makeS3Object(String key, long size) {
        return S3Object.builder()
                .key(key)
                .size(size)
                .lastModified(Instant.now())
                .build();
    }
}
