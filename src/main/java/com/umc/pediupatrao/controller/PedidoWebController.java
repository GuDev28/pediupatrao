package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.ItemPedidoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.ItemPedido;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.entity.TipoEntrega;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.ClienteService;
import com.umc.pediupatrao.service.PedidoService;
import com.umc.pediupatrao.service.ProdutoService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Telas de pedidos: lista, registro, detalhe, transições, desconto e cancelamento. */
@Controller
public class PedidoWebController {

    private final PedidoService pedidoService;
    private final ClienteService clienteService;
    private final ProdutoService produtoService;

    public PedidoWebController(PedidoService pedidoService, ClienteService clienteService,
            ProdutoService produtoService) {
        this.pedidoService = pedidoService;
        this.clienteService = clienteService;
        this.produtoService = produtoService;
    }

    @GetMapping("/pedidos")
    public String listar(@RequestParam(required = false) String status, Model model) {
        StatusPedido filtro = StatusPedido.deTexto(status);
        model.addAttribute("pedidos", pedidoService.listarPorStatus(filtro));
        model.addAttribute("statusFiltro", filtro);
        model.addAttribute("todosStatus", StatusPedido.values());
        model.addAttribute("content", "pedidos/lista :: content");
        return "layout";
    }

    @GetMapping("/pedidos/{id}")
    public String detalhe(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("pedido", pedidoService.buscarPorId(id));
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        model.addAttribute("content", "pedidos/detalhe :: content");
        return "layout";
    }

    // ===================== registro e alteração =====================

    @GetMapping("/pedidos/novo")
    public String novo(Model model) {
        PedidoRequest request = new PedidoRequest();
        request.setTipoEntrega(TipoEntrega.ENTREGA);
        request.getItens().add(new ItemPedidoRequest(null, 1));
        return exibirFormulario(request, null, model);
    }

    @PostMapping("/pedidos")
    public String criar(@ModelAttribute("pedidoRequest") PedidoRequest request, Model model,
            RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.criarPedido(request);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido #" + pedido.getCodigo()
                    + " registrado. Total calculado: R$ " + pedido.getValorTotal().toPlainString());
            return "redirect:/pedidos/" + pedido.getId();
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return exibirFormulario(request, null, model);
        }
    }

    @GetMapping("/pedidos/{id}/editar")
    public String editar(@PathVariable String id, Model model, RedirectAttributes redirectAttributes) {
        Pedido pedido;
        try {
            pedido = pedidoService.buscarPorId(id);
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
        if (pedido.getStatus() != StatusPedido.RECEBIDO) {
            redirectAttributes.addFlashAttribute("erro", "Só é possível alterar itens de pedidos com status RECEBIDO.");
            return "redirect:/pedidos/" + id;
        }
        PedidoRequest request = new PedidoRequest();
        request.setClienteId(pedido.getClienteId());
        request.setTipoEntrega(pedido.getTipoEntrega());
        request.setObservacao(pedido.getObservacao());
        for (ItemPedido item : pedido.getItens()) {
            request.getItens().add(new ItemPedidoRequest(item.getProdutoId(), item.getQuantidade()));
        }
        return exibirFormulario(request, pedido, model);
    }

    @PostMapping("/pedidos/{id}/editar")
    public String atualizar(@PathVariable String id, @ModelAttribute("pedidoRequest") PedidoRequest request,
            Model model, RedirectAttributes redirectAttributes) {
        try {
            pedidoService.atualizarPedido(id, request);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido alterado com sucesso.");
            return "redirect:/pedidos/" + id;
        } catch (RegraNegocioException e) {
            model.addAttribute("erro", e.getMessage());
            return exibirFormulario(request, pedidoService.buscarPorId(id), model);
        } catch (RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/pedidos";
        }
    }

    // ===================== status, desconto e cancelamento =====================

    @PostMapping("/pedidos/{id}/status")
    public String alterarStatus(@PathVariable String id, @RequestParam String status,
            RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.atualizarStatus(id, status);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido #" + pedido.getCodigo() + " agora está "
                    + pedido.getStatus().getDescricao() + ".");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/desconto")
    public String aplicarDesconto(@PathVariable String id, @RequestParam(required = false) BigDecimal percentual,
            @RequestParam(required = false) String justificativa, RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.aplicarDesconto(id, percentual, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Desconto de " + pedido.getPercentualDesconto().toPlainString()
                    + "% aplicado. Novo total: R$ " + pedido.getValorTotal().toPlainString());
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    @PostMapping("/pedidos/{id}/cancelar")
    public String cancelar(@PathVariable String id, @RequestParam(required = false) String justificativa,
            RedirectAttributes redirectAttributes) {
        try {
            Pedido pedido = pedidoService.cancelar(id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Pedido #" + pedido.getCodigo() + " cancelado.");
        } catch (RegraNegocioException | RecursoNaoEncontradoException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/pedidos/" + id;
    }

    // ===================== apoio =====================

    private String exibirFormulario(PedidoRequest request, Pedido pedidoEmEdicao, Model model) {
        if (request.getItens() == null || request.getItens().isEmpty()) {
            List<ItemPedidoRequest> itens = new ArrayList<>();
            itens.add(new ItemPedidoRequest(null, 1));
            request.setItens(itens);
        }
        List<Produto> produtosAtivos = new ArrayList<>();
        for (Produto produto : produtoService.listarProdutos()) {
            if (!Boolean.FALSE.equals(produto.getAtivo())) {
                produtosAtivos.add(produto);
            }
        }
        model.addAttribute("pedidoRequest", request);
        model.addAttribute("pedidoEmEdicao", pedidoEmEdicao);
        model.addAttribute("clientes", clienteService.listarClientes());
        model.addAttribute("produtos", produtosAtivos);
        model.addAttribute("tiposEntrega", TipoEntrega.values());
        model.addAttribute("content", "pedidos/form :: content");
        return "layout";
    }
}
