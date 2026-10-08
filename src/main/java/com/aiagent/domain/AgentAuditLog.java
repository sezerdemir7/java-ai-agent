package com.aiagent.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String sessionId;

    @Column(nullable = false, length = 4000)
    private String userPrompt;

    @Column(columnDefinition = "TEXT")
    private String agentResponse;

    @Column(length = 100)
    private String modelUsed;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    @Column(length = 1000)
    private String toolsExecuted;

    private Long executionDurationMs;

    @Column(nullable = false)
    private LocalDateTime timestamp;
}
