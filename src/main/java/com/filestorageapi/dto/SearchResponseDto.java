package com.filestorageapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder; //builder is used for creating new objects of class
import lombok.Value; // creating getters setters final fields

import java.util.List;

@Value
@Builder
@Schema(description = "Response for a file search request")
public class SearchResponseDto {
    @Schema(description = "User whose folder was searched", example = "sandy")
    String userName;

    @Schema(description = "search term used", example = "logistics")
    String searchTerm;

    @Schema(description = "Total noof files matched", example = "3")
    int totalResults;

    @Schema(description = "List of matched files metadata")
    List<FileMetaDataDto> files;
}
