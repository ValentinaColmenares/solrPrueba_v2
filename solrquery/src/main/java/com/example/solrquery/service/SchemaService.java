package com.example.solrquery.service;

import org.springframework.http.ResponseEntity;

import com.example.solrquery.dto.CreateCopyFieldsRequest;
import com.example.solrquery.dto.DuplicateFieldsRequest;

public interface SchemaService {
  ResponseEntity<?> duplicateFields(DuplicateFieldsRequest req);
  ResponseEntity<?> createCopyFields(CreateCopyFieldsRequest req);
}
