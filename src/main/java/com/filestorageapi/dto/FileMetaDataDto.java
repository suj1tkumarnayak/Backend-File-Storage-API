package com.filestorageapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder
@Schema(description = "Metadata about the stored file")
public class FileMetaDataDto {
    @Schema(description = "The file name without the user-folder prefix", example = "logistics-reports-2024.pdf")
    String fileName;

    @Schema(description = "Full S3 Key for the object", example = "sandy/logistics-reports-2024.pdf")
    String s3Key;

    @Schema(description = "File size in bytes", example = "204800")
    Long sizeBytes;

    @Schema(description = "MIME content type", example = "application/pdf")
    String contentType;

    @Schema(description = "Last modified timestamp (IST)")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Kolkata")
    Instant lastModified;
}
