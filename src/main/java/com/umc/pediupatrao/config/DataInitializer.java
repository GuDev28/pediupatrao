package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Inicialização dos dados:
 * 1) migra perfis antigos (USER ou vazio) para os novos perfis;
 * 2) cria um usuário de cada perfil para demonstração, se ainda não existirem;
 * 3) cadastra alguns produtos de exemplo quando a coleção estiver vazia.
 *
 * As senhas iniciais vêm de application.properties e nunca são escritas no log.
 */
@Configuration
@Profile("!test")
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Value("${pediupatrao.dados-demonstracao:true}")
    private boolean criarDadosDemonstracao;

    @Value("${pediupatrao.senha-inicial.admin:teste123}")
    private String senhaAdmin;

    @Value("${pediupatrao.senha-inicial.gerente:gerente123}")
    private String senhaGerente;

    @Value("${pediupatrao.senha-inicial.atendente:atendente123}")
    private String senhaAtendente;

    @Bean
    CommandLineRunner initDatabase(UsuarioRepository repo, ProdutoRepository produtoRepository,
            MongoTemplate mongoTemplate, PasswordEncoder encoder) {
        return args -> {
            migrarPerfisAntigos(mongoTemplate);

            criarUsuarioSeNaoExistir(repo, encoder, "admin", senhaAdmin, Perfil.ADMIN);
            if (criarDadosDemonstracao) {
                criarUsuarioSeNaoExistir(repo, encoder, "gerente01", senhaGerente, Perfil.GERENTE);
                criarUsuarioSeNaoExistir(repo, encoder, "atendente01", senhaAtendente, Perfil.ATENDENTE);
                criarProdutosDeExemplo(produtoRepository);
            }
        };
    }

    /**
     * Na versão anterior, os usuários tinham role "USER" (ou nenhuma). O admin original
     * passa a ADMIN; os demais recebem o perfil de menor privilégio (ATENDENTE).
     */
    private void migrarPerfisAntigos(MongoTemplate mongoTemplate) {
        List<String> perfisValidos = new ArrayList<>();
        for (Perfil perfil : Perfil.values()) {
            perfisValidos.add(perfil.name());
        }

        long admins = mongoTemplate.updateMulti(
                Query.query(Criteria.where("username").is("admin").and("role").nin(perfisValidos)),
                Update.update("role", Perfil.ADMIN.name()), "usuarios").getModifiedCount();

        long demais = mongoTemplate.updateMulti(
                Query.query(Criteria.where("role").nin(perfisValidos)),
                Update.update("role", Perfil.ATENDENTE.name()), "usuarios").getModifiedCount();

        if (admins + demais > 0) {
            log.info("Perfis migrados para o novo modelo: {} admin(s), {} usuário(s) como ATENDENTE", admins, demais);
        }
    }

    private void criarUsuarioSeNaoExistir(UsuarioRepository repo, PasswordEncoder encoder,
            String username, String senha, Perfil perfil) {
        if (repo.findByUsername(username).isEmpty()) {
            Usuario usuario = new Usuario();
            usuario.setUsername(username);
            usuario.setPassword(encoder.encode(senha));
            usuario.setRole(perfil);
            repo.save(usuario);
            log.info("Usuário inicial criado: {} ({})", username, perfil);
        }
    }

    private void criarProdutosDeExemplo(ProdutoRepository produtoRepository) {
        if (produtoRepository.count() > 0) {
            return;
        }
        produtoRepository.save(produto("Pizza Calabresa", "Pizza", 54.90));
        produtoRepository.save(produto("Pizza Margherita", "Pizza", 52.90));
        produtoRepository.save(produto("Pizza Quatro Queijos", "Pizza", 59.90));
        produtoRepository.save(produto("Esfiha de Carne", "Esfiha", 7.50));
        produtoRepository.save(produto("Refrigerante 2L", "Bebida", 14.00));
        log.info("Produtos de exemplo cadastrados");
    }

    private Produto produto(String nome, String tipo, double preco) {
        Produto produto = new Produto();
        produto.setNome(nome);
        produto.setTipo(tipo);
        produto.setPreco(preco);
        produto.setAtivo(true);
        return produto;
    }
}
