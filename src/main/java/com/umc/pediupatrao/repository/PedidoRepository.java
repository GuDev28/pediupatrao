package com.umc.pediupatrao.repository;

import com.umc.pediupatrao.entity.Pedido;
import com.umc.pediupatrao.entity.StatusPedido;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    List<Pedido> findByClienteId(String clienteId);

    boolean existsByClienteId(String clienteId);

    List<Pedido> findAllByOrderByDataHoraEntradaDesc();

    List<Pedido> findByStatusOrderByDataHoraEntradaDesc(StatusPedido status);
}
