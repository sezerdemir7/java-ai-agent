package com.aiagent.rag;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Enterprise RAG (Retrieval-Augmented Generation) Bilgi Katmanı.
 * Şirketin kurumsal tedarikçi sözleşmelerini ve satın alma politikalarını
 * bellek içi vektör veritabanına indeksler ve semantik arama sağlar.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final EmbeddingStore<TextSegment> embeddingStore = new InMemoryEmbeddingStore<>();
    private final EmbeddingModel embeddingModel = new SimpleEmbeddingModel();

    @PostConstruct
    public void initKnowledgeBase() {
        log.info("🧠 Yerel Vektör Embedding Modeli (SimpleEmbeddingModel) aktif.");
        log.info("📚 Kurumsal tedarikçi sözleşmeleri ve satın alma politikaları indeksleniyor...");

        List<Document> corporateDocuments = List.of(
                Document.from(
                        """
                        SÖZLEŞME: TeknoTedarik A.Ş. Çerçeve Tedarik Sözleşmesi (2026)
                        1. Teslimat Süresi: Sipariş onayından itibaren maksimum 3 iş günüdür.
                        2. Gecikme Cezası: Teslimattaki her geciken gün için toplam sipariş bedelinin %1'i oranında ceza faturası kesilir.
                        3. Garanti ve Servis: Tüm dizüstü bilgisayar ve sunucu donanımları 3 yıl yerinde parça ve servis garantilidir.
                        4. Değişim Şartı: Arızalanan cihaz 48 saat içinde onarılamazsa derhal sıfır ürünle değiştirilir.
                        5. Ödeme Vadesi: Fatura kesim tarihinden itibaren 45 gündür.
                        """,
                        Metadata.from(Map.of("doc_type", "contract", "supplier", "TeknoTedarik A.Ş."))
                ),
                Document.from(
                        """
                        SÖZLEŞME: OfisDepo Ltd. Çevre Birimleri ve Sarf Malzeme Sözleşmesi (2026)
                        1. Minimum Sipariş: Minimum sipariş adedi 10 adettir.
                        2. Kargo ve Lojistik: 5.000 TL ve üzeri alımlarda nakliye ve kargo sigortası tedarikçiye aittir.
                        3. Garanti Kapsamı: Klavye, mouse ve sarf malzemeleri için 2 yıl üretici garantisi geçerlidir.
                        4. Teslimat Süresi: Sipariş sonrası 2 iş günüdür.
                        5. Ödeme Vadesi: Fatura kesim tarihinden itibaren 30 gündür.
                        """,
                        Metadata.from(Map.of("doc_type", "contract", "supplier", "OfisDepo Ltd."))
                ),
                Document.from(
                        """
                        SÖZLEŞME: MegaBilisim A.Ş. Ekran ve Monitör Tedarik Sözleşmesi (2026)
                        1. Piksel Garantisi: Tedarik edilen tüm monitörlerde '0 Ölü Piksel' (Zero Dead Pixel) garantisi mevcuttur.
                        2. Birebir Değişim: Tek bir ölü piksel dahi çıksa 3 iş günü içinde birebir kutulu değişim taahhüt edilir.
                        3. Garanti Süresi: 3 yıl resmi distribütör garantilidir.
                        4. Teslimat Süresi: 4 iş günüdür.
                        """,
                        Metadata.from(Map.of("doc_type", "contract", "supplier", "MegaBilisim A.Ş."))
                ),
                Document.from(
                        """
                        YÖNETMELİK: Kurumsal Satın Alma ve Yetki Onay Limitleri Prosedürü (Revizyon: 2026)
                        1. Standart Onay Limiti: 20 adede kadar veya 100.000 TL'ye kadar olan siparişler Birim Amiri tarafından onaylanır.
                        2. Direktör Onayı: 20 adedi AŞAN veya toplam tutarı 100.000 TL'yi AŞAN tüm satın alma taleplerinde Genel Müdür / Departman Direktörü onayı (DIRECTOR_APPROVAL_REQUIRED) zorunludur.
                        3. Acil Sipariş İstisnası: Stok seviyesi kritik eşiğin altına düşen ürünlerde sipariş taslağı beklemeden derhal oluşturulmalıdır.
                        """,
                        Metadata.from(Map.of("doc_type", "policy", "scope", "internal"))
                )
        );

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .build();

        ingestor.ingest(corporateDocuments);
        log.info("✅ Kurumsal dokümanlar başarıyla vektörleştirildi (RAG Store hazır).");
    }

    @Bean
    public ContentRetriever contentRetriever() {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(2)
                .minScore(0.3)
                .build();
    }

    public String searchContracts(String query) {
        if (query == null || query.isBlank()) {
            return "Arama sorgusu belirtilmedi.";
        }
        try {
            ContentRetriever retriever = contentRetriever();
            List<dev.langchain4j.rag.content.Content> contents = retriever.retrieve(dev.langchain4j.rag.query.Query.from(query));
            if (contents == null || contents.isEmpty()) {
                return "Aradığınız konuyla ('" + query + "') ilgili kurumsal sözleşmelerde veya politikalarda bir madde bulunamadı.";
            }
            StringBuilder sb = new StringBuilder("Kurumsal Bilgi Tabanı (İlgili Sözleşme & Yönetmelik Maddeleri):\n");
            for (dev.langchain4j.rag.content.Content c : contents) {
                if (c != null && c.textSegment() != null) {
                    sb.append("• ").append(c.textSegment().text().trim()).append("\n\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Sözleşme arama hatası: {}", e.getMessage());
            return "Sözleşme taranırken hata oluştu: " + e.getMessage();
        }
    }
}
