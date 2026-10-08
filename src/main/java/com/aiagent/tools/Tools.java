package com.aiagent.tools;

import com.aiagent.domain.Product;
import com.aiagent.domain.PurchaseOrder;
import com.aiagent.rag.KnowledgeService;
import com.aiagent.repository.ProductRepository;
import com.aiagent.repository.PurchaseOrderRepository;
import com.aiagent.service.AuditService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Enterprise Business Tools (JPA & H2 Veritabanı Destekli).
 * Ajanın veritabanı (RDBMS) üzerinde sorgulama yapmasını ve kurumsal sözleşmeleri aramasını sağlar.
 */
@Component
public class Tools {

    private static final Logger log = LoggerFactory.getLogger(Tools.class);

    private final ProductRepository productRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final AuditService auditService;
    private final KnowledgeService knowledgeService;

    public Tools(ProductRepository productRepository,
                 PurchaseOrderRepository purchaseOrderRepository,
                 AuditService auditService,
                 KnowledgeService knowledgeService) {
        this.productRepository = productRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.auditService = auditService;
        this.knowledgeService = knowledgeService;
    }

    /**
     * Web Dashboard için doğrudan Product listesi döner
     */
    public List<Product> getAllProductsForDashboard() {
        return productRepository.findAll();
    }

    @Tool("Sistemdeki tüm kayıtlı ürünleri, kodlarını, mevcut stoklarını, kritik eşiklerini, birim fiyatlarını ve tedarikçilerini veritabanından detaylı olarak listeler.")
    public String getAllProducts() {
        log.info("🛠️ [TOOL EXECUTION]: getAllProducts() -> Veritabanından tüm ürünler çekiliyor.");
        auditService.recordToolExecution("getAllProducts()");
        List<Product> products = productRepository.findAll();
        if (products.isEmpty()) {
            return "ERP veritabanında henüz kayıtlı ürün bulunmamaktadır.";
        }

        StringBuilder sb = new StringBuilder("ERP Güncel Envanter Listesi (" + products.size() + " kayıtlı ürün):\n");
        for (Product p : products) {
            boolean isCritical = p.getStock() <= p.getCriticalThreshold();
            sb.append(String.format("• Ürün Kodu: %s | Ürün Adı: %s | Stok: %d adet | Kritik Eşik: %d | Durum: %s | Fiyat: %.2f TL | Tedarikçi: %s\n",
                    p.getCode(), p.getName(), p.getStock(), p.getCriticalThreshold(),
                    (isCritical ? "KRİTİK SEVİYE (Sipariş Gerekli)" : "YETERLİ"),
                    p.getPrice(), p.getSupplier()));
        }
        return sb.toString();
    }

    @Tool("Belirtilen ürün koduna göre veritabanından anlık stok durumu, birim fiyatı, tedarikçisi ve kritik seviye uyarısını getirir.")
    public String getProductStock(@P("Ürün kodu, örn: PRD-101") String productCode) {
        log.info("🛠️ [TOOL EXECUTION]: getProductStock(productCode='{}')", productCode);
        auditService.recordToolExecution("getProductStock(" + productCode + ")");

        if (productCode == null || productCode.isBlank()) {
            return "Hata: Geçerli bir ürün kodu belirtilmedi.";
        }

        Optional<Product> productOpt = productRepository.findByCodeIgnoreCase(productCode.trim());
        if (productOpt.isEmpty()) {
            return "Hata: '" + productCode + "' kodlu ürün veritabanında bulunamadı. Lütfen ürün kodunu kontrol ediniz.";
        }

        Product product = productOpt.get();
        boolean isCritical = product.getStock() <= product.getCriticalThreshold();

        return String.format(
                "Ürün: %s (Kod: %s) | Stok: %d adet | Kritik Eşik: %d adet | Durum: %s | Tedarikçi: %s | Birim Fiyat: %.2f TL",
                product.getName(), product.getCode(), product.getStock(), product.getCriticalThreshold(),
                (isCritical ? "KRİTİK SEVİYEDE (Tedarikçiden sipariş açılması önerilir)" : "YETERLİ"),
                product.getSupplier(), product.getPrice()
        );
    }

    @Tool("Tedarikçiye verilmek üzere ERP veritabanında yeni bir satın alma siparişi taslağı (Purchase Order) oluşturur ve kaydeder.")
    public String createOrderDraft(
            @P("Sipariş verilecek ürün kodu, örn: PRD-101") String productCode,
            @P("Sipariş adedi") int quantity,
            @P("Tedarikçi firma adı") String supplier) {

        log.info("🛠️ [TOOL EXECUTION]: createOrderDraft(code='{}', qty={}, supplier='{}')",
                productCode, quantity, supplier);
        auditService.recordToolExecution("createOrderDraft(" + productCode + ", " + quantity + ", " + supplier + ")");

        if (quantity <= 0) {
            return "Hata: Sipariş adedi sıfırdan büyük olmalıdır!";
        }

        Optional<Product> productOpt = productRepository.findByCodeIgnoreCase(productCode.trim());
        String productName = productOpt.map(Product::getName).orElse("Bilinmeyen Ürün");
        double unitPrice = productOpt.map(Product::getPrice).orElse(0.0);
        double totalAmount = unitPrice * quantity;

        String orderNumber = "PO-" + (System.currentTimeMillis() % 100000);

        // İş Kuralı (Guardrail):
        // Eğer sipariş 20 adetten fazla veya 100.000 TL üzerindeyse direktör onayı gerektirir.
        String status = (quantity > 20 || totalAmount > 100000.0)
                ? "DIREKTOR_ONAYI_BEKLIYOR"
                : "ONAY_BEKLENIYOR";

        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(orderNumber)
                .productCode(productCode.toUpperCase().trim())
                .productName(productName)
                .quantity(quantity)
                .supplier(supplier)
                .totalAmount(totalAmount)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();

        purchaseOrderRepository.save(order);

        return String.format(
                "Başarılı: ERP veritabanına %s numaralı satın alma sipariş taslağı kaydedildi. " +
                "(Ürün: %s, Adet: %d, Toplam Tutar: %.2f TL, Tedarikçi: %s, Durum: %s)",
                orderNumber, productCode, quantity, totalAmount, supplier, status
        );
    }

    @Tool("Sistemde onay bekleyen tüm satın alma sipariş taslaklarını veritabanından listeler.")
    public List<PurchaseOrder> getPendingOrders() {
        log.info("🛠️ [TOOL EXECUTION]: getPendingOrders() -> Sipariş taslakları veritabanından çekiliyor.");
        auditService.recordToolExecution("getPendingOrders()");
        return purchaseOrderRepository.findAllByOrderByCreatedAtDesc();
    }

    @Tool("Kurumsal tedarikçi sözleşmelerini, garanti şartlarını, teslimat sürelerini, gecikme cezalarını ve satın alma onay yönetmeliklerini kurumsal bilgi tabanında semantik arama ile sorgular.")
    public String searchContractsAndPolicies(@P("Sözleşmede aranacak konu veya tedarikçi adı, örn: 'TeknoTedarik gecikme cezası' veya 'ölü piksel garantisi'") String query) {
        log.info("🛠️ [TOOL EXECUTION]: searchContractsAndPolicies(query='{}')", query);
        auditService.recordToolExecution("searchContractsAndPolicies(" + query + ")");
        return knowledgeService.searchContracts(query);
    }
}
