package com.example.contor.persistence;

import com.example.contor.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, AuditLog.AuditLogId> {

    List<AuditLog> findByActionOrderByOccurredAtDesc(String action);
}
