package com.example.solrquery.service.impl;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.solrquery.dto.QuerySolrRequest;
import com.example.solrquery.entity.ClientSolr;
import com.example.solrquery.repository.ClientSolrRepository;
import com.example.solrquery.service.QuerySolrService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuerySolrServiceImpl implements QuerySolrService{

    private final ClientSolrRepository clientSolrRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    
    public ResponseEntity<?> consultar(QuerySolrRequest request){

        log.info("JSON recibido: {}", request);

        // Validación cliente
        if (request.getClient() == null || request.getClient().isBlank()) {
            return ResponseEntity.badRequest().body("El cliente es obligatorio.");
        }
        // Validación colección
        if (request.getCore() == null || request.getCore().isBlank()) {
            return ResponseEntity.badRequest().body("La colección es obligatoria.");
        }

        // Validación de cliente en MySQL
        ClientSolr client = clientSolrRepository.findByName(request.getClient())
                .orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Cliente no encontrado: " + request.getClient());
        }

        // Validación de colección en Solr
        if (!coreExistsInSolr(client, request.getCore())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("La colección '" + request.getCore() + "' no existe para el cliente " + request.getClient());
        }

        // Validación sort
        if (!isValidSort(request.getSort())) {
            return ResponseEntity.badRequest().body("El parámetro 'sort' debe tener el formato '<campo> asc' o '<campo> desc'.");
        }

        // Validación start
        if (!isNullOrInteger(request.getStart())) {
            return ResponseEntity.badRequest().body("El parámetro 'start' debe ser un número entero.");
        }
        // Validación rows
        if (!isNullOrInteger(request.getRows())) {
            return ResponseEntity.badRequest().body("El parámetro 'rows' debe ser un número entero.");
        }
        // Validación facet=on y json.facet 
        if (request.getJsonFacet() != null && !request.getJsonFacet().isEmpty()) {
            if (!"on".equalsIgnoreCase(request.getFacet())) {
                return ResponseEntity.badRequest()
                        .body("Si se usa 'json.facet', debe pasar el parámetro 'facet' en 'on'.");
            }
        }
        // Construcción de URL
        String baseUrl = "http://" + client.getIp() + ":" + client.getPuerto() + "/solr/" + request.getCore() + "/select";
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);

        addIfNotBlank(builder, "q", request.getQ());
        addIfNotBlank(builder, "fq", request.getFq());
        addIfNotBlank(builder, "start", request.getStart());
        addIfNotBlank(builder, "rows", request.getRows());
        addIfNotBlank(builder, "fl", request.getFl());
        if ("on".equalsIgnoreCase(request.getFacet())) {
            builder.queryParam("facet", "on");
        }
        if (request.getJsonFacet() != null && !request.getJsonFacet().isEmpty()) {
            builder.queryParam("json.facet", new Gson().toJson(request.getJsonFacet()));
        }

        String finalUrl = builder.build().encode().toUriString();
        if (request.getSort()!=null){
            finalUrl += (finalUrl.contains("?") ? "&" : "?") + "sort=" + request.getSort();
        }
        log.info("URL armada para consulta Solr: {}", finalUrl);
        
        // Consulta a Solr
        ResponseEntity<String> solrResponse;
        try {
            solrResponse = restTemplate.getForEntity(finalUrl, String.class);
        } catch (Exception e) {
            log.error("Error al consultar Solr", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al consultar Solr: " + e.getMessage());
        }

        //log.info("Respuesta de Solr: ", solrResponse.getBody());

        return procesarRespuesta(solrResponse.getBody());
    }
    // Entrega de docs y facets
    private ResponseEntity<?> procesarRespuesta(String solrJson) {
        try {
            JsonObject solrObj = JsonParser.parseString(solrJson).getAsJsonObject();
            JsonObject response = solrObj.getAsJsonObject("response");
            int numFound = response.get("numFound").getAsInt();

            if (numFound == 0) {
                return ResponseEntity.ok("No hay resultados");
            }
            JsonArray docs = response.getAsJsonArray("docs");
            JsonObject facets = solrObj.has("facets") ? solrObj.getAsJsonObject("facets") : null;
            Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
            List<Map<String, Object>> docsList = new Gson().fromJson(docs, listType);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("docs", docsList);
            if (facets != null) {
                result.put("facets", new Gson().fromJson(facets, Map.class));
            }
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error procesando la respuesta de Solr", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error procesando la respuesta de Solr: " + e.getMessage());
        }
    }

    private boolean isValidSort(String sort) {
        if (sort == null || sort.isBlank()) return true; 
        return sort.matches("^[a-zA-Z0-9_.]+\\s+(asc|desc)$");
    }
    
    private boolean isNullOrInteger(String val) {
        if (val == null || val.isBlank()) return true;
        try {
            Integer.parseInt(val);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private void addIfNotBlank(UriComponentsBuilder builder, String key, String value) {
        if (value != null && !value.isBlank()) {
            builder.queryParam(key, value);
        }
    }
    // Validación de colección en Solr
    private boolean coreExistsInSolr(ClientSolr client, String core) {
        String url = "http://" + client.getIp() + ":" + client.getPuerto() + "/solr/admin/cores?action=STATUS";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonObject json = JsonParser.parseString(response.getBody()).getAsJsonObject();
                JsonObject status = json.getAsJsonObject("status");
                return status.has(core);
            }
        } catch (Exception e) {
            log.error("Error consultando coleccion de Solr", e);
        }
        return false;
    }
    
}