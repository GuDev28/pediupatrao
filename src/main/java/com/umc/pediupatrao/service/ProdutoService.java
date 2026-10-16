package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Produto;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ProdutoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de produtos. Como preço é informação comercial que define o valor dos
 * pedidos, a manutenção é restrita ao GERENTE e auditada.
 */
@Service
public class ProdutoService {

    private final ProdutoRepository produtoRepository;
    private final AuditoriaService auditoriaService;

    public ProdutoService(ProdutoRepository produtoRepository, AuditoriaService auditoriaService) {
        this.produtoRepository = produtoRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<Produto> listarProdutos() {
        return produtoRepository.findAll();
    }

    public Optional<Produto> buscarPorId(String id) {
        return produtoRepository.findById(id);
    }

    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Produto produto = produtoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
        produtoRepository.deleteById(id);
        Alteracoes alteracoes = new Alteracoes()
                .remover("nome", produto.getNome())
                .remover("preco", produto.getPreco());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_PRODUTO, AuditoriaService.ENTIDADE_PRODUTO, id,
                "Produto excluído", alteracoes.getCampos(), null);
    }

    /** Salva um novo produto (sem id) ou atualiza um existente, auditando as mudanças. */
    @PreAuthorize("hasRole('GERENTE')")
    public Produto salvar(Produto dados) {
        validar(dados);
        if (dados.getId() == null || dados.getId().isBlank()) {
            Produto novo = new Produto();
            copiar(dados, novo);
            Produto salvo = produtoRepository.save(novo);
            Alteracoes alteracoes = new Alteracoes()
                    .incluir("nome", salvo.getNome())
                    .incluir("tipo", salvo.getTipo())
                    .incluir("preco", salvo.getPreco())
                    .incluir("ativo", salvo.getAtivo());
            auditoriaService.registrar(TipoOperacao.INCLUSAO_PRODUTO, AuditoriaService.ENTIDADE_PRODUTO,
                    salvo.getId(), "Produto cadastrado", alteracoes.getCampos(), null);
            return salvo;
        }

        Produto existente = produtoRepository.findById(dados.getId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado para atualização."));
        Alteracoes alteracoes = new Alteracoes()
                .comparar("nome", existente.getNome(), dados.getNome().trim())
                .comparar("tipo", existente.getTipo(), dados.getTipo())
                .comparar("preco", existente.getPreco(), dados.getPreco())
                .comparar("ativo", existente.getAtivo(), Boolean.TRUE.equals(dados.getAtivo()));
        if (alteracoes.isVazia()) {
            return existente;
        }
        copiar(dados, existente);
        Produto salvo = produtoRepository.save(existente);
        auditoriaService.registrar(TipoOperacao.ALTERACAO_PRODUTO, AuditoriaService.ENTIDADE_PRODUTO,
                salvo.getId(), "Produto alterado", alteracoes.getCampos(), null);
        return salvo;
    }

    private void copiar(Produto origem, Produto destino) {
        destino.setNome(origem.getNome().trim());
        destino.setTipo(origem.getTipo() == null ? null : origem.getTipo().trim());
        destino.setPreco(origem.getPreco());
        destino.setAtivo(Boolean.TRUE.equals(origem.getAtivo()));
    }

    private void validar(Produto produto) {
        if (produto == null || produto.getNome() == null || produto.getNome().isBlank()) {
            throw new RegraNegocioException("O nome do produto é obrigatório.");
        }
        if (produto.getPreco() == null || produto.getPreco() <= 0) {
            throw new RegraNegocioException("O preço do produto deve ser maior que zero.");
        }
    }
}
