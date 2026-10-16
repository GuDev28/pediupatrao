package com.umc.pediupatrao.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.repository.RegistroAuditoriaRepository;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.AuditoriaService;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Segunda camada de proteção: mesmo que algum controlador chame o serviço
 * diretamente, o @PreAuthorize dos serviços bloqueia perfis não autorizados.
 */
@SpringJUnitConfig(AutorizacaoServicosTest.Configuracao.class)
class AutorizacaoServicosTest {

    @Configuration
    @EnableMethodSecurity
    static class Configuracao {

        @Bean
        UsuarioLogado usuarioLogado() {
            return new UsuarioLogado();
        }

        @Bean
        AuditoriaService auditoriaService(UsuarioLogado usuarioLogado) {
            return new AuditoriaService(mock(RegistroAuditoriaRepository.class), mock(MongoTemplate.class),
                    usuarioLogado, Clock.systemDefaultZone());
        }

        @Bean
        PedidoService pedidoService(AuditoriaService auditoria, UsuarioLogado usuarioLogado) {
            return new PedidoService(mock(PedidoRepository.class), mock(ClienteRepository.class),
                    mock(ProdutoRepository.class), auditoria, usuarioLogado, Clock.systemDefaultZone());
        }

        @Bean
        ClienteService clienteService(AuditoriaService auditoria) {
            return new ClienteService(mock(ClienteRepository.class), mock(PedidoRepository.class), auditoria);
        }

        @Bean
        UsuarioService usuarioService(AuditoriaService auditoria, UsuarioLogado usuarioLogado) {
            return new UsuarioService(mock(UsuarioRepository.class), new BCryptPasswordEncoder(4), auditoria,
                    usuarioLogado, new SessionRegistryImpl());
        }
    }

    @Autowired
    private PedidoService pedidoService;
    @Autowired
    private ClienteService clienteService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private AuditoriaService auditoriaService;

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminNaoCancelaNemDaDescontoMesmoChamandoOServico() {
        autenticar("admin", Perfil.ADMIN);

        assertThatThrownBy(() -> pedidoService.cancelar("p1", "Justificativa qualquer"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> pedidoService.aplicarDesconto("p1", BigDecimal.TEN, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void atendenteNaoCancelaNemConsultaAuditoria() {
        autenticar("atendente01", Perfil.ATENDENTE);

        assertThatThrownBy(() -> pedidoService.cancelar("p1", "Justificativa qualquer"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auditoriaService.consultar(null, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void gerentePassaPelaAutorizacaoDoCancelamento() {
        autenticar("gerente01", Perfil.GERENTE);

        // Passou da autorização: o erro agora é de negócio (o pedido não existe no repositório simulado)
        assertThatThrownBy(() -> pedidoService.cancelar("p1", "Justificativa qualquer"))
                .isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void somenteAdminAdministraUsuarios() {
        autenticar("gerente01", Perfil.GERENTE);
        assertThatThrownBy(() -> usuarioService.listarTodos()).isInstanceOf(AccessDeniedException.class);

        autenticar("atendente01", Perfil.ATENDENTE);
        assertThatThrownBy(() -> usuarioService.salvarUsuario(null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminEGerenteNaoCadastramClientes() {
        autenticar("admin", Perfil.ADMIN);
        assertThatThrownBy(() -> clienteService.criar(new Cliente())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> clienteService.listarClientes()).isInstanceOf(AccessDeniedException.class);

        autenticar("gerente01", Perfil.GERENTE);
        assertThatThrownBy(() -> clienteService.atualizar("c1", new Cliente())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void semAutenticacaoNadaPassa() {
        assertThatThrownBy(() -> pedidoService.listarPedidos())
                .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    private void autenticar(String usuario, Perfil perfil) {
        List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(perfil.getAutoridade()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(usuario, null, autoridades));
    }
}
