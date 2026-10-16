package com.umc.pediupatrao.service;

import com.umc.pediupatrao.entity.Cliente;
import com.umc.pediupatrao.entity.TipoOperacao;
import com.umc.pediupatrao.exception.RecursoNaoEncontradoException;
import com.umc.pediupatrao.exception.RegraNegocioException;
import com.umc.pediupatrao.repository.ClienteRepository;
import com.umc.pediupatrao.repository.PedidoRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * Cadastro de clientes com auditoria das inclusões e alterações (RF05),
 * especialmente de telefone e endereço.
 */
@Service
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final PedidoRepository pedidoRepository;
    private final AuditoriaService auditoriaService;

    public ClienteService(ClienteRepository clienteRepository, PedidoRepository pedidoRepository,
            AuditoriaService auditoriaService) {
        this.clienteRepository = clienteRepository;
        this.pedidoRepository = pedidoRepository;
        this.auditoriaService = auditoriaService;
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public List<Cliente> listarClientes() {
        return clienteRepository.findAll();
    }

    @PreAuthorize("hasAnyRole('GERENTE','ATENDENTE')")
    public Optional<Cliente> buscarPorId(String id) {
        return clienteRepository.findById(id);
    }

    /** Cadastra um novo cliente. Somente os campos do formulário são copiados (o id nunca vem do usuário). */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente criar(Cliente dados) {
        Cliente cliente = new Cliente();
        copiarCampos(dados, cliente);
        validar(cliente);

        Cliente salvo = clienteRepository.save(cliente);

        Alteracoes alteracoes = new Alteracoes()
                .incluir("nome", salvo.getNome())
                .incluir("telefone", salvo.getTelefone())
                .incluir("cep", salvo.getCep())
                .incluir("logradouro", salvo.getLogradouro())
                .incluir("numero", salvo.getNumero())
                .incluir("complemento", salvo.getComplemento())
                .incluir("bairro", salvo.getBairro())
                .incluir("cidade", salvo.getCidade())
                .incluir("estado", salvo.getEstado());
        auditoriaService.registrar(TipoOperacao.INCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                salvo.getId(), "Cliente cadastrado", alteracoes.getCampos(), null);
        return salvo;
    }

    /**
     * Atualiza os dados do cliente e audita cada campo alterado com valor anterior e novo.
     * Se nada mudou, não grava nem audita.
     */
    @PreAuthorize("hasRole('ATENDENTE')")
    public Cliente atualizar(String id, Cliente dados) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado para atualização."));

        Cliente novo = new Cliente();
        copiarCampos(dados, novo);
        validar(novo);

        Alteracoes alteracoes = new Alteracoes()
                .comparar("nome", cliente.getNome(), novo.getNome())
                .comparar("telefone", cliente.getTelefone(), novo.getTelefone())
                .comparar("cep", cliente.getCep(), novo.getCep())
                .comparar("logradouro", cliente.getLogradouro(), novo.getLogradouro())
                .comparar("numero", cliente.getNumero(), novo.getNumero())
                .comparar("complemento", cliente.getComplemento(), novo.getComplemento())
                .comparar("bairro", cliente.getBairro(), novo.getBairro())
                .comparar("cidade", cliente.getCidade(), novo.getCidade())
                .comparar("estado", cliente.getEstado(), novo.getEstado());

        if (alteracoes.isVazia()) {
            return cliente;
        }

        copiarCampos(novo, cliente);
        Cliente salvo = clienteRepository.save(cliente);

        auditoriaService.registrar(TipoOperacao.ALTERACAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                salvo.getId(), "Dados cadastrais alterados (" + alteracoes.getQuantidade() + " campo(s))",
                alteracoes.getCampos(), null);
        return salvo;
    }

    /**
     * Exclusão restrita ao GERENTE e bloqueada para clientes com pedidos, para não
     * perder a referência do histórico de pedidos e da auditoria.
     */
    @PreAuthorize("hasRole('GERENTE')")
    public void excluir(String id) {
        Cliente cliente = clienteRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente não encontrado."));
        if (pedidoRepository.existsByClienteId(id)) {
            throw new RegraNegocioException("Cliente possui pedidos registrados e não pode ser excluído.");
        }
        clienteRepository.deleteById(id);

        Alteracoes alteracoes = new Alteracoes().remover("nome", cliente.getNome());
        auditoriaService.registrar(TipoOperacao.EXCLUSAO_CLIENTE, AuditoriaService.ENTIDADE_CLIENTE,
                id, "Cliente excluído", alteracoes.getCampos(), null);
    }

    // ===================== apoio =====================

    private void copiarCampos(Cliente origem, Cliente destino) {
        destino.setNome(limpar(origem.getNome()));
        destino.setTelefone(limpar(origem.getTelefone()));
        destino.setCep(limpar(origem.getCep()));
        destino.setLogradouro(limpar(origem.getLogradouro()));
        destino.setNumero(limpar(origem.getNumero()));
        destino.setComplemento(limpar(origem.getComplemento()));
        destino.setBairro(limpar(origem.getBairro()));
        destino.setCidade(limpar(origem.getCidade()));
        String estado = limpar(origem.getEstado());
        destino.setEstado(estado == null ? null : estado.toUpperCase());
    }

    private void validar(Cliente cliente) {
        if (cliente.getNome() == null) {
            throw new RegraNegocioException("O nome do cliente é obrigatório.");
        }
        if (cliente.getNome().length() > 100) {
            throw new RegraNegocioException("O nome do cliente deve ter no máximo 100 caracteres.");
        }
        if (cliente.getTelefone() == null) {
            throw new RegraNegocioException("O telefone do cliente é obrigatório.");
        }
        int digitosTelefone = cliente.getTelefone().replaceAll("\\D", "").length();
        if (digitosTelefone < 10 || digitosTelefone > 11) {
            throw new RegraNegocioException("Telefone inválido: informe DDD + número (10 ou 11 dígitos).");
        }
        if (cliente.getCep() != null && cliente.getCep().replaceAll("\\D", "").length() != 8) {
            throw new RegraNegocioException("CEP inválido: informe 8 dígitos.");
        }
        if (cliente.getEstado() != null && !cliente.getEstado().matches("[A-Z]{2}")) {
            throw new RegraNegocioException("Estado inválido: informe a sigla com 2 letras (ex.: SP).");
        }
    }

    private static String limpar(String valor) {
        if (valor == null) {
            return null;
        }
        String texto = valor.trim();
        return texto.isEmpty() ? null : texto;
    }
}
