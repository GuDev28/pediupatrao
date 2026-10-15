package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClienteServiceTest {

    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private ClienteService service;

    @BeforeEach
    void configurar() {
        service = new ClienteService(clienteRepository, pedidoRepository, auditoriaService);
        lenient().when(clienteRepository.save(any(Cliente.class))).thenAnswer(invocacao -> {
            Cliente cliente = invocacao.getArgument(0);
            if (cliente.getId() == null) {
                cliente.setId("novo-id");
            }
            return cliente;
        });
    }

    @Test
    void alteracaoDeEnderecoRegistraValorAnteriorENovo() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua das Flores", "(11) 98888-7777")));
        Cliente dados = cliente(null, "Av. Brasil", "(11) 98888-7777");

        Cliente salvo = service.atualizar("c1", dados);

        assertThat(salvo.getLogradouro()).isEqualTo("Av. Brasil");
        List<CampoAlterado> campos = camposAuditados(TipoOperacao.ALTERACAO_CLIENTE, "c1");
        assertThat(campos).hasSize(1);
        assertThat(campos.get(0).getCampo()).isEqualTo("logradouro");
        assertThat(campos.get(0).getValorAnterior()).isEqualTo("Rua das Flores");
        assertThat(campos.get(0).getValorNovo()).isEqualTo("Av. Brasil");
    }

    @Test
    void alteracaoDeTelefoneEhAuditada() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua das Flores", "(11) 98888-7777")));

        service.atualizar("c1", cliente(null, "Rua das Flores", "(11) 97777-6666"));

        List<CampoAlterado> campos = camposAuditados(TipoOperacao.ALTERACAO_CLIENTE, "c1");
        assertThat(campos).extracting(CampoAlterado::getCampo).containsExactly("telefone");
        assertThat(campos.get(0).getValorAnterior()).isEqualTo("(11) 98888-7777");
        assertThat(campos.get(0).getValorNovo()).isEqualTo("(11) 97777-6666");
    }

    @Test
    void semMudancasNaoGravaNemAudita() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua das Flores", "(11) 98888-7777")));

        // espaços extras e campos vazios não contam como alteração
        Cliente dados = cliente(null, "  Rua das Flores ", "(11) 98888-7777");
        dados.setComplemento("");
        service.atualizar("c1", dados);

        verify(clienteRepository, never()).save(any());
        verifyNoInteractions(auditoriaService);
    }

    @Test
    void inclusaoIgnoraIdInformadoEAudita() {
        Cliente dados = cliente("id-forjado", "Rua A", "(11) 3333-4444");

        Cliente salvo = service.criar(dados);

        assertThat(salvo.getId()).isEqualTo("novo-id");
        verify(auditoriaService).registrar(eq(TipoOperacao.INCLUSAO_CLIENTE), eq("Cliente"), eq("novo-id"),
                anyString(), any(), any());
    }

    @Test
    void validaTelefoneEEstado() {
        assertThatThrownBy(() -> service.criar(cliente(null, "Rua A", "1234")))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Telefone");
        Cliente estadoInvalido = cliente(null, "Rua A", "(11) 3333-4444");
        estadoInvalido.setEstado("São Paulo");
        assertThatThrownBy(() -> service.criar(estadoInvalido))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Estado");
    }

    @Test
    void naoExcluiClienteComPedidos() {
        when(clienteRepository.findById("c1")).thenReturn(Optional.of(cliente("c1", "Rua A", "(11) 3333-4444")));
        when(pedidoRepository.existsByClienteId("c1")).thenReturn(true);

        assertThatThrownBy(() -> service.excluir("c1"))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("pedidos");
        verify(clienteRepository, never()).deleteById(anyString());
    }

    private Cliente cliente(String id, String logradouro, String telefone) {
        Cliente cliente = new Cliente();
        cliente.setId(id);
        cliente.setNome("Maria Souza");
        cliente.setTelefone(telefone);
        cliente.setLogradouro(logradouro);
        cliente.setNumero("100");
        cliente.setCidade("Mogi das Cruzes");
        cliente.setEstado("SP");
        return cliente;
    }

    @SuppressWarnings("unchecked")
    private List<CampoAlterado> camposAuditados(TipoOperacao operacao, String id) {
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(operacao), eq("Cliente"), eq(id), anyString(), captor.capture(), any());
        return captor.getValue();
    }
}
