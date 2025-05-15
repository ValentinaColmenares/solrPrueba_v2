package com.example.solrquery.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequestsDto {
  
    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String client;

    @NotBlank(message = "La colección es obligatoria")
    private String core;

    private String filter;

}
