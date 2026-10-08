package com.aiagent.service;

import com.aiagent.domain.AgentAuditLog;
import com.aiagent.repository.AgentAuditLogRepository;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class AuditService implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AgentAuditLogRepository auditLogRepository;

    public static class InteractionContext {
        public String sessionId;
        public String prompt;
        public long startTime;
        public String modelUsed;
        public int inputTokens = 0;
        public int outputTokens = 0;
        public int totalTokens = 0;
        public final List<String> toolsExecuted = new ArrayList<>();
    }

    private final ThreadLocal<InteractionContext> currentContext = new ThreadLocal<>();

    public AuditService(AgentAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void startInteraction(String sessionId, String prompt) {
        InteractionContext ctx = new InteractionContext();
        ctx.sessionId = sessionId;
        ctx.prompt = prompt;
        ctx.startTime = System.currentTimeMillis();
        currentContext.set(ctx);
    }

    public void recordToolExecution(String toolCall) {
        InteractionContext ctx = currentContext.get();
        if (ctx != null) {
            ctx.toolsExecuted.add(toolCall);
        }
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        InteractionContext ctx = currentContext.get();
        if (ctx != null && responseContext != null && responseContext.response() != null) {
            if (responseContext.response().model() != null) {
                ctx.modelUsed = responseContext.response().model();
            }

            TokenUsage usage = responseContext.response().tokenUsage();
            if (usage != null) {
                if (usage.inputTokenCount() != null) ctx.inputTokens += usage.inputTokenCount();
                if (usage.outputTokenCount() != null) ctx.outputTokens += usage.outputTokenCount();
                if (usage.totalTokenCount() != null) ctx.totalTokens += usage.totalTokenCount();
            }
        }
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("🚨 ChatModel hatası dinleyici tarafından yakalandı: {}", errorContext.error().getMessage());
    }

    public AgentAuditLog endInteraction(String agentReply) {
        InteractionContext ctx = currentContext.get();
        if (ctx == null) {
            return null;
        }

        try {
            long duration = System.currentTimeMillis() - ctx.startTime;
            String toolsJoined = String.join(", ", ctx.toolsExecuted);
            if (toolsJoined.isBlank()) toolsJoined = "Yok (Doğrudan Yanıt)";

            String promptSnippet = ctx.prompt;
            if (promptSnippet != null && promptSnippet.length() > 3900) {
                promptSnippet = promptSnippet.substring(0, 3900) + "...";
            }

            AgentAuditLog logEntry = AgentAuditLog.builder()
                    .sessionId(ctx.sessionId != null ? ctx.sessionId : "default")
                    .userPrompt(promptSnippet)
                    .agentResponse(agentReply)
                    .modelUsed(ctx.modelUsed != null ? ctx.modelUsed : "Bilinmeyen / Fallback Model")
                    .inputTokens(ctx.inputTokens)
                    .outputTokens(ctx.outputTokens)
                    .totalTokens(ctx.totalTokens)
                    .toolsExecuted(toolsJoined)
                    .executionDurationMs(duration)
                    .timestamp(LocalDateTime.now())
                    .build();

            return auditLogRepository.save(logEntry);
        } finally {
            currentContext.remove();
        }
    }

    public List<AgentAuditLog> getRecentLogs() {
        return auditLogRepository.findTop50ByOrderByTimestampDesc();
    }
}
