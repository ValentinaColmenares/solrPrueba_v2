package com.example.solrquery.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.solrquery.dto.CopySolrRequest;
import com.example.solrquery.dto.IndexSolrRequest;
import com.example.solrquery.dto.QuerySolrRequest;
import com.example.solrquery.service.impl.CopySolrServiceImpl;
import com.example.solrquery.service.impl.IndexSolrServiceImpl;
import com.example.solrquery.service.impl.QuerySolrServiceImpl;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController
@RequestMapping("api/solr")
@RequiredArgsConstructor
public class QuerySolrController {

  private final IndexSolrServiceImpl indexSolrService;
  private final QuerySolrServiceImpl querySolrService;
  private final CopySolrServiceImpl copySolrService;

  @PostMapping("/index")
    public ResponseEntity<?> indexarSolr(@RequestBody IndexSolrRequest request) {
        return indexSolrService.indexar(request);
  }

  @PostMapping("/copy")
    public ResponseEntity<?> copyDocuments(@RequestBody CopySolrRequest request) {
        return copySolrService.copiar(request);
    }

  @PostMapping("/consultar")
  public ResponseEntity<?> consultarSolr(@RequestBody QuerySolrRequest request) {
    return querySolrService.consultar(request);
  }
  
}
