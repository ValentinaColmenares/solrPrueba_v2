package com.example.solrquery.service;

import org.springframework.http.ResponseEntity;

import com.example.solrquery.dto.QuerySolrRequest;

public interface QuerySolrService {
  ResponseEntity<?> consult(QuerySolrRequest request);
}
