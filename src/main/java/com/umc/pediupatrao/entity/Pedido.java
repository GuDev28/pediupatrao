package com.umc.pediupatrao.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Pedido da pizzaria (RF02/RF03/RF04).
 *
 * Os valores (subtotal, desconto e total) são SEMPRE calculados por
 * {@link #recalcularValores()} a partir dos itens e do percentual de desconto
 * autorizado; nenhuma requisição HTTP preenche esses campos diretamente.
 */
@Document(collection = "pedidos")
public class Pedido {

    public static final BigDecimal DESCONTO_MAXIMO_PERCENTUAL = new BigDecimal("20");

    @Id
    private String id;

    // Controle de concorrência otimista: impede que duas alterações simultâneas
    // (ex.: dois usuários mudando o status) sobrescrevam uma à outra.
    @Version
    private Long versao;

    private String clienteId;
    private String clienteNome;
    private TipoEntrega tipoEntrega;
    private String observacao;
    private List<ItemPedido> itens = new ArrayList<>();

    // Valores calculados no servidor
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal percentualDesconto = BigDecimal.ZERO;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorDesconto = BigDecimal.ZERO;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    private StatusPedido status;

    // Entrada (RECEBIDO)
    private LocalDateTime dataHoraEntrada;
    private String registradoPor;

    // Saída (SAIU_PARA_ENTREGA / RETIRADO)
    private LocalDateTime dataHoraSaida;
    private String responsavelSaida;

    // Finalização
    private LocalDateTime dataHoraFinalizacao;
    private String finalizadoPor;

    // Desconto (somente GERENTE)
    private String descontoAplicadoPor;
    private LocalDateTime dataHoraDesconto;
    private String justificativaDesconto;

    // Cancelamento (somente GERENTE, com justificativa)
    private LocalDateTime dataHoraCancelamento;
    private String canceladoPor;
    private String justificativaCancelamento;

    // Última alteração de itens/dados do pedido
    private LocalDateTime dataHoraUltimaAlteracao;
    private String alteradoPor;

    private List<HistoricoStatus> historico = new ArrayList<>();

    /**
     * Recalcula subtotal, valor do desconto e total a partir dos itens e do
     * percentual de desconto vigente. Valores com 2 casas e arredondamento HALF_UP.
     */
    public void recalcularValores() {
        BigDecimal soma = BigDecimal.ZERO;
        if (itens != null) {
            for (ItemPedido item : itens) {
                soma = soma.add(item.getSubtotal());
            }
        }
        BigDecimal percentual = percentualDesconto == null ? BigDecimal.ZERO : percentualDesconto;
        this.subtotal = soma.setScale(2, RoundingMode.HALF_UP);
        this.valorDesconto = subtotal.multiply(percentual)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        this.valorTotal = subtotal.subtract(valorDesconto).setScale(2, RoundingMode.HALF_UP);
    }

    /** Próximo status do fluxo normal, considerando se o pedido é entrega ou retirada. */
    @Transient
    public StatusPedido getProximoStatus() {
        if (status == null) {
            return null;
        }
        switch (status) {
            case RECEBIDO:
                return StatusPedido.EM_PREPARACAO;
            case EM_PREPARACAO:
                return StatusPedido.PRONTO;
            case PRONTO:
                return tipoEntrega == null ? null : tipoEntrega.getStatusDeSaida();
            case SAIU_PARA_ENTREGA:
            case RETIRADO:
                return StatusPedido.FINALIZADO;
            default:
                return null;
        }
    }

    /** Código curto para exibição (últimos 6 caracteres do id). */
    @Transient
    public String getCodigo() {
        if (id == null) {
            return "";
        }
        return id.length() <= 6 ? id.toUpperCase() : id.substring(id.length() - 6).toUpperCase();
    }

    @Transient
    public boolean isCancelado() {
        return status == StatusPedido.CANCELADO;
    }

    @Transient
    public boolean isPossuiDesconto() {
        return percentualDesconto != null && percentualDesconto.compareTo(BigDecimal.ZERO) > 0;
    }

    /** Resumo textual dos itens, usado na auditoria. */
    @Transient
    public String getResumoItens() {
        StringBuilder sb = new StringBuilder();
        if (itens != null) {
            for (ItemPedido item : itens) {
                if (sb.length() > 0) {
                    sb.append("; ");
                }
                sb.append(item.getResumo());
            }
        }
        return sb.toString();
    }

    @Transient
    public int getQuantidadeTotalItens() {
        int total = 0;
        if (itens != null) {
            for (ItemPedido item : itens) {
                total += item.getQuantidade();
            }
        }
        return total;
    }

    // ===== getters e setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getVersao() {
        return versao;
    }

    public void setVersao(Long versao) {
        this.versao = versao;
    }

    public String getClienteId() {
        return clienteId;
    }

    public void setClienteId(String clienteId) {
        this.clienteId = clienteId;
    }

    public String getClienteNome() {
        return clienteNome;
    }

    public void setClienteNome(String clienteNome) {
        this.clienteNome = clienteNome;
    }

    public TipoEntrega getTipoEntrega() {
        return tipoEntrega;
    }

    public void setTipoEntrega(TipoEntrega tipoEntrega) {
        this.tipoEntrega = tipoEntrega;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public List<ItemPedido> getItens() {
        return itens;
    }

    public void setItens(List<ItemPedido> itens) {
        this.itens = itens;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getPercentualDesconto() {
        return percentualDesconto;
    }

    public void setPercentualDesconto(BigDecimal percentualDesconto) {
        this.percentualDesconto = percentualDesconto;
    }

    public BigDecimal getValorDesconto() {
        return valorDesconto;
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public StatusPedido getStatus() {
        return status;
    }

    public void setStatus(StatusPedido status) {
        this.status = status;
    }

    public LocalDateTime getDataHoraEntrada() {
        return dataHoraEntrada;
    }

    public void setDataHoraEntrada(LocalDateTime dataHoraEntrada) {
        this.dataHoraEntrada = dataHoraEntrada;
    }

    public String getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(String registradoPor) {
        this.registradoPor = registradoPor;
    }

    public LocalDateTime getDataHoraSaida() {
        return dataHoraSaida;
    }

    public void setDataHoraSaida(LocalDateTime dataHoraSaida) {
        this.dataHoraSaida = dataHoraSaida;
    }

    public String getResponsavelSaida() {
        return responsavelSaida;
    }

    public void setResponsavelSaida(String responsavelSaida) {
        this.responsavelSaida = responsavelSaida;
    }

    public LocalDateTime getDataHoraFinalizacao() {
        return dataHoraFinalizacao;
    }

    public void setDataHoraFinalizacao(LocalDateTime dataHoraFinalizacao) {
        this.dataHoraFinalizacao = dataHoraFinalizacao;
    }

    public String getFinalizadoPor() {
        return finalizadoPor;
    }

    public void setFinalizadoPor(String finalizadoPor) {
        this.finalizadoPor = finalizadoPor;
    }

    public String getDescontoAplicadoPor() {
        return descontoAplicadoPor;
    }

    public void setDescontoAplicadoPor(String descontoAplicadoPor) {
        this.descontoAplicadoPor = descontoAplicadoPor;
    }

    public LocalDateTime getDataHoraDesconto() {
        return dataHoraDesconto;
    }

    public void setDataHoraDesconto(LocalDateTime dataHoraDesconto) {
        this.dataHoraDesconto = dataHoraDesconto;
    }

    public String getJustificativaDesconto() {
        return justificativaDesconto;
    }

    public void setJustificativaDesconto(String justificativaDesconto) {
        this.justificativaDesconto = justificativaDesconto;
    }

    public LocalDateTime getDataHoraCancelamento() {
        return dataHoraCancelamento;
    }

    public void setDataHoraCancelamento(LocalDateTime dataHoraCancelamento) {
        this.dataHoraCancelamento = dataHoraCancelamento;
    }

    public String getCanceladoPor() {
        return canceladoPor;
    }

    public void setCanceladoPor(String canceladoPor) {
        this.canceladoPor = canceladoPor;
    }

    public String getJustificativaCancelamento() {
        return justificativaCancelamento;
    }

    public void setJustificativaCancelamento(String justificativaCancelamento) {
        this.justificativaCancelamento = justificativaCancelamento;
    }

    public LocalDateTime getDataHoraUltimaAlteracao() {
        return dataHoraUltimaAlteracao;
    }

    public void setDataHoraUltimaAlteracao(LocalDateTime dataHoraUltimaAlteracao) {
        this.dataHoraUltimaAlteracao = dataHoraUltimaAlteracao;
    }

    public String getAlteradoPor() {
        return alteradoPor;
    }

    public void setAlteradoPor(String alteradoPor) {
        this.alteradoPor = alteradoPor;
    }

    public List<HistoricoStatus> getHistorico() {
        return historico;
    }

    public void setHistorico(List<HistoricoStatus> historico) {
        this.historico = historico;
    }
}
