package com.filestorageapi.repository;

/**
 * =============================================================================
 * HOW THIS TEST CLASS WORKS
 * =============================================================================
 *
 * This tests the repository directly. The repository's only job is to make
 * the right S3 API calls. We mock the S3Client (from AWS SDK) so no real
 * network call is ever made.
 *
 * KEY TECHNIQUE — ArgumentCaptor:
 *   When we call repository.uploadObject(...), the repository internally builds
 *   a PutObjectRequest and passes it to s3Client.putObject(...).
 *   We can't see that internal request from outside — but ArgumentCaptor
 *   captures it so we can inspect its fields (bucket name, key, content-type).
 *
 * ReflectionTestUtils.setField:
 *   The repository has a @Value("${aws.s3.bucket-name}") field that Spring
 *   normally injects. In a plain unit test (no Spring), we use ReflectionTestUtils
 *   to set it manually.
 * =============================================================================
 */

import com.filestorageapi.repository.S3StorageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3StorageRepository unit tests")
class S3StorageRepositoryTest {

    @Mock
    private S3Client s3Client;

    private S3StorageRepository repository;

    private static final String BUCKET = "test-bucket";

    @BeforeEach
    void setUp() {
        repository = new S3StorageRepository(s3Client);
        ReflectionTestUtils.setField(repository, "bucketName", BUCKET);
    }

    // =========================================================================
    // listObjects — pagination
    // =========================================================================

    @Test
    @DisplayName("listObjects returns all objects across multiple pages (pagination)")
    void listObjectsPaginatesCorrectly() {
        S3Object obj1 = makeS3Object("sandy/file1.pdf");
        S3Object obj2 = makeS3Object("sandy/file2.pdf");

        ListObjectsV2Response page1 = ListObjectsV2Response.builder()
                .contents(obj1)
                .isTruncated(true)
                .nextContinuationToken("token-page2")
                .build();

        ListObjectsV2Response page2 = ListObjectsV2Response.builder()
                .contents(obj2)
                .isTruncated(false)
                .build();

        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(page1)
                .thenReturn(page2);

        List<S3Object> result = repository.listObjects("sandy/");

        assertThat(result).hasSize(2).contains(obj1, obj2);
        verify(s3Client, times(2)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    @Test
    @DisplayName("listObjects returns single page result without extra API call")
    void listObjectsSinglePage() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder()
                        .contents(makeS3Object("sandy/file.pdf"))
                        .isTruncated(false)
                        .build());

        List<S3Object> result = repository.listObjects("sandy/");

        assertThat(result).hasSize(1);
        verify(s3Client, times(1)).listObjectsV2(any(ListObjectsV2Request.class));
    }

    // =========================================================================
    // objectExists
    // =========================================================================

    @Test
    @DisplayName("objectExists returns true when HeadObject succeeds")
    void objectExistsTrue() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(HeadObjectResponse.builder().build());

        assertThat(repository.objectExists("sandy/file.pdf")).isTrue();
    }

    @Test
    @DisplayName("objectExists returns false when NoSuchKeyException is thrown")
    void objectExistsFalse() {
        when(s3Client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().message("no such key").build());

        assertThat(repository.objectExists("sandy/ghost.pdf")).isFalse();
    }

    // =========================================================================
    // uploadObject — verify correct request fields
    // =========================================================================

    @Test
    @DisplayName("uploadObject sends correct bucket, key, and content-type to S3")
    void uploadObjectSendsCorrectRequest() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        repository.uploadObject(
                "sandy/report.csv",
                new ByteArrayInputStream("data".getBytes()),
                "text/csv",
                4L
        );

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));

        PutObjectRequest captured = captor.getValue();
        assertThat(captured.bucket()).isEqualTo(BUCKET);
        assertThat(captured.key()).isEqualTo("sandy/report.csv");
        assertThat(captured.contentType()).isEqualTo("text/csv");
        assertThat(captured.contentLength()).isEqualTo(4L);
    }

    // =========================================================================
    // Helper
    // =========================================================================
    private S3Object makeS3Object(String key) {
        return S3Object.builder()
                .key(key)
                .size(1024L)
                .lastModified(Instant.now())
                .build();
    }
}
