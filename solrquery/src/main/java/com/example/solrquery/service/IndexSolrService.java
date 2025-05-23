package com.example.solrquery.service;

import org.springframework.http.ResponseEntity;

import com.example.solrquery.dto.IndexSolrRequest;

public interface IndexSolrService {
    ResponseEntity<?> index(IndexSolrRequest request);
}
