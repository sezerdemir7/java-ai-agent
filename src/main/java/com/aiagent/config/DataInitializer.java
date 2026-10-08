package com.aiagent.config;

import com.aiagent.domain.Product;
import com.aiagent.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDatabase(ProductRepository productRepository) {
        return args -> {
            if (productRepository.count() == 0) {
                log.info("📦 H2 Veritabanı başlatılıyor: Başlangıç ürün verileri yükleniyor...");

                productRepository.saveAll(List.of(
                        Product.builder()
                                .code("PRD-101")
                                .name("Lenovo ThinkPad X1 Laptop")
                                .stock(4)
                                .criticalThreshold(10)
                                .supplier("TeknoTedarik A.Ş.")
                                .price(32000.0)
                                .build(),
                        Product.builder()
                                .code("PRD-102")
                                .name("Logitech MX Master 3S Mouse")
                                .stock(45)
                                .criticalThreshold(15)
                                .supplier("OfisDepo Ltd.")
                                .price(1850.0)
                                .build(),
                        Product.builder()
                                .code("PRD-103")
                                .name("Dell UltraSharp 27 inç 4K Monitör")
                                .stock(2)
                                .criticalThreshold(5)
                                .supplier("MegaBilisim A.Ş.")
                                .price(14500.0)
                                .build(),
                        Product.builder()
                                .code("PRD-104")
                                .name("Herman Miller Ergonomik Koltuk")
                                .stock(18)
                                .criticalThreshold(5)
                                .supplier("BuroMobilya A.Ş.")
                                .price(18000.0)
                                .build()
                ));

                log.info("✅ Başlangıç ürünleri H2 veritabanına başarıyla kaydedildi.");
            }
        };
    }
}
