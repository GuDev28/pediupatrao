package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.security.UsuarioLogado;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Disponibiliza o usuário logado e o perfil dele para todas as telas Thymeleaf
 * (menu lateral e botões). Serve apenas para a interface: as permissões de verdade
 * são verificadas no SecurityConfig e nos serviços.
 */
@ControllerAdvice(assignableTypes = {HomeController.class, PedidoWebController.class, AuditoriaWebController.class,
    DashboardWebController.class})
public class DadosDoUsuarioNasTelas {

    private final UsuarioLogado usuarioLogado;

    public DadosDoUsuarioNasTelas(UsuarioLogado usuarioLogado) {
        this.usuarioLogado = usuarioLogado;
    }

    @ModelAttribute
    public void adicionarDadosDoUsuario(Model model) {
        Perfil perfil = usuarioLogado.getPerfil();
        model.addAttribute("usuarioAtual", usuarioLogado.isAutenticado() ? usuarioLogado.getUsername() : null);
        model.addAttribute("perfilAtual", perfil);
        model.addAttribute("ehAdmin", perfil == Perfil.ADMIN);
        model.addAttribute("ehGerente", perfil == Perfil.GERENTE);
        model.addAttribute("ehAtendente", perfil == Perfil.ATENDENTE);
    }
}
