package com.umc.pediupatrao.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
@Profile("!test")
public class MongoInitConfig implements CommandLineRunner {

    private static final String[] COLECOES = {
        "pedidos", "clientes", "usuarios", "produtos", "auditoria"
    };

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) throws Exception {
        for (String colecao : COLECOES) {
            if (!mongoTemplate.collectionExists(colecao)) {
                mongoTemplate.createCollection(colecao);
            }
        }
    }
}
