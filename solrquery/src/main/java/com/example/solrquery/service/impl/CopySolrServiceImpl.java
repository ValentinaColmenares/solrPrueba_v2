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
import org.springframework.web.util.UriComponentsBuilder;

import com.example.solrquery.dto.CopySolrRequest;
import com.example.solrquery.entity.ClientSolr;
import com.example.solrquery.repository.ClientSolrRepository;
import com.example.solrquery.service.CopySolrService;
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
public class CopySolrServiceImpl implements CopySolrService{

    private final ClientSolrRepository clientSolrRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();
    private String protocol = "http";
    private String qt = "select";
    

    public ResponseEntity<?> copy(CopySolrRequest request) {
        log.info("JSON recibido para copiar: {}", request);

        // Validación de parámetros obligatorios
        if (request.getClient().isBlank() ||
            request.getSourceCore().isBlank() ||
            request.getTargetCore().isBlank()) {
            return ResponseEntity.badRequest().body("Cliente, colección origen y colección destino son obligatorios.");
        }

        // Validación de cliente en MySQL
        ClientSolr client = clientSolrRepository.findByName(request.getClient()).orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Cliente no encontrado: " + request.getClient());
        }

        // Validación de ambas colecciones en Solr
        if (!coreExistsInSolr(client, request.getSourceCore())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("La colección origen '" + request.getSourceCore() + "' no existe.");
        }
        if (!coreExistsInSolr(client, request.getTargetCore())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("La colección destino '" + request.getTargetCore() + "' no existe.");
        }

        // Validación de start y rows
        if (!isNullOrInteger(request.getStart()) || !isNullOrInteger(request.getRows())) {
            return ResponseEntity.badRequest()
                    .body("Los parámetros 'start' y 'rows' deben ser enteros o vacíos.");
        }

        // Validación de Sort
        if (!isValidSort(request.getSort())) {
            return ResponseEntity.badRequest()
                    .body("El parámetro 'sort' debe tener formato '<campo> asc' o '<campo> desc'.");
        }

        // Construcción URL de consulta para la colección origen
        String baseUrl = protocol + "://" + client.getIp() + ":" + client.getPort()
                       + "/solr/" + request.getSourceCore() + "/" + qt;
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl);
        addIfNotBlank(builder, "q", request.getQ());
        addIfNotBlank(builder, "fq", request.getFq());
        addIfNotBlank(builder, "fl", request.getFl());
        addIfNotBlank(builder, "start", request.getStart());
        addIfNotBlank(builder, "rows", request.getRows());

        String partialUrl = builder.build().encode().toUriString();
        String queryUrl = partialUrl + (partialUrl.contains("?") ? "&" : "?")
                        + "sort=" + (request.getSort() == null ? "" : request.getSort());

        log.info("URL de consulta (origen): {}", queryUrl);

        String solrJson;
        try {
            solrJson = restTemplate.getForObject(queryUrl, String.class);
        } catch (Exception e) {
            log.error("Error consultando Solr origen", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al consultar la colección origen: " + e.getMessage());
        }

        // Extracción de documentos
        JsonObject root = JsonParser.parseString(solrJson).getAsJsonObject();
        JsonObject response = root.getAsJsonObject("response");
        int numFound = response.get("numFound").getAsInt();
        if (numFound == 0) {
            return ResponseEntity.ok("No hay documentos en la colección origen");
        }
        JsonArray docsJson = response.getAsJsonArray("docs");
        Type listType = new TypeToken<List<Map<String, Object>>>(){}.getType();
        List<Map<String, Object>> docs = gson.fromJson(docsJson, listType);

        for (Map<String, Object> doc : docs) {
            doc.keySet().removeIf(field -> field.equals("_version_"));
        }

        // Validación de tipos en colección destino
        Map<String,String> targetSchema = fetchSolrSchemaFields(client, request.getTargetCore());
        for (int i = 0; i < docs.size(); i++) {
            Map<String,Object> doc = docs.get(i);
            for (Map.Entry<String,Object> e : doc.entrySet()) {

                if (targetSchema.containsKey(e.getKey())){
                    if (!isValidType(e.getValue(), targetSchema.get(e.getKey()))) {
                        return ResponseEntity.badRequest()
                                .body("Doc " + (i+1) + ": valor '" + e.getValue() +
                                    "' no concuerda con tipo '" + targetSchema.get(e.getKey()) + "'");
                    }
                }

            }
        }

        // Indexación en colección destino
        String updateUrl = "http://" + client.getIp() + ":" + client.getPort()
                         + "/solr/" + request.getTargetCore() + "/update?commit=true";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<List<Map<String,Object>>> entity =
                new HttpEntity<>(docs, headers);

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(updateUrl, entity, String.class);
            log.info("Solr destino respondio: {}", resp.getBody());
        } catch (Exception e) {
            log.error("Error indexando en Solr destino", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al indexar en destino: " + e.getMessage());
        }

        Map<String,Object> result = new LinkedHashMap<>();
        result.put("message", "Se copiaron " + docs.size() +
                             " docs de '" + request.getSourceCore() +
                             "' a '" + request.getTargetCore() + "'");
        result.put("docs", docs);
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

}
