package com.example.solrquery.service.impl;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.solrquery.dto.IndexSolrRequest;
import com.example.solrquery.entity.ClientSolr;
import com.example.solrquery.repository.ClientSolrRepository;
import com.example.solrquery.service.IndexSolrService;
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
public class IndexSolrServiceImpl implements IndexSolrService{

    private final ClientSolrRepository clientSolrRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    public ResponseEntity<?> index(IndexSolrRequest request) {
        log.info("JSON recibido para indexar: {}", request);
        
        // Validación ingreso de cliente
        if (request.getClient() == null || request.getClient().isBlank()) {
            return ResponseEntity.badRequest().body("El cliente es obligatorio.");
        }

        // Validación de cliente en MySQL
        ClientSolr client = clientSolrRepository.findByName(request.getClient())
                .orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Cliente no encontrado: " + request.getClient());
        }

        // Validación ingreso de colección
        if (request.getCore() == null || request.getCore().isBlank()) {
            return ResponseEntity.badRequest().body("La colección es obligatoria.");
        }

        // Validación de colección en Solr
        if (!coreExistsInSolr(client, request.getCore())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body("La colección '" + request.getCore() + "' no existe para el cliente " + request.getClient());
        }

        // Validación ingreso de colección
        if (request.getDocs() == null || request.getDocs().isEmpty()) {
            return ResponseEntity.badRequest().body("Debe proveer al menos un documento para indexar");
        }

        // Validación esquema de campos de Solr
        Map<String, String> schemaFields = fetchSolrSchemaFields(client, request.getCore());
        if (schemaFields.isEmpty()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("No se pudo obtener el esquema de campos de Solr");
        }

        // Validación de campos y tipos
        for (int i = 0; i < request.getDocs().size(); i++) {
            Map<String, Object> doc = request.getDocs().get(i);
            for (Map.Entry<String, Object> entry : doc.entrySet()) {
                String field = entry.getKey();
                Object value = entry.getValue();

                if(schemaFields.containsKey(field)){
                    String solrType = schemaFields.get(field);
                    if (!isValidType(value, solrType)) {
                        return ResponseEntity.badRequest()
                                .body("Documento " + (i+1) + ": valor '" + value +
                                    "' no concuerda con el tipo '" + solrType + "' de campo '" + field + "'");
                    }
                }
            }
        }

        // Indexación 
        String updateUrl = "http://" + client.getIp()
                         + ":" + client.getPort()
                         + "/solr/" + request.getCore()
                         + "/update?commit=true";
        log.info("URL para indexar en Solr: {}", updateUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<Map<String, Object>>> entity =
                new HttpEntity<>(request.getDocs(), headers);

        ResponseEntity<String> solrResp;
        try {
            solrResp = restTemplate.postForEntity(updateUrl, entity, String.class);
        } catch (Exception e) {
            log.error("Error al indexar documentos en Solr", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al indexar en Solr: " + e.getMessage());
        }

        log.info("Respuesta de Solr al indexar: {}", solrResp.getBody());

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("message", "Documentos indexados con éxito");
        result.put("docs", request.getDocs());
        return ResponseEntity.ok(result);
    }

    // Validación de colección en Solr
    private boolean coreExistsInSolr(ClientSolr client, String core) {
        String url = "http://" + client.getIp() + ":" + client.getPort() + "/solr/admin/cores?action=STATUS";
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

    // Construcción HashMap de campos del esquema
    private Map<String, String> fetchSolrSchemaFields(ClientSolr client, String core) {
        String url = "http://" + client.getIp() + ":" + client.getPort()
                   + "/solr/" + core + "/schema/fields";
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonArray arr = root.getAsJsonArray("fields");

            Type listType = new TypeToken<List<JsonObject>>(){}.getType();
            List<JsonObject> fields = gson.fromJson(arr, listType);

            Map<String,String> map = new HashMap<>();
            for (JsonObject f : fields) {
                String name = f.get("name").getAsString();
                String type = f.get("type").getAsString();
                map.put(name, type);
            }
            log.info("Campos del esquema de Solr para coleccion '{}': {}", core, map);
            return map;
        } catch (Exception e) {
            log.error("Error obteniendo esquema de campos de Solr", e);
            return Collections.emptyMap();
        }
    }

    // Validación tipos de dato
    private boolean isValidType(Object value, String solrType) {
        if (value == null) return true;
        String t = solrType.toLowerCase();

        if (value instanceof Collection) {
            Collection<?> values = (Collection<?>) value;
            for (Object v : values) {
                if (!isValidTypeSingle(v, t)) return false;
            }
            return true;
        }
        
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                Object v = java.lang.reflect.Array.get(value, i);
                if (!isValidTypeSingle(v, t)) return false;
            }
            return true;
        }
        
        return isValidTypeSingle(value, t);
    }

    private boolean isValidTypeSingle(Object value, String solrType) {
        if (value == null) return true;
        if (solrType.contains("int") || solrType.contains("double") ||
            solrType.contains("float") || solrType.contains("long")) {
            try {
                Double.parseDouble(value.toString());
                return true;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        if (solrType.contains("string") || solrType.contains("text")) {
            return value instanceof String;
        }
        
        return true;
    }
}
