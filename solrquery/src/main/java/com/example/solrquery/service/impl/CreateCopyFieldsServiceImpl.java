package com.example.solrquery.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.solrquery.dto.CreateCopyFieldsRequest;
import com.example.solrquery.entity.ClientSolr;
import com.example.solrquery.repository.ClientSolrRepository;
import com.example.solrquery.service.CreateCopyFieldsService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateCopyFieldsServiceImpl implements CreateCopyFieldsService{

    private final ClientSolrRepository clientSolrRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    public ResponseEntity<?> createCopyFields(CreateCopyFieldsRequest request){
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

        return ResponseEntity.ok("Funcionooo");
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

}
