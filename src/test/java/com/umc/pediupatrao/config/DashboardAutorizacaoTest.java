package com.umc.pediupatrao.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.umc.pediupatrao.dto.dashboard.FiltroDashboard;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.repository.PedidoDashboardRepository;
import com.umc.pediupatrao.repository.ProdutoRepository;
import com.umc.pediupatrao.security.UsuarioLogado;
import com.umc.pediupatrao.service.DashboardService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Segunda camada de proteção do dashboard: o @PreAuthorize do DashboardService bloqueia
 * o ATENDENTE mesmo que algum controlador chame o serviço diretamente.
 */
@SpringJUnitConfig(DashboardAutorizacaoTest.Configuracao.class)
class DashboardAutorizacaoTest {

    @Configuration
    @EnableMethodSecurity
    static class Configuracao {

        @Bean
        DashboardService dashboardService() {
            return new DashboardService(mock(PedidoDashboardRepository.class), mock(ProdutoRepository.class),
                    new UsuarioLogado(), Clock.systemDefaultZone());
        }
    }

    @Autowired
    private DashboardService dashboardService;

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void atendenteNaoConsultaNenhumIndicador() {
        logarComo(Perfil.ATENDENTE);

        assertThatThrownBy(() -> dashboardService.resumo(new FiltroDashboard()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.produtosMaisVendidos(new FiltroDashboard(), null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.pedidosEmAberto(null, null, null))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> dashboardService.opcoesDeFiltro())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void gerenteEAdminConsultam() {
        logarComo(Perfil.GERENTE);
        assertThat(dashboardService.resumo(new FiltroDashboard()).pedidos().atual()).isZero();

        logarComo(Perfil.ADMIN);
        assertThat(dashboardService.situacaoDosPedidos(new FiltroDashboard()).totalPedidos()).isZero();
    }

    private static void logarComo(Perfil perfil) {
        List<SimpleGrantedAuthority> autoridades = new ArrayList<>();
        autoridades.add(new SimpleGrantedAuthority(perfil.getAutoridade()));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("usuario", null, autoridades));
    }
}
