package com.example.solrquery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCopyFieldsRequest {

  @NotBlank(message = "El cliente es obligatorio")
  private String client;

  @NotBlank(message = "La colección origen es obligatoria")
  private String core;

}
