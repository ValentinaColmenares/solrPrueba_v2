package com.example.solrquery.service;

import org.springframework.http.ResponseEntity;

import com.example.solrquery.dto.CopySolrRequest;

public interface CopySolrService {
  ResponseEntity<?> copy(CopySolrRequest request);
}
