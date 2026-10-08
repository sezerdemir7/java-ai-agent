package com.aiagent.controller;

import com.aiagent.agent.Agent;
import com.aiagent.domain.AgentAuditLog;
import com.aiagent.service.AuditService;
import com.aiagent.tools.Tools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final Agent agent;
    private final Tools tools;
    private final AuditService auditService;
    private final com.aiagent.service.ProactiveAgentService proactiveAgentService;

    public AgentController(Agent agent, Tools tools, AuditService auditService, com.aiagent.service.ProactiveAgentService proactiveAgentService) {
        this.agent = agent;
        this.tools = tools;
        this.auditService = auditService;
        this.proactiveAgentService = proactiveAgentService;
    }

    public record ChatRequest(String sessionId, String message) {}
    public record ChatResponse(String sessionId, String userMessage, String agentReply, Long executionDurationMs, Integer totalTokens) {}

    /**
     * POST İsteği ile Ajanla İletişim (Standart / Senkron)
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chatPost(@RequestBody ChatRequest request) {
        String session = (request.sessionId() != null && !request.sessionId().isBlank())
                ? request.sessionId().trim()
                : "default-session";

        auditService.startInteraction(session, request.message());
        String reply;
        try {
            reply = agent.chat(session, request.message());
        } catch (Exception e) {
            reply = "⚠️ Ajan yürütülürken hata meydana geldi: " + e.getMessage();
        }
        AgentAuditLog auditLog = auditService.endInteraction(reply);

        Long duration = auditLog != null ? auditLog.getExecutionDurationMs() : 0L;
        Integer tokens = auditLog != null ? auditLog.getTotalTokens() : 0;

        return ResponseEntity.ok(new ChatResponse(session, request.message(), reply, duration, tokens));
    }

    /**
     * Streaming (SSE - Server-Sent Events) Uç Noktası
     * Cevap ChatGPT gibi harf harf / kelime kelime akar!
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam(value = "sessionId", defaultValue = "default-session") String sessionId,
            @RequestParam("message") String message) {

        SseEmitter emitter = new SseEmitter(120_000L);
        auditService.startInteraction(sessionId, message);
        StringBuilder fullReply = new StringBuilder();

        agent.streamChat(sessionId, message)
                .onNext(token -> {
                    try {
                        fullReply.append(token);
                        emitter.send(SseEmitter.event().name("token").data(Map.of("token", token)));
                    } catch (Exception e) {
                        log.warn("SSE token gönderme hatası: {}", e.getMessage());
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    auditService.endInteraction(fullReply.toString());
                    try {
                        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                        emitter.complete();
                    } catch (Exception ignored) {}
                })
                .onError(error -> {
                    auditService.endInteraction("Hata: " + error.getMessage());
                    log.error("Streaming hatası: {}", error.getMessage());
                    emitter.completeWithError(error);
                })
                .start();

        return emitter;
    }

    /**
     * Hızlı test için GET uç noktası
     */
    @GetMapping("/chat")
    public ResponseEntity<ChatResponse> chatGet(
            @RequestParam(value = "sessionId", defaultValue = "default-session") String sessionId,
            @RequestParam("message") String message) {

        auditService.startInteraction(sessionId, message);
        String reply;
        try {
            reply = agent.chat(sessionId, message);
        } catch (Exception e) {
            reply = "⚠️ Ajan yürütülürken hata meydana geldi: " + e.getMessage();
        }
        AgentAuditLog auditLog = auditService.endInteraction(reply);

        Long duration = auditLog != null ? auditLog.getExecutionDurationMs() : 0L;
        Integer tokens = auditLog != null ? auditLog.getTotalTokens() : 0;

        return ResponseEntity.ok(new ChatResponse(sessionId, message, reply, duration, tokens));
    }

    /**
     * Envanter, bekleyen siparişler, audit logları ve proaktif uyarıları canlı izleme uç noktası
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "products", tools.getAllProductsForDashboard(),
                "pendingOrders", tools.getPendingOrders(),
                "recentAudits", auditService.getRecentLogs(),
                "proactiveAlerts", proactiveAgentService.getRecentAlerts()
        ));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AgentAuditLog>> getAuditLogs() {
        return ResponseEntity.ok(auditService.getRecentLogs());
    }

    /**
     * Human-in-the-Loop (HITL) - Web UI üzerinden Sipariş Onaylama
     */
    @PostMapping("/orders/{orderNumber}/approve")
    public ResponseEntity<Map<String, String>> approveOrder(
            @PathVariable("orderNumber") String orderNumber,
            @RequestBody(required = false) Map<String, String> body) {
        String note = (body != null && body.containsKey("note")) ? body.get("note") : "Web Arayüzünden Yönetici Onayı Verildi";
        String result = tools.approveOrder(orderNumber, note);
        return ResponseEntity.ok(Map.of("message", result));
    }

    /**
     * Human-in-the-Loop (HITL) - Web UI üzerinden Sipariş Reddetme / İptal
     */
    @PostMapping("/orders/{orderNumber}/reject")
    public ResponseEntity<Map<String, String>> rejectOrder(
            @PathVariable("orderNumber") String orderNumber,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = (body != null && body.containsKey("reason")) ? body.get("reason") : "Yönetici tarafından iptal edildi";
        String result = tools.rejectOrder(orderNumber, reason);
        return ResponseEntity.ok(Map.of("message", result));
    }

    /**
     * Otonom Arka Plan Nöbetçisini Anlık Manuel Tetikleme Uç Noktası
     */
    @PostMapping("/proactive-scan")
    public ResponseEntity<Map<String, Object>> triggerProactiveScan() {
        var alert = proactiveAgentService.runProactiveAudit();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "alert", alert != null ? alert : "Tüm stoklar güvenli, risk tespit edilmedi."
        ));
    }

    @GetMapping("/proactive-alerts")
    public ResponseEntity<List<com.aiagent.service.ProactiveAgentService.ProactiveAlert>> getProactiveAlerts() {
        return ResponseEntity.ok(proactiveAgentService.getRecentAlerts());
    }
}
