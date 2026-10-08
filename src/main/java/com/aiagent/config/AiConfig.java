package com.aiagent.config;

import com.aiagent.agent.Agent;
import com.aiagent.resilience.FallbackChatModel;
import com.aiagent.resilience.FallbackStreamingChatModel;
import com.aiagent.service.AuditService;
import com.aiagent.tools.Tools;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.model-name:gemini-1.5-flash}")
    private String geminiModelName;

    @Value("${groq.api-key:}")
    private String groqApiKey;

    @Value("${groq.model-name:llama-3.3-70b-versatile}")
    private String groqModelName;

    @Value("${groq.base-url:https://api.groq.com/openai/v1}")
    private String groqBaseUrl;

    @Value("${openai.api-key:}")
    private String openAiApiKey;

    @Value("${openai.model-name:gpt-4o-mini}")
    private String openAiModelName;

    @Value("${openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    @Bean
    public ChatLanguageModel chatLanguageModel(AuditService auditService) {
        List<FallbackChatModel.NamedModel> models = new ArrayList<>();

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                ChatLanguageModel geminiModel = GoogleAiGeminiChatModel.builder()
                        .apiKey(geminiApiKey.trim())
                        .modelName(geminiModelName)
                        .temperature(0.2)
                        .timeout(Duration.ofSeconds(60))
                        .logRequestsAndResponses(true)
                        .listeners(List.of(auditService))
                        .build();

                models.add(new FallbackChatModel.NamedModel("Google Gemini (" + geminiModelName + ")", geminiModel));
                log.info(" Google Gemini modeli aktif edildi.");
            } catch (Exception e) {
                log.error("Google Gemini yapılandırılamadı: {}", e.getMessage());
            }
        }

        if (groqApiKey != null && !groqApiKey.isBlank()) {
            try {
                ChatLanguageModel groqModel = OpenAiChatModel.builder()
                        .baseUrl(groqBaseUrl)
                        .apiKey(groqApiKey.trim())
                        .modelName(groqModelName)
                        .temperature(0.2)
                        .timeout(Duration.ofSeconds(60))
                        .logRequests(true)
                        .logResponses(true)
                        .listeners(List.of(auditService))
                        .build();

                models.add(new FallbackChatModel.NamedModel("Groq Llama-3 (" + groqModelName + ")", groqModel));
                log.info(" Groq (Llama-3) modeli aktif edildi.");
            } catch (Exception e) {
                log.error("Groq modeli yapılandırılamadı: {}", e.getMessage());
            }
        }

        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            try {
                ChatLanguageModel openAiModel = OpenAiChatModel.builder()
                        .baseUrl(openAiBaseUrl)
                        .apiKey(openAiApiKey.trim())
                        .modelName(openAiModelName)
                        .temperature(0.2)
                        .timeout(Duration.ofSeconds(60))
                        .logRequests(true)
                        .logResponses(true)
                        .listeners(List.of(auditService))
                        .build();

                models.add(new FallbackChatModel.NamedModel("OpenAI (" + openAiModelName + ")", openAiModel));
                log.info(" OpenAI ({}) modeli aktif edildi.", openAiModelName);
            } catch (Exception e) {
                log.error("OpenAI modeli yapılandırılamadı: {}", e.getMessage());
            }
        }

        if (models.isEmpty()) {
            log.warn("⚠️ DİKKAT: Ne GEMINI_API_KEY ne GROQ_API_KEY ne de OPENAI_API_KEY tanımlanmış.");
            return new ChatLanguageModel() {
                @Override
                public Response<AiMessage> generate(List<ChatMessage> messages) {
                    return Response.from(AiMessage.from(
                            "⚠️ Henüz bir API anahtarı tanımlamadınız!\n" +
                            "Lütfen '.env' dosyasına GEMINI_API_KEY, GROQ_API_KEY veya OPENAI_API_KEY ekleyiniz."
                    ));
                }
            };
        }

        return new FallbackChatModel(models);
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel() {
        List<FallbackStreamingChatModel.NamedStreamingModel> streamingModels = new ArrayList<>();

        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            try {
                StreamingChatLanguageModel geminiStreaming = GoogleAiGeminiStreamingChatModel.builder()
                        .apiKey(geminiApiKey.trim())
                        .modelName(geminiModelName)
                        .temperature(0.2)
                        .timeout(Duration.ofSeconds(60))
                        .build();

                streamingModels.add(new FallbackStreamingChatModel.NamedStreamingModel("Google Gemini Streaming", geminiStreaming));
                log.info("🌊 Google Gemini Streaming modeli aktif edildi.");
            } catch (Exception e) {
                log.warn("Gemini streaming modeli başlatılamadı: {}", e.getMessage());
            }
        }

        if (groqApiKey != null && !groqApiKey.isBlank()) {
            try {
                StreamingChatLanguageModel groqStreaming = OpenAiStreamingChatModel.builder()
                        .baseUrl(groqBaseUrl)
                        .apiKey(groqApiKey.trim())
                        .modelName(groqModelName)
                        .temperature(0.2)
                        .timeout(Duration.ofSeconds(60))
                        .build();

                streamingModels.add(new FallbackStreamingChatModel.NamedStreamingModel("Groq Llama-3 Streaming", groqStreaming));
                log.info("🌊 Groq (Llama-3) Streaming modeli aktif edildi.");
            } catch (Exception e) {
                log.warn("Groq streaming modeli başlatılamadı: {}", e.getMessage());
            }
        }

        if (openAiApiKey != null && !openAiApiKey.isBlank()) {
            try {
                StreamingChatLanguageModel openAiStreaming = OpenAiStreamingChatModel.builder()
                        .baseUrl(openAiBaseUrl)
                        .apiKey(openAiApiKey.trim())
                        .modelName(openAiModelName)
                        .temperature(0.2)
                        .timeout(Duration.ofSeconds(60))
                        .build();

                streamingModels.add(new FallbackStreamingChatModel.NamedStreamingModel("OpenAI Streaming (" + openAiModelName + ")", openAiStreaming));
                log.info("🌊 OpenAI Streaming ({}) modeli aktif edildi.", openAiModelName);
            } catch (Exception e) {
                log.warn("OpenAI streaming modeli başlatılamadı: {}", e.getMessage());
            }
        }

        if (streamingModels.isEmpty()) {
            return (messages, handler) -> {
                handler.onNext("⚠️ Henüz bir API anahtarı tanımlamadınız! Lütfen GEMINI_API_KEY, GROQ_API_KEY veya OPENAI_API_KEY giriniz.");
                handler.onComplete(Response.from(AiMessage.from("API anahtarı eksik.")));
            };
        }

        return new FallbackStreamingChatModel(streamingModels);
    }

    @Bean
    public Agent agent(ChatLanguageModel chatLanguageModel,
                       StreamingChatLanguageModel streamingChatLanguageModel,
                       Tools tools) {
        log.info("🤖 LangChain4j AiServices ile Agent (Tools + Agentic RAG + Streaming) derleniyor...");
        return AiServices.builder(Agent.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(tools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(20))
                .build();
    }
}
