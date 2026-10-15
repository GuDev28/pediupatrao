package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.RegistroAuditoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Repositório da auditoria com exposição seletiva de métodos.
 *
 * Diferente dos demais repositórios, NÃO estende MongoRepository/CrudRepository:
 * só existem operações de gravação de novos registros e de consulta.
 * Não há delete, deleteAll, saveAll nem update disponíveis para o restante do sistema.
 */
public interface RegistroAuditoriaRepository extends Repository<RegistroAuditoria, String> {

    /** Grava um novo registro (o objeto é criado sem id, então sempre resulta em inserção). */
    <S extends RegistroAuditoria> S save(S registro);

    Optional<RegistroAuditoria> findById(String id);

    List<RegistroAuditoria> findByEntidadeAndEntidadeIdOrderByDataHoraDesc(String entidade, String entidadeId);

    long count();
}
