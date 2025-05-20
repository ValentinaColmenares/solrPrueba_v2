package com.example.solrquery.dto;

import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class IndexSolrRequest {

  @NotBlank(message = "El nombre del cliente es obligatorio")
  private String client;

  @NotBlank(message = "La colección es obligatoria")
  private String core;

  @NotEmpty(message = "Debe proveer al menos un documento para indexar")
  private List<Map<String, Object>> docs;

}
