package com.aiagent.repository;

import com.aiagent.domain.AgentAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentAuditLogRepository extends JpaRepository<AgentAuditLog, Long> {
    List<AgentAuditLog> findTop50ByOrderByTimestampDesc();
}
