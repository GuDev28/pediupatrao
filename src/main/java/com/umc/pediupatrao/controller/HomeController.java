package com.umc.pediupatrao.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.Perfil;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.Usuario;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.ProdutoService;
import com.umc.pediupatrao.service.UsuarioService;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Telas de usuários, clientes e produtos. As telas de pedidos estão em
 * PedidoWebController e a de auditoria em AuditoriaWebController.
 */
@Controller
public class HomeController {

    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final ClienteService clienteService;
    private final ProdutoService produtoService;
    private final UsuarioService usuarioService;

    public HomeController(ClienteService clienteService, ProdutoService produtoService,
            UsuarioService usuarioService) {
        this.clienteService = clienteService;
        this.produtoService = produtoService;
        this.usuarioService = usuarioService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("content", "home :: content");
        return "layout";
    }

    // ========================
    // USUÁRIOS (somente ADMIN)
    // ========================
    @GetMapping("/usuarios")
    public String listarUsuarios(Model model) {
        List<Usuario> usuarios = usuarioService.listarTodos();
        model.addAttribute("usuarios", usuarios);
        model.addAttribute("content", "usuarios/lista :: content");
        log.info("Carregando fragmento: usuarios/lista :: content");
        return "layout";
    }

    @GetMapping("/usuarios/novo")
    public String novoUsuarioForm(Model model) {
        return exibirFormularioUsuario(new Usuario(), model);
    }

    @GetMapping("/usuarios/editar/{id}")
    public String editarUsuarioForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        boolean encontrado = usuarioService.buscarPorId(id).map(u -> {
            u.setPassword(null); // o hash da senha nunca vai para a tela
            model.addAttribute("usuario", u);
            return true;
        }).orElse(false);

        if (!encontrado) {
            redirectAttributes.addFlashAttribute("erro", "Usuário não encontrado.");
            return "redirect:/usuarios";
        }

        return exibirFormularioUsuario((Usuario) model.getAttribute("usuario"), model);
    }

    @PostMapping("/usuarios/salvar")
    public String salvarUsuario(@ModelAttribute Usuario usuario, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.salvarUsuario(usuario);
        } catch (RegraNegocioException e) {
            usuario.setPassword(null);
            model.addAttribute("erro", e.getMessage());
            return exibirFormularioUsuario(usuario, model);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Usuário criado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/editar/{id}")
    public String atualizarUsuario(@PathVariable String id,
            @ModelAttribute Usuario usuario, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.atualizarUsuario(id, usuario);
        } catch (RegraNegocioException e) {
            usuario.setId(id);
            usuario.setPassword(null);
            model.addAttribute("erro", e.getMessage());
            return exibirFormularioUsuario(usuario, model);
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/usuarios";
        }
        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado com sucesso!");
        return "redirect:/usuarios";
    }

    @PostMapping("/usuarios/deletar/{id}")
    public String deletarUsuario(@PathVariable String id,
            RedirectAttributes redirectAttributes) {
        try {
            usuarioService.deletarUsuario(id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário removido com sucesso!");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private String exibirFormularioUsuario(Usuario usuario, Model model) {
        model.addAttribute("usuario", usuario);
        model.addAttribute("perfis", Perfil.values());
        model.addAttribute("content", "usuarios/form :: content");
        return "layout";
    }

    // ========================
    // PRODUTOS (consulta: todos; manutenção: GERENTE)
    // ========================
    @GetMapping("/produtos")
    public String produtos(Model model) {
        Produto novo = new Produto();
        novo.setAtivo(true);
        return exibirProdutos(novo, model);
    }

    @GetMapping("/produtos/editar/{id}")
    public String editarProduto(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Produto produto = produtoService.buscarPorId(id).orElse(null);
        if (produto == null) {
            redirectAttributes.addFlashAttribute("erro", "Produto não encontrado.");
            return "redirect:/produtos";
        }
        return exibirProdutos(produto, model);
    }

    @PostMapping("/produtos/salvar")
    public String salvarProduto(@ModelAttribute Produto produto, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            produtoService.salvar(produto);
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            model.addAttribute("erro", e.getMessage());
            return exibirProdutos(produto, model);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Produto salvo com sucesso!");
        return "redirect:/produtos";
    }

    @PostMapping("/produtos/excluir/{id}")
    public String excluirProduto(@PathVariable String id, RedirectAttributes redirectAttributes) {
        try {
            produtoService.excluir(id);
            redirectAttributes.addFlashAttribute("sucesso", "Produto excluído.");
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/produtos";
    }

    private String exibirProdutos(Produto produtoEmEdicao, Model model) {
        model.addAttribute("produtos", produtoService.listarProdutos());
        model.addAttribute("produto", produtoEmEdicao);
        model.addAttribute("content", "produtos/lista :: content");
        return "layout";
    }

    // ========================
    // CLIENTES (cadastro/alteração: ATENDENTE; consulta: GERENTE e ATENDENTE)
    // ========================
    @GetMapping("/clientes")
    public String clientes(Model model) {
        model.addAttribute("content", "clientes/lista :: content");
        log.info("Carregando fragmento: clientes/lista :: content");
        return "layout";
    }

    @GetMapping("/clientes/novo")
    public String novoClienteForm(Model model) {
        return exibirFormularioCliente(new Cliente(), model);
    }

    @PostMapping("/clientes/salvar")
    public String salvarCliente(@ModelAttribute Cliente cliente, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.criar(cliente);
        } catch (RegraNegocioException e) {
            cliente.setId(null);
            model.addAttribute("erro", e.getMessage());
            return exibirFormularioCliente(cliente, model);
        }
        redirectAttributes.addFlashAttribute("sucesso", "Cliente criado com sucesso!");
        return "redirect:/clientes";
    }

    @GetMapping("/clientes/editar/{id}")
    public String editarClienteForm(@PathVariable String id, Model model,
            RedirectAttributes redirectAttributes) {
        Cliente cliente = clienteService.buscarPorId(id).orElse(null);
        if (cliente == null) {
            redirectAttributes.addFlashAttribute("erro", "Cliente não encontrado.");
            return "redirect:/clientes";
        }
        return exibirFormularioCliente(cliente, model);
    }

    @PostMapping("/clientes/editar/{id}")
    public String atualizarCliente(@PathVariable String id,
            @ModelAttribute Cliente cliente, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            clienteService.atualizar(id, cliente);
        } catch (RegraNegocioException e) {
            cliente.setId(id);
            model.addAttribute("erro", e.getMessage());
            return exibirFormularioCliente(cliente, model);
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/clientes";
        }
        redirectAttributes.addFlashAttribute("sucesso", "Cliente atualizado com sucesso!");
        return "redirect:/clientes";
    }

    private String exibirFormularioCliente(Cliente cliente, Model model) {
        model.addAttribute("cliente", cliente);
        model.addAttribute("content", "clientes/form :: content");
        return "layout";
    }

    // ========================
    // OUTRAS ROTAS (ADMIN)
    // ========================
    @GetMapping("/configuracoes")
    public String configuracoes(Model model) {
        model.addAttribute("content", "configuracoes :: content");
        return "layout";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }
}
