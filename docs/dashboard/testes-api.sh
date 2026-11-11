#!/usr/bin/env bash
# Roteiro de testes da API do dashboard (evidências).
# Uso (Git Bash/Linux/macOS), com a aplicação rodando:  bash docs/dashboard/testes-api.sh [http://localhost:8080]
# Cada caso mostra o resultado esperado, o código HTTP obtido e o corpo da resposta (encurtado).

BASE="${1:-http://localhost:8080}/api/dashboard"
GERENTE="gerente01:gerente123"
ADMIN="admin:teste123"
ATENDENTE="atendente01:atendente123"
APROVADOS=0
REPROVADOS=0

caso() { # caso "descrição" esperado credencial método url
  local descricao="$1" esperado="$2" credencial="$3" metodo="$4" url="$5"
  local auth=()
  [ -n "$credencial" ] && auth=(-u "$credencial")
  local saida codigo corpo
  saida=$(curl -s "${auth[@]}" -X "$metodo" -w $'\n%{http_code}' "$url")
  codigo="${saida##*$'\n'}"
  corpo="${saida%$'\n'*}"
  if [ "$codigo" = "$esperado" ]; then resultado="OK"; APROVADOS=$((APROVADOS + 1)); else resultado="FALHOU"; REPROVADOS=$((REPROVADOS + 1)); fi
  echo "[$resultado] $descricao"
  echo "        $metodo ${url#${BASE%/api/dashboard}}  -> esperado $esperado, obtido $codigo"
  echo "        ${corpo:0:300}"
  echo
}

echo "=== Segurança e perfis ==="
caso "Sem autenticação" 401 "" GET "$BASE/resumo"
caso "Senha incorreta" 401 "gerente01:errada" GET "$BASE/resumo"
caso "ATENDENTE não acessa o dashboard (tentativa auditada)" 403 "$ATENDENTE" GET "$BASE/resumo"
caso "GERENTE consulta o resumo" 200 "$GERENTE" GET "$BASE/resumo"
caso "ADMIN consulta o resumo" 200 "$ADMIN" GET "$BASE/resumo"
caso "API somente leitura (POST negado)" 403 "$GERENTE" POST "$BASE/resumo"
caso "API somente leitura (DELETE negado)" 403 "$ADMIN" DELETE "$BASE/pedidos"

echo "=== Endpoints (RF01 a RF05) ==="
caso "Opções dos filtros" 200 "$GERENTE" GET "$BASE/filtros"
caso "Resumo com período e tipo de entrega" 200 "$GERENTE" GET "$BASE/resumo?dataInicio=2026-10-01&dataFim=2026-10-31&tipoEntrega=ENTREGA"
caso "Série de vendas por semana" 200 "$GERENTE" GET "$BASE/vendas/serie?agrupamento=SEMANA"
caso "Distribuição por hora" 200 "$GERENTE" GET "$BASE/vendas/distribuicao?dimensao=HORA"
caso "Distribuição por categoria" 200 "$GERENTE" GET "$BASE/vendas/distribuicao?dimensao=CATEGORIA"
caso "Top 5 produtos por faturamento" 200 "$GERENTE" GET "$BASE/produtos/mais-vendidos?ordenarPor=FATURAMENTO&limite=5"
caso "Ranking só de bebidas" 200 "$GERENTE" GET "$BASE/produtos/mais-vendidos?categoria=Bebida"
caso "Situação dos pedidos" 200 "$GERENTE" GET "$BASE/pedidos/situacao"
caso "Pedidos em aberto (atraso após 30 min)" 200 "$GERENTE" GET "$BASE/pedidos/em-aberto?limiteMinutos=30&limite=5"
caso "Pedidos cancelados do período (paginado)" 200 "$GERENTE" GET "$BASE/pedidos?status=CANCELADO&pagina=0&tamanho=5"
caso "ADMIN vê nomes de clientes mascarados" 200 "$ADMIN" GET "$BASE/pedidos?tamanho=2"

echo "=== Validação e tratamento de erros (RF07) ==="
caso "Data em formato inválido" 400 "$GERENTE" GET "$BASE/resumo?dataInicio=03/11/2026"
caso "Data inicial depois da final" 422 "$GERENTE" GET "$BASE/resumo?dataInicio=2026-11-03&dataFim=2026-10-01"
caso "Período maior que 366 dias" 422 "$GERENTE" GET "$BASE/resumo?dataInicio=2025-01-01&dataFim=2026-11-03"
caso "Tipo de entrega inexistente" 400 "$GERENTE" GET "$BASE/resumo?tipoEntrega=DRONE"
caso "Agrupamento inexistente" 400 "$GERENTE" GET "$BASE/vendas/serie?agrupamento=ANO"
caso "Dimensão inexistente" 400 "$GERENTE" GET "$BASE/vendas/distribuicao?dimensao=COR"
caso "Limite do ranking fora do intervalo" 400 "$GERENTE" GET "$BASE/produtos/mais-vendidos?limite=500"
caso "Limite do ranking não numérico" 400 "$GERENTE" GET "$BASE/produtos/mais-vendidos?limite=dez"
caso "Status inexistente" 400 "$GERENTE" GET "$BASE/pedidos?status=ENTREGUE"
caso "Tamanho de página fora do intervalo" 400 "$GERENTE" GET "$BASE/pedidos?tamanho=1000"

echo "=== Resultado: $APROVADOS aprovado(s), $REPROVADOS reprovado(s) ==="
[ "$REPROVADOS" -eq 0 ]
