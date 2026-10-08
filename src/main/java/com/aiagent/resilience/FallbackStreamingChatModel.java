package com.aiagent.resilience;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Enterprise Resilience Pattern: Fallback Streaming Chat Model.
 * Streaming (SSE) isteklerinde sağlayıcı hatası olduğunda
 * otomatik olarak yedek sağlayıcıya geçiş yapar.
 */
public class FallbackStreamingChatModel implements StreamingChatLanguageModel {

    private static final Logger log = LoggerFactory.getLogger(FallbackStreamingChatModel.class);

    public record NamedStreamingModel(String name, StreamingChatLanguageModel model) {}

    private final List<NamedStreamingModel> candidateModels = new ArrayList<>();

    public FallbackStreamingChatModel(List<NamedStreamingModel> models) {
        if (models != null) {
            for (NamedStreamingModel nm : models) {
                if (nm != null && nm.model() != null) {
                    this.candidateModels.add(nm);
                }
            }
        }
    }

    @Override
    public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
        generateWithFallback(0, messages, null, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecifications, StreamingResponseHandler<AiMessage> handler) {
        generateWithFallback(0, messages, toolSpecifications, handler);
    }

    @Override
    public void generate(List<ChatMessage> messages, ToolSpecification toolSpecification, StreamingResponseHandler<AiMessage> handler) {
        generateWithFallback(0, messages, List.of(toolSpecification), handler);
    }

    private void generateWithFallback(int modelIndex,
                                      List<ChatMessage> messages,
                                      List<ToolSpecification> tools,
                                      StreamingResponseHandler<AiMessage> handler) {
        if (candidateModels.isEmpty()) {
            handler.onNext("⚠️ Henüz bir API anahtarı tanımlamadınız! Lütfen GEMINI_API_KEY ya da GROQ_API_KEY giriniz.");
            handler.onComplete(Response.from(AiMessage.from("API anahtarı eksik.")));
            return;
        }

        if (modelIndex >= candidateModels.size()) {
            handler.onError(new RuntimeException("Tüm yapılandırılmış streaming modelleri başarısız oldu!"));
            return;
        }

        NamedStreamingModel current = candidateModels.get(modelIndex);
        log.info("🌊 Streaming AI İsteği yürütülüyor [Sağlayıcı: {}]", current.name());

        try {
            StreamingResponseHandler<AiMessage> proxyHandler = new StreamingResponseHandler<>() {
                private boolean tokenReceived = false;

                @Override
                public void onNext(String token) {
                    tokenReceived = true;
                    handler.onNext(token);
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    handler.onComplete(response);
                }

                @Override
                public void onError(Throwable error) {
                    // Eğer henüz kullanıcıya token gitmediyse, bir sonraki modele geçebiliriz
                    if (!tokenReceived && modelIndex + 1 < candidateModels.size()) {
                        log.warn("⚠️ Streaming [Sağlayıcı: {}] hata verdi: {}. Sonraki modele geçiliyor...",
                                current.name(), error.getMessage());
                        generateWithFallback(modelIndex + 1, messages, tools, handler);
                    } else {
                        handler.onError(error);
                    }
                }
            };

            if (tools != null && !tools.isEmpty()) {
                current.model().generate(messages, tools, proxyHandler);
            } else {
                current.model().generate(messages, proxyHandler);
            }
        } catch (Exception e) {
            log.warn("⚠️ Streaming başlatılırken hata oluştu [Model: {}]: {}. Sonrakine geçiliyor...",
                    current.name(), e.getMessage());
            generateWithFallback(modelIndex + 1, messages, tools, handler);
        }
    }
}
