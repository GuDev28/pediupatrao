package com.umc.pediupatrao.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.IndexDirection;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Registro de auditoria persistido na coleção "auditoria" (RF05).
 *
 * A classe não tem setters: um registro é criado uma única vez pelo
 * AuditoriaService e nunca mais alterado. O repositório correspondente também
 * não expõe métodos de atualização nem de exclusão.
 */
@Document(collection = "auditoria")
public class RegistroAuditoria {

    @Id
    private String id;

    @Indexed(direction = IndexDirection.DESCENDING)
    private LocalDateTime dataHora;

    @Indexed
    private String usuario;

    private String perfil;

    private TipoOperacao operacao;

    private String entidade;

    @Indexed
    private String entidadeId;

    private String descricao;

    private List<CampoAlterado> alteracoes = new ArrayList<>();

    private String justificativa;

    /** Usado apenas pelo Spring Data ao ler do MongoDB. */
    private RegistroAuditoria() {
    }

    public RegistroAuditoria(LocalDateTime dataHora, String usuario, String perfil, TipoOperacao operacao,
            String entidade, String entidadeId, String descricao, List<CampoAlterado> alteracoes,
            String justificativa) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.perfil = perfil;
        this.operacao = operacao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.descricao = descricao;
        this.alteracoes = alteracoes == null ? new ArrayList<>() : new ArrayList<>(alteracoes);
        this.justificativa = justificativa;
    }

    /**
     * Cópia deste registro com outra lista de alterações. Usada somente para exibição
     * (ex.: dados pessoais mascarados); o registro original no banco não muda.
     */
    public RegistroAuditoria copiaParaExibicao(List<CampoAlterado> alteracoesExibidas) {
        RegistroAuditoria copia = new RegistroAuditoria(dataHora, usuario, perfil, operacao, entidade,
                entidadeId, descricao, alteracoesExibidas, justificativa);
        copia.id = this.id;
        return copia;
    }

    public String getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getPerfil() {
        return perfil;
    }

    public TipoOperacao getOperacao() {
        return operacao;
    }

    public String getEntidade() {
        return entidade;
    }

    public String getEntidadeId() {
        return entidadeId;
    }

    public String getDescricao() {
        return descricao;
    }

    public List<CampoAlterado> getAlteracoes() {
        return alteracoes == null ? Collections.emptyList() : Collections.unmodifiableList(alteracoes);
    }

    public String getJustificativa() {
        return justificativa;
    }
}
