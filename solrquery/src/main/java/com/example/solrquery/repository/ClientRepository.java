package com.example.solrquery.repository;
  
import java.util.Optional;
  
import org.springframework.data.jpa.repository.JpaRepository;
  
import com.example.solrquery.entity.Client;
  
public interface ClientRepository extends JpaRepository<Client, Long>{
  Optional<Client> findByName(String name);
} 
