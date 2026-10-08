package com.aiagent.service;

import com.aiagent.domain.Product;
import com.aiagent.rag.KnowledgeService;
import com.aiagent.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Autonomous Proactive Agent Service.
 * Kullanıcı soru sormadan, arka planda otonom olarak (Scheduled)
 * ERP veritabanını tarar, tedarikçi sözleşmelerini analiz eder ve
 * olası kesintilere karşı proaktif risk raporları üretir.
 */
@Service
public class ProactiveAgentService {

    private static final Logger log = LoggerFactory.getLogger(ProactiveAgentService.class);

    private final ProductRepository productRepository;
    private final KnowledgeService knowledgeService;
    private final List<ProactiveAlert> alertHistory = new CopyOnWriteArrayList<>();

    public record ProactiveAlert(
            String id,
            LocalDateTime timestamp,
            String title,
            String severity,
            int criticalCount,
            String summary,
            List<String> affectedProducts,
            String contractInsight
    ) {}

    public ProactiveAgentService(ProductRepository productRepository, KnowledgeService knowledgeService) {
        this.productRepository = productRepository;
        this.knowledgeService = knowledgeService;
    }

    /**
     * Otonom Arka Plan Nöbetçisi:
     * Her 30 dakikada bir otomatik çalışır (Ayrıca UI'dan manuel de tetiklenebilir).
     */
    @Scheduled(fixedRate = 1800000, initialDelay = 8000)
    public ProactiveAlert runProactiveAudit() {
        log.info("🛡️ [OTONOM NÖBETÇİ BAŞLADI]: ERP Stokları ve Tedarik Riskleri taranıyor...");

        List<Product> products = productRepository.findAll();
        List<Product> criticalProducts = products.stream()
                .filter(p -> p.getStock() <= p.getCriticalThreshold())
                .toList();

        if (criticalProducts.isEmpty()) {
            log.info("✅ [OTONOM NÖBETÇİ]: Tüm stoklar güvenli seviyede. Kritik risk bulunamadı.");
            return null;
        }

        List<String> affected = new ArrayList<>();
        StringBuilder summaryBuilder = new StringBuilder();
        summaryBuilder.append(String.format("Tespit edilen %d adet kritik ürün için acil tedarik aksiyonu önerilmektedir:\n", criticalProducts.size()));

        for (Product p : criticalProducts) {
            String itemDesc = String.format("%s (%s): Mevcut %d / Eşik %d - Tedarikçi: %s",
                    p.getName(), p.getCode(), p.getStock(), p.getCriticalThreshold(), p.getSupplier());
            affected.add(itemDesc);
            summaryBuilder.append("• ").append(itemDesc).append("\n");
        }

        // Tedarikçi sözleşmelerinden teslimat süresi ve gecikme cezası analizi (RAG)
        String supplierToQuery = criticalProducts.get(0).getSupplier();
        String contractInsight = knowledgeService.searchContracts(supplierToQuery + " teslimat süresi gecikme cezası");

        String alertId = "ALT-" + (System.currentTimeMillis() % 100000);
        ProactiveAlert alert = new ProactiveAlert(
                alertId,
                LocalDateTime.now(),
                "Kritik Envanter ve Tedarik Zinciri Risk Uyarısı",
                criticalProducts.size() >= 2 ? "HIGH" : "MEDIUM",
                criticalProducts.size(),
                summaryBuilder.toString(),
                affected,
                contractInsight
        );

        alertHistory.add(0, alert);
        if (alertHistory.size() > 20) {
            alertHistory.remove(alertHistory.size() - 1);
        }

        log.warn("⚠️ [OTONOM NÖBETÇİ UYARISI OLUŞTURULDU]: {} - {} kritik ürün!", alert.title(), alert.criticalCount());
        return alert;
    }

    public List<ProactiveAlert> getRecentAlerts() {
        return new ArrayList<>(alertHistory);
    }
}
