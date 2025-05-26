package com.example.solrquery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DuplicateFieldsRequest {
  
  @NotBlank(message = "El cliente es obligatorio")
  private String client;

  @NotBlank(message = "La colección origen es obligatoria")
  private String sourceCore;

  @NotBlank(message = "La colección destino es obligatoria")
  private String targetCore;

}
