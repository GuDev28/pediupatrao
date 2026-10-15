package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carrega o usuário do MongoDB para o Spring Security, atribuindo a autoridade
 * correspondente ao perfil gravado (ROLE_ADMIN, ROLE_GERENTE ou ROLE_ATENDENTE).
 * Antes, todo usuário autenticado recebia a role fixa USER.
 */
@Service
public class UsuarioDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));

        // Usuário sem perfil válido autentica, mas não recebe nenhuma permissão.
        List<GrantedAuthority> autoridades = new ArrayList<>();
        if (usuario.getRole() != null) {
            autoridades.add(new SimpleGrantedAuthority(usuario.getRole().getAutoridade()));
        }

        return User.builder()
                .username(usuario.getUsername())
                .password(usuario.getPassword())
                .authorities(autoridades)
                .build();
    }
}
