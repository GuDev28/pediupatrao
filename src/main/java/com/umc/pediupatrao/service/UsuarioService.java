package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.UsuarioRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Administração de usuários e perfis (RF01). Todos os métodos exigem ADMIN.
 *
 * Proteções contra atribuição indevida de privilégios:
 * - somente ADMIN cadastra usuários ou altera perfis (rota e método protegidos);
 * - o perfil precisa ser um dos três existentes (enum Perfil);
 * - ninguém altera o próprio perfil/username nem exclui a si mesmo;
 * - o último ADMIN não pode ser rebaixado nem excluído;
 * - ao alterar perfil, senha ou excluir um usuário, as sessões abertas dele são encerradas,
 *   para que as permissões antigas não continuem valendo.
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioService {

    private static final int SENHA_MINIMA = 6;
    private static final int SENHA_MAXIMA = 72; // limite do BCrypt

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoriaService;
    private final UsuarioLogado usuarioLogado;
    private final SessionRegistry sessionRegistry;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
            AuditoriaService auditoriaService, UsuarioLogado usuarioLogado, SessionRegistry sessionRegistry) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditoriaService = auditoriaService;
        this.usuarioLogado = usuarioLogado;
        this.sessionRegistry = sessionRegistry;
    }

    // Busca um usuário por ID
    public Optional<Usuario> buscarPorId(String id) {
        return usuarioRepository.findById(id);
    }

    // Busca um usuário por username
    public Optional<Usuario> buscarPorUsername(String username) {
        return usuarioRepository.findByUsername(username);
    }

    // Lista todos os usuários
    public List<Usuario> listarTodos() {
        return usuarioRepository.findAll();
    }

    /** Cadastra um novo usuário (senha criptografada com BCrypt). */
    public Usuario salvarUsuario(Usuario dados) {
        if (dados == null) {
            throw new RegraNegocioException("Dados do usuário não informados.");
        }
        String username = validarUsername(dados.getUsername());
        if (usuarioRepository.existsByUsername(username)) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + username + "'.");
        }
        validarSenha(dados.getPassword(), true);
        Perfil perfil = validarPerfil(dados.getRole());

        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setPassword(passwordEncoder.encode(dados.getPassword()));
        usuario.setRole(perfil);
        Usuario salvo = usuarioRepository.save(usuario);

        Alteracoes alteracoes = new Alteracoes()
                .incluir("username", salvo.getUsername())
                .incluir("perfil", salvo.getRole());
        auditoriaService.registrar(TipoOperacao.INCLUSAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                salvo.getId(), "Usuário " + salvo.getUsername() + " cadastrado", alteracoes.getCampos(), null);
        return salvo;
    }

    /** Atualiza username, perfil e (opcionalmente) a senha de outro usuário. */
    public Usuario atualizarUsuario(String id, Usuario dados) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (dados == null) {
            throw new RegraNegocioException("Dados do usuário não informados.");
        }
        boolean proprioUsuario = existente.getUsername().equals(usuarioLogado.getUsername());

        String novoUsername = validarUsername(dados.getUsername());
        Perfil novoPerfil = validarPerfil(dados.getRole());

        if (proprioUsuario && !novoUsername.equals(existente.getUsername())) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio username.");
        }
        if (proprioUsuario && novoPerfil != existente.getRole()) {
            throw new RegraNegocioException("Você não pode alterar o seu próprio perfil. "
                    + "A mudança deve ser feita por outro administrador.");
        }
        if (!novoUsername.equals(existente.getUsername()) && usuarioRepository.existsByUsername(novoUsername)) {
            throw new RegraNegocioException("Já existe um usuário com o username '" + novoUsername + "'.");
        }
        if (existente.getRole() == Perfil.ADMIN && novoPerfil != Perfil.ADMIN
                && usuarioRepository.countByRole(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("O sistema precisa de pelo menos um ADMIN.");
        }

        boolean trocarSenha = dados.getPassword() != null && !dados.getPassword().isBlank();
        if (trocarSenha) {
            validarSenha(dados.getPassword(), true);
        }

        Alteracoes alteracoes = new Alteracoes()
                .comparar("username", existente.getUsername(), novoUsername)
                .comparar("perfil", existente.getRole(), novoPerfil);
        if (trocarSenha) {
            // O CampoAlterado mascara automaticamente campos de senha: só registra que houve troca.
            alteracoes.comparar("senha", "anterior", "alterada");
        }
        if (alteracoes.isVazia()) {
            return existente;
        }

        String usernameAnterior = existente.getUsername();
        existente.setUsername(novoUsername);
        existente.setRole(novoPerfil);
        if (trocarSenha) {
            existente.setPassword(passwordEncoder.encode(dados.getPassword()));
        }
        Usuario salvo = usuarioRepository.save(existente);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                salvo.getId(), "Usuário " + usernameAnterior + " alterado", alteracoes.getCampos(), null);

        if (!proprioUsuario) {
            encerrarSessoes(usernameAnterior);
        }
        return salvo;
    }

    // Deleta um usuário pelo ID
    public void deletarUsuario(String id) {
        Usuario existente = usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
        if (existente.getUsername().equals(usuarioLogado.getUsername())) {
            throw new RegraNegocioException("Você não pode excluir o seu próprio usuário.");
        }
        if (existente.getRole() == Perfil.ADMIN && usuarioRepository.countByRole(Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("O último ADMIN do sistema não pode ser excluído.");
        }
        usuarioRepository.deleteById(id);

        Alteracoes alteracoes = new Alteracoes()
                .remover("username", existente.getUsername())
                .remover("perfil", existente.getRole());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_USUARIO, AuditoriaService.ENTIDADE_USUARIO,
                id, "Usuário " + existente.getUsername() + " excluído", alteracoes.getCampos(), null);
        encerrarSessoes(existente.getUsername());
    }

    // ===================== apoio =====================

    private String validarUsername(String username) {
        String valor = username == null ? "" : username.trim();
        if (valor.length() < 3 || valor.length() > 50) {
            throw new RegraNegocioException("O username deve ter entre 3 e 50 caracteres.");
        }
        if (!valor.matches("[A-Za-z0-9._-]+")) {
            throw new RegraNegocioException("O username deve conter apenas letras, números, ponto, hífen ou sublinhado.");
        }
        return valor;
    }

    private void validarSenha(String senha, boolean obrigatoria) {
        if (senha == null || senha.isBlank()) {
            if (obrigatoria) {
                throw new RegraNegocioException("A senha é obrigatória.");
            }
            return;
        }
        if (senha.length() < SENHA_MINIMA || senha.length() > SENHA_MAXIMA) {
            throw new RegraNegocioException("A senha deve ter entre " + SENHA_MINIMA + " e " + SENHA_MAXIMA + " caracteres.");
        }
    }

    private Perfil validarPerfil(Perfil perfil) {
        if (perfil == null) {
            throw new RegraNegocioException("Selecione um perfil válido: ADMIN, GERENTE ou ATENDENTE.");
        }
        return perfil;
    }

    /** Expira as sessões abertas do usuário: no próximo acesso ele precisa entrar de novo. */
    private void encerrarSessoes(String username) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof UserDetails detalhes && detalhes.getUsername().equals(username)) {
                for (SessionInformation sessao : sessionRegistry.getAllSessions(principal, false)) {
                    sessao.expireNow();
                }
            }
        }
    }
}
