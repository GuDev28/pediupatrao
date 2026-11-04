package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela do dashboard administrativo. A página só monta a estrutura; todos os números
 * vêm da API /api/dashboard/** (RF06), carregada pelo script static/js/dashboard.js.
 */
@Controller
public class DashboardWebController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
