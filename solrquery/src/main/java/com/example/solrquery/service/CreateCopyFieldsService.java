package com.example.solrquery.service;

import org.springframework.http.ResponseEntity;

import com.example.solrquery.dto.CreateCopyFieldsRequest;

public interface CreateCopyFieldsService {
  ResponseEntity<?> createCopyFields(CreateCopyFieldsRequest request);
}
