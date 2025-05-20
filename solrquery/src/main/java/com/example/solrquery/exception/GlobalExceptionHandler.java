package com.example.solrquery.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import jakarta.validation.ConstraintViolationException;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1) Errores de @Valid / @NotBlank / @NotEmpty
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach((FieldError err) -> {
            errors.put(err.getField(), err.getDefaultMessage());
        });
        return ResponseEntity
                .badRequest()
                .body(errors);
    }

    // Sintaxis inválida
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "JSON mal formado: " + ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .badRequest()
                .body(error);
    }

    // 3) Parámetros query / path inválidos (opcional)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(cv ->
            errors.put(cv.getPropertyPath().toString(), cv.getMessage())
        );
        return ResponseEntity
                .badRequest()
                .body(errors);
    }

    // 4) Cualquier otra excepción de tipo BindException (binding de parámetros)
    @ExceptionHandler(org.springframework.validation.BindException.class)
    public ResponseEntity<Map<String, String>> handleBindException(org.springframework.validation.BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
            errors.put(err.getField(), err.getDefaultMessage())
        );
        return ResponseEntity
                .badRequest()
                .body(errors);
    }
}
