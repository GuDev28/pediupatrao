package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

/** Tela de consulta da auditoria (somente leitura; ADMIN e GERENTE). */
@Controller
public class AuditoriaWebController {

    private static final String[] ENTIDADES = {
        AuditoriaService.ENTIDADE_PEDIDO, AuditoriaService.ENTIDADE_CLIENTE, AuditoriaService.ENTIDADE_PRODUTO,
        AuditoriaService.ENTIDADE_USUARIO, AuditoriaService.ENTIDADE_SEGURANCA
    };

    private final AuditoriaService auditoriaService;

    public AuditoriaWebController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping("/auditoria")
    public String consultar(@ModelAttribute("filtro") FiltroAuditoria filtro,
            @RequestParam(defaultValue = "0") int pagina, Model model) {
        Page<RegistroAuditoria> registros = auditoriaService.consultar(filtro, pagina, 20);
        model.addAttribute("registros", registros);
        model.addAttribute("entidades", ENTIDADES);
        model.addAttribute("operacoes", TipoOperacao.values());
        model.addAttribute("content", "auditoria/lista :: content");
        return "layout";
    }
}
