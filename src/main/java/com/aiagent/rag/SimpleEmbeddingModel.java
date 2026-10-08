package com.aiagent.rag;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Harici bir API anahtarı veya 100 MB'lık ağır ONNX modelleri olmadan
 * bellek içinde çalışan deterministik vektör üretici (Embedding Model).
 * Metinlerdeki terim frekanslarını ve n-gram hash'lerini 64 boyutlu normalize vektöre çevirir.
 */
public class SimpleEmbeddingModel implements EmbeddingModel {

    private static final int VECTOR_DIMENSION = 64;

    @Override
    public Response<Embedding> embed(String text) {
        float[] vector = createVector(text);
        return Response.from(new Embedding(vector));
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        return embed(textSegment.text());
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        for (TextSegment segment : textSegments) {
            embeddings.add(embed(segment).content());
        }
        return Response.from(embeddings);
    }

    @Override
    public int dimension() {
        return VECTOR_DIMENSION;
    }

    private float[] createVector(String text) {
        float[] vector = new float[VECTOR_DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }

        String[] words = text.toLowerCase().split("\\W+");
        for (String word : words) {
            if (word.isBlank()) continue;
            int hash = Math.abs(word.hashCode()) % VECTOR_DIMENSION;
            vector[hash] += 1.0f;
        }

        // L2 Normalizasyonu (Cosine similarity için)
        float sumSquares = 0.0f;
        for (float v : vector) {
            sumSquares += v * v;
        }

        if (sumSquares > 0) {
            float norm = (float) Math.sqrt(sumSquares);
            for (int i = 0; i < VECTOR_DIMENSION; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }
}
