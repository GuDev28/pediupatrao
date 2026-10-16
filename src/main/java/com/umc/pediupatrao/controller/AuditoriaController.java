package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.FiltroAuditoria;
import com.umc.pediupatrao.dto.PaginaResposta;
import com.umc.pediupatrao.entity.RegistroAuditoria;
import com.umc.pediupatrao.service.AuditoriaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consulta da auditoria (somente leitura, ADMIN e GERENTE).
 * Não existem endpoints de alteração ou exclusão; o SecurityConfig ainda nega
 * explicitamente qualquer método diferente de GET em /api/auditoria/**.
 */
@RestController
@RequestMapping("/api/auditoria")
public class AuditoriaController {

    private final AuditoriaService auditoriaService;

    public AuditoriaController(AuditoriaService auditoriaService) {
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    public PaginaResposta<RegistroAuditoria> consultar(FiltroAuditoria filtro,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamanho) {
        return PaginaResposta.de(auditoriaService.consultar(filtro, pagina, tamanho));
    }

    @GetMapping("/{id}")
    public RegistroAuditoria buscar(@PathVariable String id) {
        return auditoriaService.buscarPorId(id);
    }
}
