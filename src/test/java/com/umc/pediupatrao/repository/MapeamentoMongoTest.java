package com.umc.pediupatrao.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.HistoricoStatus;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Verifica, sem precisar de um MongoDB rodando, como as entidades são gravadas e lidas:
 * valores monetários como Decimal128, enums como texto, datas, itens e auditoria imutável.
 */
class MapeamentoMongoTest {

    private MappingMongoConverter conversor;

    @BeforeEach
    void configurar() {
        MongoCustomConversions conversoes = new MongoCustomConversions(new ArrayList<>());
        MongoMappingContext contexto = new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversoes.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        conversor = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, contexto);
        conversor.setCustomConversions(conversoes);
        conversor.afterPropertiesSet();
    }

    @Test
    void pedidoEhGravadoELidoSemPerderInformacoes() {
        LocalDateTime entrada = LocalDateTime.of(2026, 9, 23, 14, 35);
        Pedido pedido = new Pedido();
        pedido.setClienteId("c1");
        pedido.setClienteNome("Maria Souza");
        pedido.setTipoEntrega(TipoEntrega.ENTREGA);
        List<ItemPedido> itens = new ArrayList<>();
        itens.add(new ItemPedido("p1", "Pizza Calabresa", 2, new BigDecimal("54.90")));
        pedido.setItens(itens);
        pedido.setPercentualDesconto(new BigDecimal("10.00"));
        pedido.recalcularValores();
        pedido.setStatus(StatusPedido.RECEBIDO);
        pedido.setDataHoraEntrada(entrada);
        pedido.setRegistradoPor("atendente01");
        pedido.getHistorico().add(new HistoricoStatus(null, StatusPedido.RECEBIDO, entrada, "atendente01", "ATENDENTE"));

        Document documento = new Document();
        conversor.write(pedido, documento);

        assertThat(documento.get("valorTotal")).isInstanceOf(Decimal128.class);
        assertThat(documento.get("status")).isEqualTo("RECEBIDO");
        // campos calculados só para exibição não são gravados
        assertThat(documento).doesNotContainKeys("codigo", "proximoStatus", "resumoItens", "cancelado");

        Pedido lido = conversor.read(Pedido.class, documento);
        assertThat(lido.getValorTotal()).isEqualByComparingTo("98.82");
        assertThat(lido.getItens().get(0).getPrecoUnitario()).isEqualByComparingTo("54.90");
        assertThat(lido.getHistorico().get(0).getStatusNovo()).isEqualTo(StatusPedido.RECEBIDO);
        assertThat(lido.getDataHoraEntrada()).isEqualTo(entrada);
    }

    @Test
    void registroDeAuditoriaEhLidoComOsValoresOriginais() {
        List<CampoAlterado> campos = new ArrayList<>();
        campos.add(new CampoAlterado("logradouro", "Rua das Flores", "Av. Brasil"));
        RegistroAuditoria registro = new RegistroAuditoria(LocalDateTime.of(2026, 9, 23, 14, 35), "atendente01",
                "ATENDENTE", TipoOperacao.ALTERACAO_CLIENTE, "Cliente", "c1", "Dados alterados", campos, null);

        Document documento = new Document();
        conversor.write(registro, documento);
        RegistroAuditoria lido = conversor.read(RegistroAuditoria.class, documento);

        assertThat(lido.getOperacao()).isEqualTo(TipoOperacao.ALTERACAO_CLIENTE);
        assertThat(lido.getAlteracoes()).hasSize(1);
        assertThat(lido.getAlteracoes().get(0).getValorAnterior()).isEqualTo("Rua das Flores");
        assertThat(lido.getAlteracoes().get(0).getValorNovo()).isEqualTo("Av. Brasil");
    }

    @Test
    void perfilDoUsuarioEhGravadoComoTexto() {
        Usuario usuario = new Usuario();
        usuario.setUsername("gerente01");
        usuario.setRole(Perfil.GERENTE);

        Document documento = new Document();
        conversor.write(usuario, documento);

        assertThat(documento.get("role")).isEqualTo("GERENTE");
        assertThat(conversor.read(Usuario.class, documento).getRole()).isEqualTo(Perfil.GERENTE);
    }
}
