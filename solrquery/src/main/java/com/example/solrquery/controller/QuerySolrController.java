package com.example.solrquery.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.solrquery.dto.CopySolrRequest;
import com.example.solrquery.dto.CreateCopyFieldsRequest;
import com.example.solrquery.dto.DuplicateFieldsRequest;
import com.example.solrquery.dto.IndexSolrRequest;
import com.example.solrquery.dto.QuerySolrRequest;
import com.example.solrquery.service.SolrSchemaService;
import com.example.solrquery.service.impl.CopySolrServiceImpl;
import com.example.solrquery.service.impl.CreateCopyFieldsServiceImpl;
import com.example.solrquery.service.impl.DuplicateFieldsServiceImpl;
import com.example.solrquery.service.impl.IndexSolrServiceImpl;
import com.example.solrquery.service.impl.QuerySolrServiceImpl;

import jakarta.validation.Valid;
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
  //private final DuplicateFieldsServiceImpl duplicateFieldsService;
  //private final CreateCopyFieldsServiceImpl createCopyFieldsService;
  private final SolrSchemaService schemaService;

  @PostMapping("/index")
    public ResponseEntity<?> indexSolr(@RequestBody IndexSolrRequest request) {
        return indexSolrService.index(request);
  }

  @PostMapping("/copy")
    public ResponseEntity<?> copySolr(@RequestBody CopySolrRequest request) {
        return copySolrService.copy(request);
    }

  @PostMapping("/consult")
  public ResponseEntity<?> consultSolr(@RequestBody QuerySolrRequest request) {
    return querySolrService.consult(request);
  }

  /*@PostMapping("/duplicateFields")
  public ResponseEntity<?> duplicateFieldsSolr(@RequestBody DuplicateFieldsRequest request){
    return duplicateFieldsService.duplicateFields(request);
  }

  @PostMapping("/createCopyFields")
  public ResponseEntity<?> createCopyFieldsSolr(@RequestBody CreateCopyFieldsRequest request){
    return createCopyFieldsService.createCopyFields(request);
  }*/

  @PostMapping("/duplicateFields")
    public ResponseEntity<?> duplicateFields(
            @Valid @RequestBody DuplicateFieldsRequest req
    ) {
        return schemaService.duplicateFields(req);
    }

    @PostMapping("/createCopyFields")
    public ResponseEntity<?> createCopyFields(
            @Valid @RequestBody CreateCopyFieldsRequest req
    ) {
        return schemaService.createCopyFields(req);
    }
  
}
