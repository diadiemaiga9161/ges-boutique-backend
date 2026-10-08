package com.ges.boutique.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "produits",
                "categories",
                "fournisseurs",
                "clients",
                "boutique",
                "boutiques",
                "employes",
                "rapports",
                "ventes",
                "ventes-resume",
                "inventaire",
                "boutiques-partenaires",
                "fonctionnalites",
                "permissionsVendeur",
                "rolesBoutique"
        );
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .recordStats()
        );
        return manager;
    }
}
