package org.example.web;

import org.example.application.NotFoundException;
import org.example.infrastructure.moex.MoexClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Сбой обращения к MOEX → 502 Bad Gateway. */
    @ExceptionHandler(MoexClientException.class)
    public ResponseEntity<Map<String, String>> handleMoex(MoexClientException ex) {
        logger.warn("Ошибка MOEX: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    /** Нет статического ресурса (например, GET / или favicon.ico) → тихий 404, без стектрейса. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, String>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Ресурс не найден: " + ex.getResourcePath()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return badRequest(ex.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class})
    public ResponseEntity<Map<String, String>> handleValidation(Exception ex) {
        String message = ex instanceof MethodArgumentNotValidException manv && manv.getFieldError() != null
                ? manv.getFieldError().getField() + ": " + manv.getFieldError().getDefaultMessage()
                : ex.getMessage();
        return badRequest(message);
    }

    /**
     * {@code Map.of} падает с NPE на null-значении, а исключение вполне может прийти без сообщения —
     * тогда вместо 400 клиент получал бы 500 из самого обработчика.
     */
    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", message == null ? "Некорректный запрос" : message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        logger.error("Непредвиденная ошибка", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Внутренняя ошибка сервера"));
    }
}
