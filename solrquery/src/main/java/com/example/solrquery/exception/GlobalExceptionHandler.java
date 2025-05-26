package com.example.solrquery.exception;

import org.springframework.http.HttpStatus;
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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidQueryParam(IllegalArgumentException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("Invalid character")) {
            Map<String,String> error = new LinkedHashMap<>();
            error.put("error", "Parámetro de consulta inválido: " + msg);
            return ResponseEntity
                    .badRequest()
                    .body(error);
        }
        Map<String,String> fallback = new LinkedHashMap<>();
        fallback.put("error", "Error en la petición: " + msg);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(fallback);
    }

}
