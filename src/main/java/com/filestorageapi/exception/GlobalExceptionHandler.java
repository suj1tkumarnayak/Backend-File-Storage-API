package com.filestorageapi.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String TIMESTAMP_PROPERTY = "timestamp";

    @ExceptionHandler(FileNotFoundException.class)
    public ProblemDetail handleFileNotFound(FileNotFoundException ex){
        log.warn("File not found: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.NOT_FOUND, "File Not Found", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgumentException(IllegalArgumentException ex){
        log.warn("Bad request: {}", ex.getMessage());
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "Validation Failed", ex.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(ConstraintViolationException ex){
        log.warn("Bad request: {}", ex.getMessage());
        String detail = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(ex.getMessage());
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "Validation Failed", detail);
    }

    @ExceptionHandler(S3Exception.class)
    public ProblemDetail handleS3Exception(S3Exception ex) {
        log.error("AWS S3 error [{}]: {}", ex.statusCode(), ex.awsErrorDetails().errorMessage(), ex);
        return buildProblemDetail(
                HttpStatus.valueOf(ex.statusCode()),
                "S3 Storage Error",
                ex.awsErrorDetails().errorMessage()
        );
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Method argument not valid: {}", ex.getMessage());
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "Validation Failed", detail);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = String.format("Parameter '%s' has invalid value: %s", ex.getName(), ex.getValue());
        return buildProblemDetail(HttpStatus.BAD_REQUEST, "Type Mismatch", detail);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return buildProblemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                "An unexpected error occurred. Please try again later.");
    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParams(MissingServletRequestParameterException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing"
        );
        problem.setTitle("Bad Request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }
    // helpers
    private ProblemDetail buildProblemDetail(HttpStatus status, String title, String detail){
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setType(URI.create("about:blank"));
        pd.setProperty(TIMESTAMP_PROPERTY, Instant.now());
        return pd;
    }
}
