package com.example.solrquery.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.solrquery.dto.QueryRequestsDto;
import com.example.solrquery.entity.Client;
import com.example.solrquery.repository.ClientRepository;
import com.example.solrquery.service.QueryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QueryServiceImpl implements QueryService{

  private final ClientRepository clientRepository;
  private final RestTemplate restTemplate;

  public String consultarSolr(QueryRequestsDto request) {
      Client cliente = clientRepository.findByNombre(request.getClient())
          .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

      String url = String.format(
          "http://%s:%d/solr/%s/select?q=%s&wt=json",
          cliente.getIpSolr(),
          cliente.getPuertoSolr(),
          request.getColeccion(),
          request.getFiltro() != null ? request.getFiltro() : "*:*"
      );

      return restTemplate.getForObject(url, String.class);
  }
}
