package com.umc.pediupatrao.security;

import com.umc.pediupatrao.entity.Perfil;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acesso ao usuário autenticado na requisição atual (username e perfil),
 * usado para registrar responsáveis nos pedidos e identificar o autor na auditoria.
 */
@Component
public class UsuarioLogado {

    public static final String SISTEMA = "sistema";

    public boolean isAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    /** Username do usuário autenticado; "sistema" quando não há usuário (ex.: rotinas de inicialização). */
    public String getUsername() {
        if (!isAutenticado()) {
            return SISTEMA;
        }
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    /** Perfil do usuário autenticado (ADMIN, GERENTE ou ATENDENTE) ou null. */
    public Perfil getPerfil() {
        if (!isAutenticado()) {
            return null;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        for (GrantedAuthority autoridade : auth.getAuthorities()) {
            for (Perfil perfil : Perfil.values()) {
                if (perfil.getAutoridade().equals(autoridade.getAuthority())) {
                    return perfil;
                }
            }
        }
        return null;
    }

    /** Nome do perfil para registro (ex.: "GERENTE"), ou "-" quando não houver. */
    public String getNomePerfil() {
        Perfil perfil = getPerfil();
        return perfil == null ? "-" : perfil.name();
    }

    public boolean possuiPerfil(Perfil perfil) {
        return perfil != null && perfil == getPerfil();
    }
}
