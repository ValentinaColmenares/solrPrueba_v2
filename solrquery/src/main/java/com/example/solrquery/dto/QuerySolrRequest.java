package com.example.solrquery.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class QuerySolrRequest {
  
    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String client;
    
    @NotBlank(message = "La colección es obligatoria")
    private String core;

    private String q;
    private String fq;
    private String sort;
    private String start;
    private String rows;
    private String fl;
    private String facet;
    private Map<String, Object> jsonFacet;
}
