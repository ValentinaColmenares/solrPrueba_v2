package com.example.solrquery.service;

import org.springframework.http.ResponseEntity;

import com.example.solrquery.dto.DuplicateFieldsRequest;

public interface DuplicateFieldsService {
  ResponseEntity<?> duplicateFields(DuplicateFieldsRequest request);
}
