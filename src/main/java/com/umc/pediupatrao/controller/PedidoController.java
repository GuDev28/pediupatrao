package com.umc.pediupatrao.controller;

import com.umc.pediupatrao.dto.CancelamentoRequest;
import com.umc.pediupatrao.dto.DescontoRequest;
import com.umc.pediupatrao.dto.PedidoRequest;
import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.service.PedidoService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API REST de pedidos. As permissões são verificadas no SecurityConfig (rota) e no
 * PedidoService (@PreAuthorize); este controlador só traduz HTTP <-> serviço.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<Pedido> listarPedidos(@RequestParam(required = false) String status) {
        if (status == null || status.isBlank()) {
            return pedidoService.listarPedidos();
        }
        StatusPedido filtro = StatusPedido.deTexto(status);
        if (filtro == null) {
            throw new RegraNegocioException("Status inválido para filtro: '" + status + "'.");
        }
        return pedidoService.listarPorStatus(filtro);
    }

    @GetMapping("/{id}")
    public Pedido buscarPedido(@PathVariable String id) {
        return pedidoService.buscarPorId(id);
    }

    /** Registra o pedido. Total, preços, status e responsável são definidos pelo servidor. */
    @PostMapping
    public ResponseEntity<Pedido> criarPedido(@RequestBody PedidoRequest pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.criarPedido(pedido));
    }

    /** Altera itens/cliente/tipo de entrega/observação enquanto o pedido está RECEBIDO. */
    @PutMapping("/{id}")
    public Pedido atualizarPedido(@PathVariable String id, @RequestBody PedidoRequest pedido) {
        return pedidoService.atualizarPedido(id, pedido);
    }

    /** Mesmo endpoint da versão original, agora com validação das transições. */
    @PutMapping("/{id}/status")
    public Pedido atualizarStatus(@PathVariable String id, @RequestParam String status) {
        return pedidoService.atualizarStatus(id, status);
    }

    /** Exclusivo do GERENTE. */
    @PostMapping("/{id}/desconto")
    public Pedido aplicarDesconto(@PathVariable String id, @RequestBody DescontoRequest desconto) {
        return pedidoService.aplicarDesconto(id, desconto.getPercentual(), desconto.getJustificativa());
    }

    /** Exclusivo do GERENTE; justificativa obrigatória. */
    @PostMapping("/{id}/cancelar")
    public Pedido cancelarPedido(@PathVariable String id, @RequestBody CancelamentoRequest cancelamento) {
        return pedidoService.cancelar(id, cancelamento.getJustificativa());
    }
}
