package com.example.solrquery.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.util.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Sintaxis inválida
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(HttpMessageNotReadableException ex) {
        Map<String, String> error = new HashMap<>();
        error.put("error", "JSON mal formado: " + ex.getMostSpecificCause().getMessage());
        return ResponseEntity
                .badRequest()
                .body(error);
    }

}
