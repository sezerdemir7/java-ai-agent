package com.aiagent.resilience;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Enterprise Resilience Pattern: Fallback / Failover Chat Model.
 * Birden fazla LLM sağlayıcısını (Gemini, Groq vb.) sırayla dener.
 * Herhangi bir sağlayıcıda hata (rate limit, timeout vb.) olduğunda
 * kesinti yaşatmadan otomatik olarak bir sonrakine geçer.
 */
public class FallbackChatModel implements ChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackChatModel.class);

    public record NamedModel(String name, ChatLanguageModel model) {}

    private final List<NamedModel> candidateModels = new ArrayList<>();

    public FallbackChatModel(List<NamedModel> models) {
        if (models != null) {
            for (NamedModel nm : models) {
                if (nm != null && nm.model() != null) {
                    this.candidateModels.add(nm);
                }
            }
        }
    }

    private <T> T executeWithFallback(String actionName, Function<ChatLanguageModel, T> action) {
        if (candidateModels.isEmpty()) {
            throw new IllegalStateException("Kullanılabilir hiçbir AI modeli bulunamadı! Lütfen GEMINI_API_KEY veya GROQ_API_KEY giriniz.");
        }

        Exception lastException = null;
        for (NamedModel namedModel : candidateModels) {
            try {
                log.info("AI İsteği yürütülüyor [Sağlayıcı: {}] -> {}", namedModel.name(), actionName);
                return action.apply(namedModel.model());
            } catch (Exception e) {
                lastException = e;
                log.warn("⚠️ [Sağlayıcı: {}] başarısız oldu! Hata: {}. Otomatik olarak yedek modele geçiliyor...",
                        namedModel.name(), e.getMessage());
            }
        }

        throw new RuntimeException("Tüm yapılandırılmış modeller (" + candidateModels.size() + " adet) başarısız oldu!", lastException);
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return executeWithFallback("generate(List<ChatMessage>)", model -> model.generate(messages));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications) {
        return executeWithFallback("generate(List<ChatMessage>, List<ToolSpecification>)",
                model -> model.generate(messages, toolSpecifications));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification toolSpecification) {
        return executeWithFallback("generate(List<ChatMessage>, ToolSpecification)",
                model -> model.generate(messages, toolSpecification));
    }

    @Override
    public ChatResponse chat(ChatRequest chatRequest) {
        return executeWithFallback("chat(ChatRequest)", model -> model.chat(chatRequest));
    }
}
