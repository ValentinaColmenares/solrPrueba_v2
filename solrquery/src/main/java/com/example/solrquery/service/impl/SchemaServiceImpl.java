package com.example.solrquery.service.impl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.solrquery.dto.CreateCopyFieldsRequest;
import com.example.solrquery.dto.DuplicateFieldsRequest;
import com.example.solrquery.entity.ClientSolr;
import com.example.solrquery.repository.ClientSolrRepository;
import com.example.solrquery.service.SchemaService;
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
public class SchemaServiceImpl implements SchemaService{

    private final ClientSolrRepository clientSolrRepository;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    public ResponseEntity<?> duplicateFields(DuplicateFieldsRequest request){
      
        log.info("JSON recibido para duplicar campos: {}", request);

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

        // Validación ingreso de colección origen
        if (request.getSourceCore() == null || request.getSourceCore().isBlank()) {
            return ResponseEntity.badRequest().body("La colección origen es obligatoria.");
        }

        // Validación de colección origen en Solr
        if (!coreExistsInSolr(client, request.getSourceCore())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("La colección origen '" + request.getSourceCore() + "' no existe.");
        }

        
        // Validación ingreso de colección destino
        if (request.getTargetCore() == null || request.getTargetCore().isBlank()) {
            return ResponseEntity.badRequest().body("La colección destino es obligatoria.");
        }

        // Validación de colección destino en Solr
        if (!coreExistsInSolr(client, request.getTargetCore())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("La colección destino '" + request.getTargetCore() + "' no existe.");
        }

        // Obtener campos de colección origen
        List<JsonObject> sourceFields = fetchRawSchemaFields(client, request.getSourceCore());

        // Obtener campos de colección destino
        Map<String, String> targetFields = fetchSchemaFields(client, request.getTargetCore());

        List<String> added = new ArrayList<>();
        String schemaTargetUrl = buildBaseUrl(client, request.getTargetCore()) + "/schema";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Comparación de campos, si no existe el campo entonces se crea en colección destino
        for (JsonObject field : sourceFields){
            String name = field.get("name").getAsString();
            if(!targetFields.containsKey(name)){
                JsonObject addField = new JsonObject();
                addField.addProperty("name", name);
                addField.addProperty("type", field.get("type").getAsString());
                if (field.has("multiValued"))
                    addField.addProperty("multiValued", field.get("multiValued").getAsBoolean());
                if (field.has("stored"))
                    addField.addProperty("stored", field.get("stored").getAsBoolean());
                if (field.has("indexed"))
                    addField.addProperty("indexed", field.get("indexed").getAsBoolean());
                JsonObject body = new JsonObject();
                body.add("add-field", addField);
                restTemplate.postForEntity(schemaTargetUrl, new HttpEntity<>(gson.toJson(body), headers), String.class);
                added.add(name);
            }
        }

        // Imprimir campos disponibles y campos añadidos
        List<JsonObject> updatedTargetFields = fetchRawSchemaFields(client, request.getTargetCore());
        List<Map<String,Object>> updated = new ArrayList<>();
        for(JsonObject fieldDef : updatedTargetFields){
            Type mapType = new TypeToken<Map<String,Object>>(){}.getType();
            Map<String, Object> map = gson.fromJson(fieldDef, mapType);
            updated.add(map);
        }
        Map<String,Object> result = new HashMap<>();
        result.put("addedFields", added);
        result.put("allFields", updated);
        return ResponseEntity.ok(result);

    }

    public ResponseEntity<?> createCopyFields(CreateCopyFieldsRequest request){
        
        log.info("JSON recibido para crear CopyFields: {}", request);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

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
                    .body("La colección origen '" + request.getCore() + "' no existe.");
        }

        // Validación ingreso de campo
        if (request.getField() == null || request.getField().isBlank()) {
            return ResponseEntity.badRequest().body("El campo para hacerle copyField es obligatorio.");
        }

        // Validación de que exista el campo a hacerle el copyField
        Map<String, String> fields = fetchSchemaFields(client, request.getCore());
        if(!fields.containsKey(request.getField())){
            return ResponseEntity.badRequest().body("El campo " + request.getField() + " no existe en la colección " + request.getCore());
        }

        // Validación ingreso de tipo de copyField
        if (request.getTypeCopyField() == null || request.getTypeCopyField().isBlank()) {
            return ResponseEntity.badRequest().body("Es obligatorio el tipo de dato para hacer el copyField.");
        }

        // Validación de que exista el tipo de campo en dynamicFields
        Map<String, String> dynamicFields = fetchDynamicFields(client, request.getCore());
        if(!dynamicFields.containsKey(request.getTypeCopyField())){
            return ResponseEntity.badRequest().body("El tipo de dato " + request.getTypeCopyField() + " no existe.");
        }

        // Validación de existencia del copyField solicitado
        List<Map<String,String>> copyFields = fetchCopyFields(client, request.getCore());
        String destField = request.getField() + "_" + request.getTypeCopyField();
        if(copyFields.stream().anyMatch(m -> m.get("source").equals(request.getField()) &&
                            m.get("dest").equals(destField))){
            return ResponseEntity.badRequest().body("El copyField solicitado ya existe");
        }
        
        // Creación de copyfields
        String schemaUrl = buildBaseUrl(client, request.getCore() + "/schema");

        JsonObject newCopyField = new JsonObject();
        newCopyField.addProperty("source", request.getField());
        newCopyField.addProperty("dest", destField);
        JsonArray arr = new JsonArray();
        arr.add(newCopyField);
        JsonObject command = new JsonObject();    
        command.add("add-copy-field", arr);
        restTemplate.postForEntity(schemaUrl, new HttpEntity<>(gson.toJson(command), headers), String.class);
    
        Map<String, String> created = Map.of(
            "source", newCopyField.get("source").getAsString(),
            "dest",   newCopyField.get("dest").getAsString()
        );

        return ResponseEntity.ok(
            Map.of(
                "createdCopyField", created
            )
        );
    }

    // Construcción de url base
    private String buildBaseUrl(ClientSolr client, String core){
        return "http://" + client.getIp() + ":" + client.getPort()
             + (core.isBlank()?"":"/solr/" + core);
    }

    // Obtener definición de campos de la colección
    private List<JsonObject> fetchRawSchemaFields(ClientSolr client, String core){
        String url = buildBaseUrl(client, core) + "/schema/fields";
        String body = restTemplate.getForObject(url, String.class);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("fields");
        Type listType = new TypeToken<List<JsonObject>>(){}.getType();
        return gson.fromJson(arr, listType);
    }

    // Obtener Map de nombres y tipos de colección
    private Map<String, String> fetchSchemaFields(ClientSolr client, String core){
        List<JsonObject> fields = fetchRawSchemaFields(client, core);
        Map<String,String> map = new HashMap<>();
        for (JsonObject field : fields) {
            String name = field.get("name").getAsString();
            String type = field.get("type").getAsString();
            map.put(name, type);
        }
        log.info("Campos del esquema de Solr para coleccion '{}': {}", core, map);
        return map;
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

    // Obtener CopyFields de la colección
    private List<Map<String,String>> fetchCopyFields(ClientSolr client, String core){
        String url = buildBaseUrl(client, core) + "/schema/copyfields";
        String body = restTemplate.getForObject(url, String.class);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("copyFields");
        Type listType = new TypeToken<List<JsonObject>>(){}.getType();
        List<JsonObject> objs = gson.fromJson(arr, listType);
        var out = new ArrayList<Map<String,String>>();
        for (JsonObject obj : objs){
            out.add(Map.of(
                "source", obj.get("source").getAsString(),
                "dest", obj.get("dest").getAsString()
            ));
        }
        return out;
    }

    // Obtener DynamicFields de la colección
    private Map<String, String> fetchDynamicFields(ClientSolr client, String core){
        String url = buildBaseUrl(client, core) + "/schema/dynamicfields";
        String body = restTemplate.getForObject(url, String.class);
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("dynamicFields");
        Type listType = new TypeToken<List<JsonObject>>(){}.getType();
        List<JsonObject> fields = gson.fromJson(arr, listType);
        Map<String,String> map = new HashMap<>();
        for (JsonObject field : fields) {
            String name = field.get("name").getAsString();
            if (name.startsWith("*_")) {
                name = name.substring(2);
            }else{
                continue;
            }
            String type = field.get("type").getAsString();
            map.put(name, type);
        }
        log.info("Campos de dynamicFields para coleccion '{}': {}", core, map);
        return map;
    }

}