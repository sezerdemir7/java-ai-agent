# 🤖 java-ai-agent — Enterprise Resilient AI Agent (Spring Boot 3 + LangChain4j)

[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4%2B-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![LangChain4j](https://img.shields.io/badge/LangChain4j-0.36.2-blue.svg)](https://github.com/langchain4j/langchain4j)
[![Hibernate/JPA](https://img.shields.io/badge/JPA-Hibernate%207-red.svg)](https://hibernate.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**java-ai-agent**, kurumsal organizasyonlar için geliştirilmiş; **RAG (Vektör Tabanlı Kurumsal Bilgi & Sözleşme Arama)**, **SSE Streaming (Reaktif Harf Harf Yanıt)**, **Otonom Araç Kullanımı (Tool Calling)**, **Oturum Bazlı Hafıza (Session Chat Memory)**, **H2 JPA/RDBMS Envanter Yönetimi** ve **Çoklu Model Hata Toleransı (Resilient Failover)** sunan production-ready bir Java AI Ajan mimarisidir.

---

## 🏛️ Mimari Şema

```
                                      +---------------------------------------------+
                                      |          Web UI / REST API / SSE            |
                                      +---------------------------------------------+
                                                             |
                                                             v
                                      +---------------------------------------------+
                                      |            com.aiagent.agent.Agent          |
                                      |      (LangChain4j Declarative Service)      |
                                      +---------------------------------------------+
                                        /                    |                    \
                                       /                     |                     \
                                      v                      v                      v
         +------------------------------------+  +----------------------+  +---------------------+
         |     com.aiagent.resilience.        |  |  com.aiagent.rag.    |  | com.aiagent.tools.  |
         |  FallbackChatModel & Streaming     |  |   KnowledgeService   |  |        Tools        |
         +------------------------------------+  +----------------------+  +---------------------+
             /                  \             \              |                         |
            v                    v             \             v                         v
+------------------------+ +-----------------+  \  +-------------------+   +---------------------+
|     Google Gemini      | |  Groq (Llama-3) |   \ | InMemoryEmbedding |   | Spring Data JPA Repo|
|    (Primary LLM)       | | (Failover LLM)  |    \| Store (Vektör DB) |   +---------------------+
+------------------------+ +-----------------+     +-------------------+              |
            \                      /                         |                        v
             \                    /                          v             +---------------------+
              v                  v               [Tedarikçi Sözleşmeleri,  | H2 In-Memory RDBMS  |
         +------------------------------------+   Garanti ve Yönetmelik]   | - products          |
         |    AuditService (Observability)    |--------------------------->| - purchase_orders   |
         |   - Token Usage (Input/Output)     |                            | - agent_audit_logs  |
         |   - Latency (ms) & Tool Tracing    |                            +---------------------+
         +------------------------------------+
```

---

## 🌟 Öne Çıkan Mühendislik Yetenekleri

### 1. Enterprise RAG & Vektör Veritabanı (Retrieval-Augmented Generation)
Ajan sadece SQL tablosundaki sayılara bakmaz; şirketin hukuki ve operasyonel metinlerini semantik olarak anlar:
* **Tedarikçi Çerçeve Sözleşmeleri:** TeknoTedarik, OfisDepo ve MegaBilisim firmalarıyla yapılan anlaşma maddeleri (teslimat süreleri, gecikme cezaları, piksel garantileri) `InMemoryEmbeddingStore` içerisine vektörleştirilir.
* Kullanıcı bir sözleşme maddesi sorduğunda, `ContentRetriever` devreye girerek en alakalı maddeleri çıkarır ve modele bağlam olarak sunar.

### 2. Server-Sent Events (SSE) ile Gerçek Zamanlı Streaming
* Kullanıcı uzun cevapları beklemek zorunda kalmaz; `TokenStream` ve Spring `SseEmitter` aracılığıyla yanıtlar ekrana harf harf akar (ChatGPT arayüzü deneyimi).

### 3. Spring Data JPA & H2 RDBMS Entegrasyonu
Veriler bellek içi geçici listeler yerine gerçek JPA Entity'leri ile yönetilir:
* **`Product`**: Ürün kodu, stok adedi, kritik eşik, tedarikçi ve birim fiyat.
* **`PurchaseOrder`**: Ajan tarafından açılan satın alma sipariş taslakları, toplam tutar ve onay statüsü.
* **`AgentAuditLog`**: Ajanın yaptığı her hareketin, harcanan token'ların ve gecikme süresinin saklandığı denetim tablosu.

### 4. Full Observability & Audit Trail (Denetim İzi ve Token Takibi)
* LangChain4j'nin `ChatModelListener` arayüzü kullanılarak her AI çağrısının:
  * Hangi model tarafından cevaplandığı,
  * Kaç **giriş (input)**, **çıkış (output)** ve **toplam token** yaktığı,
  * İstek süresinin kaç milisaniye (`executionDurationMs`) sürdüğü,
  * Hangi `@Tool` metodlarının hangi parametrelerle çalıştırıldığı veritabanına otomatik kaydedilir.

### 5. Resilient Failover Pattern (Çoklu Model Yedekleme)
* `FallbackChatModel` ve `FallbackStreamingChatModel` sayesinde sistem **Triple-LLM Resilience (Gemini, Groq Llama-3, OpenAI GPT-4o)** destekler.
* İstekler öncelikle **Google Gemini** modeline yönlendirilir.
* Eğer Gemini'de kota dolumu, oran sınırı (429 Rate Limit) veya kesinti yaşanırsa, sistem kullanıcıya hata yansıtmadan **otomatik olarak Groq (Llama-3)** veya **OpenAI (GPT-4o)** modeline geçer.

### 6. Enterprise Guardrails (İş Kuralları & Onay Mekanizması)
* Sipariş adedi 20'nin üzerinde veya toplam tutar 100.000 TL'den fazla ise siparişe doğrudan onay verilmez; sistem otonom olarak `DIREKTOR_ONAYI_BEKLIYOR` statüsüne çeker.

---

## 🚀 Hızlı Başlangıç

### Gereksinimler
* Java 21 LTS
* (Opsiyonel) Google Gemini, Groq veya OpenAI API anahtarlarından en az biri

### 1. Konfigürasyon
`.env.example` dosyasını kopyalayıp `.env` oluşturun ve API anahtarınızı tanımlayın:

```bash
cp .env.example .env
```

`.env` içeriği:
```env
# Google Gemini API Anahtarı (Öncelikli)
GEMINI_API_KEY=your_gemini_api_key_here

# Groq API Anahtarı (Yedekleme / Fallback)
GROQ_API_KEY=your_groq_api_key_here

# OpenAI API Anahtarı (Opsiyonel)
OPENAI_API_KEY=your_openai_api_key_here
```

> **Not:** API anahtarlarınız `.gitignore` ile korunmaktadır ve asla GitHub'a gönderilmez.

### 2. Çalıştırma
Projeyi derleyip ayağa kaldırmak için terminalde şu komutu çalıştırın:

```bash
# Windows
.\mvnw.cmd spring-boot:run

# Linux / Mac
./mvnw spring-boot:run
```

Uygulama başladıktan sonra tarayıcınızdan **`http://localhost:8080`** adresine gidin.
H2 Veritabanı konsolu: **`http://localhost:8080/h2-console`** (JDBC URL: `jdbc:h2:mem:agentusdb`)

---

## 📂 Paket Mimarisi

```
com.aiagent
├── agent/            # Agent Interface (LangChain4j AiServices deklarasyonu)
├── config/           # Spring & AI Bean yapılandırmaları ve DataInitializer
├── controller/       # REST API & SSE Streaming Uç Noktaları
├── domain/           # JPA Entity'leri (Product, PurchaseOrder, AgentAuditLog)
├── rag/              # KnowledgeService (Vektör tabanlı RAG bilgi bankası)
├── repository/       # Spring Data JPA Repository arayüzleri
├── resilience/       # FallbackChatModel & FallbackStreamingChatModel
├── service/          # AuditService (Observability & token listener)
└── tools/            # Enterprise Business Tools (@Tool servisleri)
```

---

## 📄 Lisans
Bu proje [MIT](LICENSE) lisansı ile lisanslanmıştır.
