package com.example.contor.persistence;

import com.example.contor.domain.EmailEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmailEmbeddingRepository extends JpaRepository<EmailEmbedding, EmailEmbedding.EmailEmbeddingId> {

    List<EmailEmbedding> findById_EmailId(Long emailId);
}
