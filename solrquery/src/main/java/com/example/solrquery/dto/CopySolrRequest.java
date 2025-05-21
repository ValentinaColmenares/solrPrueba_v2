package com.example.solrquery.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class CopySolrRequest {

    @NotBlank(message = "El nombre del cliente es obligatorio")
    private String client;

    @NotBlank(message = "La colección origen es obligatoria")
    private String sourceCore;

    @NotBlank(message = "La colección destino es obligatoria")
    private String targetCore;

    private String q;
    private String fq;
    private String sort;
    private String fl;
    private String start;
    private String rows;
}
