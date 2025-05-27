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
import com.example.solrquery.service.SolrSchemaService;
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
public class SolrSchemaServiceImpl implements SolrSchemaService{

    private final ClientSolrRepository clientRepo;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Gson gson = new Gson();

    @Override
    public ResponseEntity<?> duplicateFields(DuplicateFieldsRequest req) {
        // 1) Validar cliente
        ClientSolr client = clientRepo.findByName(req.getClient()).orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Cliente no encontrado: " + req.getClient());
        }

        // 2) Validar cores
        if (!coreExists(client, req.getSourceCore()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Core origen no existe: " + req.getSourceCore());
        if (!coreExists(client, req.getTargetCore()))
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Core destino no existe: " + req.getTargetCore());

        // 3) Leer esquema de origen
        List<JsonObject> sourceFields = fetchRawSchemaFields(client, req.getSourceCore());

        // 4) Leer esquema de destino y guardar nombres
        Map<String,String> targetMap = fetchSchemaFields(client, req.getTargetCore());

        // 5) Por cada campo origen que NO exista, invocar Schema API add-field
        List<String> added = new ArrayList<>();
        String schemaUrl = buildBaseUrl(client, req.getTargetCore()) + "/schema";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        for (JsonObject fieldDef : sourceFields) {
            String name = fieldDef.get("name").getAsString();
            if (!targetMap.containsKey(name)) {
                // Construyo el comando add-field copiando type, multiValued, stored, indexed
                JsonObject addF = new JsonObject();
                addF.addProperty("name", name);
                addF.addProperty("type", fieldDef.get("type").getAsString());
                if (fieldDef.has("multiValued"))
                    addF.addProperty("multiValued", fieldDef.get("multiValued").getAsBoolean());
                if (fieldDef.has("stored"))
                    addF.addProperty("stored", fieldDef.get("stored").getAsBoolean());
                if (fieldDef.has("indexed"))
                    addF.addProperty("indexed", fieldDef.get("indexed").getAsBoolean());

                JsonObject body = new JsonObject();
                body.add("add-field", addF);

                restTemplate.postForEntity(schemaUrl, new HttpEntity<>(gson.toJson(body), headers), String.class);
                added.add(name);
            }
        }

        // 6) Leer esquema actualizado y convertir a List<Map<String,Object>>
        List<JsonObject> updatedRaw = fetchRawSchemaFields(client, req.getTargetCore());
        List<Map<String, Object>> updated = new ArrayList<>();
        for (JsonObject jo : updatedRaw) {
            // convierte cada JsonObject a Map<String,Object>
            Map<String, Object> map = gson.fromJson(jo, Map.class);
            updated.add(map);
        }

        // 7) Devolver resultados
        Map<String,Object> result = new HashMap<>();
        result.put("addedFields", added);
        result.put("allFields", updated);
        return ResponseEntity.ok(result);
    }

    @Override
    public ResponseEntity<?> createCopyFields(CreateCopyFieldsRequest req) {
        final String CORE = req.getCore();
        final String TEXT_FIELD = "_text_";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 1) Cliente y core
        ClientSolr client = clientRepo.findByName(req.getClient()).orElse(null);
        if (client == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Cliente no encontrado: " + req.getClient());
        }
        if (!coreExists(client, CORE)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("Core no existe: " + CORE);
        }

        String schemaUrl = buildBaseUrl(client, CORE) + "/schema";

        // 2) Asegurar campo _text_ existe
        Map<String,String> fieldMap = fetchSchemaFields(client, CORE);
        if (!fieldMap.containsKey(TEXT_FIELD)) {
            JsonObject addTextField = new JsonObject();
            addTextField.addProperty("name", TEXT_FIELD);
            addTextField.addProperty("type", "text_general");
            addTextField.addProperty("multiValued", false);
            addTextField.addProperty("stored", false);
            JsonObject payload = new JsonObject();
            payload.add("add-field", addTextField);
            restTemplate.postForEntity(schemaUrl, new HttpEntity<>(gson.toJson(payload), headers), String.class);
            fieldMap = fetchSchemaFields(client, CORE);
        }

        // 3) Asegurar copyField "* -> _text_" existe
        List<Map<String,String>> existing = fetchCopyFields(client, CORE);
        boolean hasGlobal = existing.stream()
            .anyMatch(m -> m.get("source").equals("*") && m.get("dest").equals(TEXT_FIELD));
        List<JsonObject> ops = new ArrayList<>();
        if (!hasGlobal) {
            JsonObject cf = new JsonObject();
            cf.addProperty("source", "*");
            cf.addProperty("dest", TEXT_FIELD);
            ops.add(cf);
        }

        // 4) Leer esquema completo (raw JSON) para decidir dinámicos
        List<JsonObject> raw = fetchRawSchemaFields(client, CORE);
        for (JsonObject f : raw) {
            String name = f.get("name").getAsString();
            String type = f.get("type").getAsString().toLowerCase();

            // saltar campos internos y el propio _text_
            if (name.startsWith("_") || name.equals(TEXT_FIELD)) continue;

            // ya hay un copyField para este source->dest
            boolean exists = existing.stream()
                .anyMatch(m -> m.get("source").equals(name) && m.get("dest").startsWith(name + "_"));
            if (exists) continue;

            // decidir sufijo según tipo
            String suffix;
            if (type.contains("text_general")) {
                suffix = "_str";
            } else if (type.contains("date")) {
                suffix = "_dt";
            } else if (type.contains("double") || type.contains("pdoubles")) {
                suffix = "_d";
            } else if (type.contains("float")) {
                suffix = "_f";
            } else if (type.contains("long")) {
                suffix = "_l";
            } else if (type.contains("int")) {
                suffix = "_i";
            } else {
                continue; // otros tipos no los mapeamos
            }

            JsonObject cf = new JsonObject();
            cf.addProperty("source", name);
            cf.addProperty("dest", name + suffix);
            ops.add(cf);
        }

        // 5) Enviar UNA sola petición con todo el array de add-copy-field
        List<Map<String,String>> created = new ArrayList<>();
        if (!ops.isEmpty()) {
            JsonObject multi = new JsonObject();
            JsonArray arr = new JsonArray();
            ops.forEach(arr::add);
            multi.add("add-copy-field", arr);
            restTemplate.postForEntity(schemaUrl, new HttpEntity<>(gson.toJson(multi), headers), String.class);

            // preparar output
            ops.forEach(o -> created.add(Map.of(
                "source", o.get("source").getAsString(),
                "dest",   o.get("dest").getAsString()
            )));
        }

        return ResponseEntity.ok(Map.of(
            "globalCopyField", Map.of("source","*", "dest", TEXT_FIELD),
            "createdCopyFields", created
        ));
    }

    private boolean coreExists(ClientSolr c, String core) {
        String url = String.format(
            "http://%s:%s/solr/admin/cores?action=STATUS",
            c.getIp(), c.getPort()
        );
        try {
            String body = restTemplate.getForObject(url, String.class);
            JsonObject status = JsonParser
                .parseString(body)
                .getAsJsonObject()
                .getAsJsonObject("status");
            return status.has(core);
        } catch (Exception e) {
            log.error("Error consultando cores en {}: {}", url, e.getMessage());
            return false;
        }
    }

    private String buildBaseUrl(ClientSolr c, String core) {
        return "http://" + c.getIp() + ":" + c.getPort()
             + (core.isBlank()?"":"/solr/" + core);
    }

    /** Devuelve sólo name/type/indexed/stored/multiValued de cada field */
    private List<JsonObject> fetchRawSchemaFields(ClientSolr c, String core) {
        String url = buildBaseUrl(c, core) + "/schema/fields";
        JsonArray arr = JsonParser
            .parseString(restTemplate.getForObject(url, String.class))
            .getAsJsonObject()
            .getAsJsonArray("fields");
        Type listType = new TypeToken<List<JsonObject>>(){}.getType();
        return gson.fromJson(arr, listType);
    }

    /** Mapa name→type para validaciones rápidas */
    private Map<String,String> fetchSchemaFields(ClientSolr c, String core) {
        List<JsonObject> raw = fetchRawSchemaFields(c, core);
        Map<String,String> m = new HashMap<>();
        for (JsonObject f: raw) {
            m.put(f.get("name").getAsString(),
                  f.get("type").getAsString());
        }
        return m;
    }

    /** Lee copyFields existentes vía /schema/copyfields */
    private List<Map<String,String>> fetchCopyFields(ClientSolr c, String core) {
        String url = buildBaseUrl(c, core) + "/schema/copyfields";
        JsonArray arr = JsonParser
            .parseString(restTemplate.getForObject(url, String.class))
            .getAsJsonObject()
            .getAsJsonArray("copyFields");
        Type lt = new TypeToken<List<JsonObject>>(){}.getType();
        List<JsonObject> objs = gson.fromJson(arr, lt);
        var out = new ArrayList<Map<String,String>>();
        for (JsonObject o: objs) {
            out.add(Map.of(
                "source", o.get("source").getAsString(),
                "dest",   o.get("dest").getAsString()
            ));
        }
        return out;
    }
}
