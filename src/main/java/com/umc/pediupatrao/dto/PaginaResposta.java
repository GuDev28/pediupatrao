package com.umc.pediupatrao.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Página de resultados em formato JSON estável (em vez de serializar PageImpl diretamente). */
public record PaginaResposta<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos, int totalPaginas) {

    public static <T> PaginaResposta<T> de(Page<T> page) {
        return new PaginaResposta<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
