package com.receipts.common;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import com.receipts.storage.StorageException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Object> handleApi(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(e.body());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleVersionConflict(OptimisticLockingFailureException e) {
        return error(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Transaction was changed by another request. Reload and try again");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException e) {
        FieldError field = e.getBindingResult().getFieldError();
        String message = field == null
                ? "Request body is not valid"
                : toSnakeCase(field.getField()) + " " + field.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body is missing or not valid JSON");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleBadPathValue(MethodArgumentTypeMismatchException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getName() + " must be a number");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleTooLarge(MaxUploadSizeExceededException e) {
        return error(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "File is larger than 10 MB");
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ApiError> handleBadMultipart(MultipartException e) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "File is missing or empty");
    }

    @ExceptionHandler(StorageException.class)
    ResponseEntity<ApiError> handleStorage(StorageException e) {
        log.error("File storage error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR", "File storage error");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleOther(Exception e) {
        if (e instanceof ErrorResponse springError) {
            HttpStatusCode status = springError.getStatusCode();
            String code = HttpStatus.resolve(status.value()) == null ? "ERROR" : HttpStatus.valueOf(status.value()).name();
            return ResponseEntity.status(status).body(new ApiError(code, e.getMessage()));
        }
        log.error("Unexpected error", e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error");
    }

    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }

    private static String toSnakeCase(String path) {
        return path.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
