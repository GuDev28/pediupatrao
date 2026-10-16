package com.umc.pediupatrao.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.umc.pediupatrao.entity.CampoAlterado;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private UsuarioLogado usuarioLogado;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final SessionRegistryImpl sessionRegistry = new SessionRegistryImpl();
    private UsuarioService service;

    @BeforeEach
    void configurar() {
        service = new UsuarioService(usuarioRepository, encoder, auditoriaService, usuarioLogado, sessionRegistry);
        lenient().when(usuarioLogado.getUsername()).thenReturn("admin");
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacao -> {
            Usuario usuario = invocacao.getArgument(0);
            if (usuario.getId() == null) {
                usuario.setId("u-novo");
            }
            return usuario;
        });
    }

    @Test
    void adminNaoPodeAlterarOProprioPerfil() {
        when(usuarioRepository.findById("u-admin")).thenReturn(Optional.of(usuario("u-admin", "admin", Perfil.ADMIN)));

        assertThatThrownBy(() -> service.atualizarUsuario("u-admin", dados("admin", null, Perfil.ATENDENTE)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio perfil");
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void naoPermiteRebaixarOUltimoAdmin() {
        when(usuarioRepository.findById("u2")).thenReturn(Optional.of(usuario("u2", "outroadmin", Perfil.ADMIN)));
        when(usuarioRepository.countByRole(Perfil.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> service.atualizarUsuario("u2", dados("outroadmin", null, Perfil.GERENTE)))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("pelo menos um ADMIN");
    }

    @Test
    void naoPermiteExcluirASiMesmo() {
        when(usuarioRepository.findById("u-admin")).thenReturn(Optional.of(usuario("u-admin", "admin", Perfil.ADMIN)));

        assertThatThrownBy(() -> service.deletarUsuario("u-admin"))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("próprio usuário");
        verify(usuarioRepository, never()).deleteById(anyString());
    }

    @Test
    void perfilEhObrigatorioEUsernameUnico() {
        assertThatThrownBy(() -> service.salvarUsuario(dados("novo.user", "senha123", null)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("perfil");

        when(usuarioRepository.existsByUsername("gerente01")).thenReturn(true);
        assertThatThrownBy(() -> service.salvarUsuario(dados("gerente01", "senha123", Perfil.GERENTE)))
                .isInstanceOf(RegraNegocioException.class).hasMessageContaining("Já existe");
    }

    @Test
    void cadastroCriptografaSenhaENaoAAudita() {
        Usuario salvo = service.salvarUsuario(dados("caixa02", "senhaSecreta1", Perfil.ATENDENTE));

        assertThat(salvo.getPassword()).isNotEqualTo("senhaSecreta1");
        assertThat(encoder.matches("senhaSecreta1", salvo.getPassword())).isTrue();

        List<CampoAlterado> campos = camposAuditados(TipoOperacao.INCLUSAO_USUARIO);
        for (CampoAlterado campo : campos) {
            assertThat(campo.getValorNovo()).doesNotContain("senhaSecreta1");
            assertThat(campo.getValorNovo()).isNotEqualTo(salvo.getPassword());
        }
    }

    @Test
    void trocaDePerfilAuditaEEncerraSessoesDoUsuario() {
        when(usuarioRepository.findById("u3")).thenReturn(Optional.of(usuario("u3", "atendente01", Perfil.ATENDENTE)));
        User principal = new User("atendente01", "x", List.of(new SimpleGrantedAuthority("ROLE_ATENDENTE")));
        sessionRegistry.registerNewSession("sessao-1", principal);

        service.atualizarUsuario("u3", dados("atendente01", "novaSenha9", Perfil.GERENTE));

        SessionInformation sessao = sessionRegistry.getSessionInformation("sessao-1");
        assertThat(sessao.isExpired()).isTrue();

        List<CampoAlterado> campos = camposAuditados(TipoOperacao.ALTERACAO_USUARIO);
        assertThat(campos).extracting(CampoAlterado::getCampo).containsExactly("perfil", "senha");
        assertThat(campos.get(0).getValorAnterior()).isEqualTo("ATENDENTE");
        assertThat(campos.get(0).getValorNovo()).isEqualTo("GERENTE");
        assertThat(campos.get(1).getValorNovo()).isEqualTo(CampoAlterado.VALOR_PROTEGIDO);
    }

    private Usuario usuario(String id, String username, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setUsername(username);
        usuario.setPassword(encoder.encode("senha123"));
        usuario.setRole(perfil);
        return usuario;
    }

    private Usuario dados(String username, String senha, Perfil perfil) {
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(senha);
        usuario.setRole(perfil);
        return usuario;
    }

    @SuppressWarnings("unchecked")
    private List<CampoAlterado> camposAuditados(TipoOperacao operacao) {
        ArgumentCaptor<List<CampoAlterado>> captor = ArgumentCaptor.forClass(List.class);
        verify(auditoriaService).registrar(eq(operacao), eq("Usuario"), anyString(), anyString(), captor.capture(), any());
        return captor.getValue();
    }

}
