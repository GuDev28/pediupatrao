package com.umc.pediupatrao.config;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * Perfil "demo": popula um banco SEPARADO (pediupatrao_demo, ver application-demo.properties)
 * com clientes, produtos e um histórico de pedidos de exemplo para apresentar o dashboard.
 *
 * Só gera pedidos quando a coleção "pedidos" desse banco está vazia; nunca altera dados existentes.
 * Executar: mvnw spring-boot:run -Dspring-boot.run.profiles=demo
 */
@Configuration
@Profile("demo")
public class DemonstracaoDashboardConfig {

    private static final Logger log = LoggerFactory.getLogger(DemonstracaoDashboardConfig.class);

    @Value("${pediupatrao.demo.dias-historico:120}")
    private int diasHistorico;

    @Value("${pediupatrao.demo.semente:2026}")
    private long semente;

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    CommandLineRunner gerarHistoricoDemonstracao(PedidoRepository pedidoRepository,
            ClienteRepository clienteRepository, ProdutoRepository produtoRepository, Clock clock) {
        return args -> {
            if (pedidoRepository.count() > 0) {
                log.info("Perfil demo: a coleção de pedidos já tem dados; nenhum pedido de exemplo foi gerado.");
                return;
            }
            List<Produto> produtos = garantirProdutos(produtoRepository);
            List<Cliente> clientes = garantirClientes(clienteRepository);
            List<Pedido> pedidos = new GeradorPedidosDemonstracao(semente)
                    .gerar(diasHistorico, LocalDateTime.now(clock), produtos, clientes);
            pedidoRepository.saveAll(pedidos);
            log.info("Perfil demo: {} pedidos de exemplo gerados ({} dias de histórico).", pedidos.size(),
                    diasHistorico);
        };
    }

    private List<Produto> garantirProdutos(ProdutoRepository repository) {
        Object[][] catalogo = {
            {"Pizza Calabresa", "Pizza", 54.90}, {"Pizza Margherita", "Pizza", 52.90},
            {"Pizza Quatro Queijos", "Pizza", 59.90}, {"Pizza Frango com Catupiry", "Pizza", 57.90},
            {"Pizza Portuguesa", "Pizza", 58.90}, {"Pizza de Chocolate", "Pizza", 49.90},
            {"Esfiha de Carne", "Esfiha", 7.50}, {"Esfiha de Queijo", "Esfiha", 7.00},
            {"Refrigerante 2L", "Bebida", 14.00}, {"Suco Natural 500ml", "Bebida", 9.00},
            {"X-Burguer", "Lanche", 24.90}
        };
        List<Produto> existentes = repository.findAll();
        for (Object[] item : catalogo) {
            boolean existe = false;
            for (Produto produto : existentes) {
                if (item[0].equals(produto.getNome())) {
                    existe = true;
                    break;
                }
            }
            if (!existe) {
                Produto novo = new Produto();
                novo.setNome((String) item[0]);
                novo.setTipo((String) item[1]);
                novo.setPreco((Double) item[2]);
                novo.setAtivo(true);
                repository.save(novo);
            }
        }
        List<Produto> ativos = new ArrayList<>();
        for (Produto produto : repository.findAll()) {
            if (!Boolean.FALSE.equals(produto.getAtivo()) && produto.getPreco() != null && produto.getPreco() > 0) {
                ativos.add(produto);
            }
        }
        return ativos;
    }

    /** Clientes fictícios, criados apenas se o banco de demonstração não tiver nenhum. */
    private List<Cliente> garantirClientes(ClienteRepository repository) {
        if (repository.count() > 0) {
            return repository.findAll();
        }
        String[] nomes = {"Ana Paula Ribeiro", "Bruno Carvalho", "Camila Fernandes", "Diego Martins",
            "Eduarda Lopes", "Felipe Nogueira", "Gabriela Rocha", "Henrique Alves", "Isabela Duarte",
            "João Pedro Santos", "Larissa Moreira", "Marcos Teixeira"};
        List<Cliente> clientes = new ArrayList<>();
        for (int i = 0; i < nomes.length; i++) {
            Cliente cliente = new Cliente();
            cliente.setNome(nomes[i]);
            cliente.setTelefone(String.format("(11) 90000-%04d", 1000 + i));
            cliente.setLogradouro("Rua de Exemplo");
            cliente.setNumero(String.valueOf(100 + i * 10));
            cliente.setBairro("Centro");
            cliente.setCidade("Mogi das Cruzes");
            cliente.setEstado("SP");
            clientes.add(repository.save(cliente));
        }
        return clientes;
    }
}
