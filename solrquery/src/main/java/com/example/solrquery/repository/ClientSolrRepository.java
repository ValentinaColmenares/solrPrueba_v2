package com.example.solrquery.repository;
  
import java.util.Optional;
  
import org.springframework.data.jpa.repository.JpaRepository;
  
import com.example.solrquery.entity.ClientSolr;
  
public interface ClientSolrRepository extends JpaRepository<ClientSolr, Long>{
  Optional<ClientSolr> findByName(String name);
} 
