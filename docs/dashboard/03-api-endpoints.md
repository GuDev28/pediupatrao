# 3. Documentação dos endpoints

[← Análise e projeto](02-analise-e-projeto.md) · [Índice](README.md) · [Evidências de testes →](04-evidencias-de-testes.md)

Base: `http://localhost:8080/api/dashboard`. Todos os endpoints são **`GET`**, respondem **JSON (UTF-8)** e são **somente leitura**.

Os exemplos de resposta abaixo são **respostas reais** da aplicação. Alguns trechos longos foram encurtados e estão indicados.

## 3.1 Autenticação e autorização

| Forma | Quando usar | Exemplo |
|---|---|---|
| Sessão do navegador (login em `/login`) | A própria tela `/dashboard` | O `fetch` envia o cookie de sessão automaticamente |
| HTTP Basic no cabeçalho `Authorization` | Postman, curl, testes | `curl -u gerente01:gerente123 http://localhost:8080/api/dashboard/resumo` |

| Perfil | Acesso |
|---|---|
| GERENTE | ✅ todos os endpoints |
| ADMIN | ✅ todos os endpoints (nomes de clientes mascarados em `pedidos/em-aberto` e `pedidos`) |
| ATENDENTE | ❌ 403 em todos (tentativa registrada na auditoria) |
| Sem login | ❌ 401 |

Qualquer método diferente de `GET` (`POST`, `PUT`, `PATCH`, `DELETE`) em `/api/dashboard/**` responde **403**. As respostas trazem `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`.

## 3.2 Filtros comuns

Aceitos por `resumo`, `vendas/serie`, `vendas/distribuicao`, `produtos/mais-vendidos`, `pedidos/situacao` e `pedidos`:

| Parâmetro | Tipo | Obrigatório | Padrão | Descrição |
|---|---|---|---|---|
| `dataInicio` | data `aaaa-mm-dd` | não | hoje − 29 dias | Primeiro dia do período (inclusivo), pela data de entrada do pedido |
| `dataFim` | data `aaaa-mm-dd` | não | hoje | Último dia do período (inclusivo) |
| `tipoEntrega` | `ENTREGA` \| `RETIRADA` | não | todos | Forma de saída do pedido |
| `atendente` | texto | não | todos | Usuário que registrou o pedido (`registradoPor`) |

Regras:
- Só `dataInicio`: vai até hoje. Só `dataFim`: os 30 dias que terminam nela.
- Período máximo de **366 dias**, com `dataInicio` ≤ `dataFim`; fora disso, **422**.
- As opções não diferenciam maiúsculas, acentos, espaços nem hífens: `entrega`, `Dia-Semana` e `em preparação` são aceitos.
- Valor vazio é tratado como "não informado".
- Toda resposta devolve o `periodo` efetivamente usado e `geradoEm` (data/hora de Brasília).

## 3.3 Resumo dos endpoints

| # | Método e rota | Requisito | Descrição |
|---|---|---|---|
| 1 | `GET /api/dashboard/filtros` | RF05 | Opções aceitas pelos filtros e período padrão |
| 2 | `GET /api/dashboard/resumo` | RF01 | Indicadores gerais com comparação ao período anterior |
| 3 | `GET /api/dashboard/vendas/serie` | RF02 | Evolução das vendas por dia, semana ou mês |
| 4 | `GET /api/dashboard/vendas/distribuicao` | RF02 | Faturamento por hora, dia da semana, tipo de entrega ou categoria |
| 5 | `GET /api/dashboard/produtos/mais-vendidos` | RF04 | Ranking de produtos |
| 6 | `GET /api/dashboard/pedidos/situacao` | RF03 | Quantidade por status e tempos médios |
| 7 | `GET /api/dashboard/pedidos/em-aberto` | RF03 | Fila de pedidos em aberto agora, com atrasados |
| 8 | `GET /api/dashboard/pedidos` | RF03/RF05 | Lista paginada dos pedidos do período |

---

## 1. `GET /api/dashboard/filtros`

Devolve os valores aceitos pelos parâmetros, o período padrão e o período máximo. Usado pela tela para montar os campos de seleção.

**Parâmetros:** nenhum.

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/filtros"
```

**Resposta 200**
```json
{
  "periodoPadrao": {
    "dataInicio": "2026-10-05",
    "dataFim": "2026-11-03",
    "dias": 30
  },
  "periodoMaximoDias": 366,
  "tiposEntrega": [
    {
      "valor": "ENTREGA",
      "descricao": "Entrega"
    },
    {
      "valor": "RETIRADA",
      "descricao": "Retirada no balcão"
    }
  ],
  "status": [
    {
      "valor": "RECEBIDO",
      "descricao": "Recebido"
    },
    {
      "valor": "EM_PREPARACAO",
      "descricao": "Em preparação"
    },
    {
      "valor": "PRONTO",
      "descricao": "Pronto"
    },
    {
      "valor": "SAIU_PARA_ENTREGA",
      "descricao": "Saiu para entrega"
    },
    {
      "valor": "RETIRADO",
      "descricao": "Retirado"
    },
    {
      "valor": "FINALIZADO",
      "descricao": "Finalizado"
    },
    {
      "valor": "CANCELADO",
      "descricao": "Cancelado"
    }
  ],
  "categorias": ["Bebida", "Esfiha", "Lanche", "Pizza"],
  "atendentes": ["atendente01", "gerente01"],
  "agrupamentos": [
    {
      "valor": "DIA",
      "descricao": "Dia"
    },
    {
      "valor": "SEMANA",
      "descricao": "Semana"
    },
    {
      "valor": "MES",
      "descricao": "Mês"
    }
  ],
  "dimensoes": [
    {
      "valor": "HORA",
      "descricao": "Hora do dia"
    },
    {
      "valor": "DIA_SEMANA",
      "descricao": "Dia da semana"
    },
    {
      "valor": "TIPO_ENTREGA",
      "descricao": "Tipo de entrega"
    },
    {
      "valor": "CATEGORIA",
      "descricao": "Categoria de produto"
    }
  ],
  "ordenacoesRanking": [
    {
      "valor": "QUANTIDADE",
      "descricao": "Quantidade vendida"
    },
    {
      "valor": "FATURAMENTO",
      "descricao": "Faturamento"
    }
  ]
}
```

| Campo | Descrição |
|---|---|
| `periodoPadrao` | Período usado quando o cliente não envia datas |
| `periodoMaximoDias` | Tamanho máximo do período (366) |
| `tiposEntrega`, `status`, `agrupamentos`, `dimensoes`, `ordenacoesRanking` | `valor` (aceito pela API) e `descricao` (para exibição) |
| `categorias` | Categorias (tipos) do cadastro de produtos, sem repetição |
| `atendentes` | Usuários que já registraram pedidos |

**Erros:** 401, 403.

---

## 2. `GET /api/dashboard/resumo` — RF01

Indicadores gerais de vendas do período e comparação com o período anterior de mesma duração.

**Parâmetros:** filtros comuns.

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/resumo?dataInicio=2026-10-01&dataFim=2026-10-31"
```

**Resposta 200**
```json
{
  "periodo": {
    "dataInicio": "2026-10-01",
    "dataFim": "2026-10-31",
    "dias": 31
  },
  "periodoAnterior": {
    "dataInicio": "2026-08-31",
    "dataFim": "2026-09-30",
    "dias": 31
  },
  "faturamento": {
    "atual": 56282.37,
    "anterior": 52759.46,
    "variacaoPercentual": 6.7
  },
  "pedidos": {
    "atual": 539,
    "anterior": 499,
    "variacaoPercentual": 8.0
  },
  "ticketMedio": {
    "atual": 104.42,
    "anterior": 105.73,
    "variacaoPercentual": -1.2
  },
  "itensVendidos": {
    "atual": 1742,
    "anterior": 1748,
    "variacaoPercentual": -0.3
  },
  "pedidosCancelados": 25,
  "taxaCancelamento": 4.4,
  "valorCancelado": 2720.91,
  "pedidosComDesconto": 47,
  "valorDescontos": 519.03,
  "pedidosEmAndamento": 0,
  "geradoEm": "2026-11-03T17:24:25"
}
```

| Campo | Descrição |
|---|---|
| `periodo` / `periodoAnterior` | Período consultado e período de comparação (mesmo número de dias, terminando na véspera) |
| `faturamento`, `pedidos`, `ticketMedio`, `itensVendidos` | `{atual, anterior, variacaoPercentual}`. Consideram apenas pedidos **não cancelados**. `variacaoPercentual` é `null` quando o valor anterior é zero |
| `pedidosCancelados`, `taxaCancelamento`, `valorCancelado` | Cancelamentos do período. A taxa é sobre todos os pedidos registrados |
| `pedidosComDesconto`, `valorDescontos` | Descontos concedidos em pedidos válidos |
| `pedidosEmAndamento` | Pedidos do período ainda não finalizados nem cancelados |
| `geradoEm` | Momento do cálculo (horário de Brasília) |

**Erros:** 400 (data ou tipo inválido), 401, 403, 422 (período inválido).

---

## 3. `GET /api/dashboard/vendas/serie` — RF02

Evolução das vendas válidas no período. Intervalos sem venda aparecem com zero. A primeira e a última semana ou mês são recortadas para ficar dentro do período.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| filtros comuns | | | |
| `agrupamento` | `DIA` \| `SEMANA` \| `MES` | automático | Até 62 dias agrupa por `DIA`, até 182 por `SEMANA` (segunda a domingo), acima disso por `MES` |

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/vendas/serie?dataInicio=2026-10-01&dataFim=2026-10-31&agrupamento=SEMANA"
```

**Resposta 200**
```json
{
  "periodo": {
    "dataInicio": "2026-10-01",
    "dataFim": "2026-10-31",
    "dias": 31
  },
  "agrupamento": "SEMANA",
  "pontos": [
    {
      "inicio": "2026-10-01",
      "fim": "2026-10-04",
      "rotulo": "Sem. 01/10",
      "faturamento": 9329.34,
      "pedidos": 85,
      "ticketMedio": 109.76
    },
    {
      "inicio": "2026-10-05",
      "fim": "2026-10-11",
      "rotulo": "Sem. 05/10",
      "faturamento": 11970.47,
      "pedidos": 114,
      "ticketMedio": 105.00
    },
    {
      "inicio": "2026-10-12",
      "fim": "2026-10-18",
      "rotulo": "Sem. 12/10",
      "faturamento": 11894.66,
      "pedidos": 121,
      "ticketMedio": 98.30
    },
    {
      "inicio": "2026-10-19",
      "fim": "2026-10-25",
      "rotulo": "Sem. 19/10",
      "faturamento": 12054.03,
      "pedidos": 112,
      "ticketMedio": 107.63
    },
    {
      "inicio": "2026-10-26",
      "fim": "2026-10-31",
      "rotulo": "Sem. 26/10",
      "faturamento": 11033.87,
      "pedidos": 107,
      "ticketMedio": 103.12
    }
  ],
  "totalFaturamento": 56282.37,
  "totalPedidos": 539,
  "geradoEm": "2026-11-03T17:24:25"
}
```

**Erros:** 400 (`agrupamento` inválido, data ou tipo inválido), 401, 403, 422.

---

## 4. `GET /api/dashboard/vendas/distribuicao` — RF02

Distribuição do faturamento das vendas válidas.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| filtros comuns | | | |
| `dimensao` | `HORA` \| `DIA_SEMANA` \| `TIPO_ENTREGA` \| `CATEGORIA` | `HORA` | `HORA` traz as 24 horas (`chave` 0–23), `DIA_SEMANA` os 7 dias (`chave` 1 = segunda … 7 = domingo) e `TIPO_ENTREGA` as duas opções, todos mesmo com zero. `CATEGORIA` traz as categorias encontradas, da maior para a menor |

Em `CATEGORIA`:
- `faturamento` é o valor **líquido** dos itens: o desconto do pedido é rateado e fecha com o total.
- `pedidos` conta os pedidos que têm ao menos um item da categoria.
- `quantidadeItens` soma as quantidades.

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/vendas/distribuicao?dataInicio=2026-10-01&dataFim=2026-10-31&dimensao=TIPO_ENTREGA"
```

**Resposta 200**
```json
{
  "periodo": {
    "dataInicio": "2026-10-01",
    "dataFim": "2026-10-31",
    "dias": 31
  },
  "dimensao": "TIPO_ENTREGA",
  "itens": [
    {
      "chave": "ENTREGA",
      "rotulo": "Entrega",
      "pedidos": 354,
      "quantidadeItens": 1149,
      "faturamento": 36632.68,
      "percentualFaturamento": 65.1
    },
    {
      "chave": "RETIRADA",
      "rotulo": "Retirada no balcão",
      "pedidos": 185,
      "quantidadeItens": 593,
      "faturamento": 19649.69,
      "percentualFaturamento": 34.9
    }
  ],
  "totalFaturamento": 56282.37,
  "geradoEm": "2026-11-03T17:24:25"
}
```

Trecho de `dimensao=HORA` (apenas 19h–21h mostradas; a resposta traz as 24):
```json
{
  "periodo": {
    "dataInicio": "2026-10-01",
    "dataFim": "2026-10-31",
    "dias": 31
  },
  "dimensao": "HORA",
  "itens": [
    {
      "chave": "19",
      "rotulo": "19h",
      "pedidos": 102,
      "quantidadeItens": 310,
      "faturamento": 10172.00,
      "percentualFaturamento": 18.1
    },
    {
      "chave": "20",
      "rotulo": "20h",
      "pedidos": 123,
      "quantidadeItens": 372,
      "faturamento": 12178.47,
      "percentualFaturamento": 21.6
    },
    {
      "chave": "21",
      "rotulo": "21h",
      "pedidos": 97,
      "quantidadeItens": 317,
      "faturamento": 11006.98,
      "percentualFaturamento": 19.6
    }
  ],
  "totalFaturamento": 56282.37,
  "geradoEm": "2026-11-03T17:26:38"
}
```

**Erros:** 400 (`dimensao` inválida), 401, 403, 422.

---

## 5. `GET /api/dashboard/produtos/mais-vendidos` — RF04

Ranking dos produtos vendidos (pedidos válidos).

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| filtros comuns | | | |
| `ordenarPor` | `QUANTIDADE` \| `FATURAMENTO` | `QUANTIDADE` | Critério principal. Empates são decididos pelo outro critério e depois pelo nome |
| `categoria` | texto | todas | Considera só os itens dessa categoria (sem diferenciar maiúsculas) |
| `limite` | inteiro 1–50 | 10 | Quantidade de produtos devolvidos |

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/produtos/mais-vendidos?dataInicio=2026-10-01&dataFim=2026-10-31&ordenarPor=FATURAMENTO&limite=3"
```

**Resposta 200** (2 dos 3 itens mostrados)
```json
{
  "periodo": {
    "dataInicio": "2026-10-01",
    "dataFim": "2026-10-31",
    "dias": 31
  },
  "ordenarPor": "FATURAMENTO",
  "categoria": null,
  "limite": 3,
  "totalProdutos": 11,
  "quantidadeTotal": 1742,
  "faturamentoTotal": 56282.37,
  "itens": [
    {
      "posicao": 1,
      "produtoId": "prod0",
      "nome": "Pizza Calabresa",
      "categoria": "Pizza",
      "quantidade": 233,
      "faturamento": 12703.82,
      "pedidos": 189,
      "percentualQuantidade": 13.4,
      "percentualFaturamento": 22.6
    },
    {
      "posicao": 2,
      "produtoId": "prod2",
      "nome": "Pizza Quatro Queijos",
      "categoria": "Pizza",
      "quantidade": 141,
      "faturamento": 8362.05,
      "pedidos": 117,
      "percentualQuantidade": 8.1,
      "percentualFaturamento": 14.9
    }
  ],
  "geradoEm": "2026-11-03T17:24:25"
}
```

| Campo | Descrição |
|---|---|
| `totalProdutos` | Quantos produtos diferentes foram vendidos (antes do limite) |
| `quantidadeTotal`, `faturamentoTotal` | Totais de referência para os percentuais. `faturamentoTotal` coincide com o faturamento do `resumo` quando não há filtro de categoria |
| `itens[].percentualQuantidade` / `percentualFaturamento` | Participação do produto no total |
| `itens[].pedidos` | Em quantos pedidos o produto apareceu |

**Erros:** 400 (`ordenarPor` inválido; `limite` fora de 1–50 ou não numérico), 401, 403, 422.

---

## 6. `GET /api/dashboard/pedidos/situacao` — RF03

Situação de **todos** os pedidos registrados no período, inclusive os cancelados.

**Parâmetros:** filtros comuns.

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/pedidos/situacao?dataInicio=2026-10-01&dataFim=2026-10-31"
```

**Resposta 200**
```json
{
  "periodo": {
    "dataInicio": "2026-10-01",
    "dataFim": "2026-10-31",
    "dias": 31
  },
  "totalPedidos": 564,
  "porStatus": [
    {
      "status": "RECEBIDO",
      "descricao": "Recebido",
      "quantidade": 0,
      "percentual": 0.0,
      "valorTotal": 0.00
    },
    {
      "status": "EM_PREPARACAO",
      "descricao": "Em preparação",
      "quantidade": 0,
      "percentual": 0.0,
      "valorTotal": 0.00
    },
    {
      "status": "PRONTO",
      "descricao": "Pronto",
      "quantidade": 0,
      "percentual": 0.0,
      "valorTotal": 0.00
    },
    {
      "status": "SAIU_PARA_ENTREGA",
      "descricao": "Saiu para entrega",
      "quantidade": 0,
      "percentual": 0.0,
      "valorTotal": 0.00
    },
    {
      "status": "RETIRADO",
      "descricao": "Retirado",
      "quantidade": 0,
      "percentual": 0.0,
      "valorTotal": 0.00
    },
    {
      "status": "FINALIZADO",
      "descricao": "Finalizado",
      "quantidade": 539,
      "percentual": 95.6,
      "valorTotal": 56282.37
    },
    {
      "status": "CANCELADO",
      "descricao": "Cancelado",
      "quantidade": 25,
      "percentual": 4.4,
      "valorTotal": 2720.91
    }
  ],
  "emAndamento": 0,
  "finalizados": 539,
  "cancelados": 25,
  "tempoMedioAteSaidaMinutos": 46.0,
  "tempoMedioAteFinalizacaoMinutos": 63.2,
  "geradoEm": "2026-11-03T17:24:25"
}
```

| Campo | Descrição |
|---|---|
| `porStatus` | Os 7 status na ordem do fluxo, sempre presentes, com `quantidade`, `percentual` e `valorTotal`. Pedidos antigos sem status aparecem numa linha extra "Não informado" |
| `emAndamento` / `finalizados` / `cancelados` | Agrupamentos operacionais |
| `tempoMedioAteSaidaMinutos` | Média de minutos entre entrada e saída (pedidos que já saíram). `null` se nenhum saiu |
| `tempoMedioAteFinalizacaoMinutos` | Média de minutos entre entrada e finalização. `null` se nenhum foi finalizado |

**Erros:** 400, 401, 403, 422.

---

## 7. `GET /api/dashboard/pedidos/em-aberto` — RF03

Fila em **tempo real**: todos os pedidos que ainda não foram finalizados nem cancelados, **sem filtro de período** (um pedido de ontem ainda aberto precisa aparecer), do que espera há mais tempo para o mais recente.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| `tipoEntrega` | `ENTREGA` \| `RETIRADA` | todos | Filtra a fila |
| `limiteMinutos` | inteiro 5–240 | 45 | A partir de quantos minutos desde a entrada o pedido é considerado atrasado |
| `limite` | inteiro 1–100 | 20 | Máximo de linhas em `pedidos`. `total` e `atrasados` contam todos |

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/pedidos/em-aberto?limite=2"
```

**Resposta 200**
```json
{
  "limiteMinutos": 45,
  "total": 8,
  "atrasados": 3,
  "pedidos": [
    {
      "id": "6650a1b2c3d4e5f6a7eae6e8",
      "codigo": "EAE6E8",
      "clienteNome": "João Pedro Santos",
      "tipoEntrega": "RETIRADA",
      "status": "PRONTO",
      "statusDescricao": "Pronto",
      "valorTotal": 99.80,
      "dataHoraEntrada": "2026-11-03T16:08:52",
      "minutosDesdeEntrada": 75,
      "atrasado": true
    },
    {
      "id": "6650a1b2c3d4e5f6a7eac7f9",
      "codigo": "EAC7F9",
      "clienteNome": "Bruno Carvalho",
      "tipoEntrega": "RETIRADA",
      "status": "EM_PREPARACAO",
      "statusDescricao": "Em preparação",
      "valorTotal": 38.90,
      "dataHoraEntrada": "2026-11-03T16:22:52",
      "minutosDesdeEntrada": 61,
      "atrasado": true
    }
  ],
  "geradoEm": "2026-11-03T17:24:25"
}
```

Para o **ADMIN**, `clienteNome` vem mascarado, por exemplo "J*** P*** S***".

**Erros:** 400 (`tipoEntrega`, `limiteMinutos` ou `limite` inválidos), 401, 403.

---

## 8. `GET /api/dashboard/pedidos` — RF03/RF05

Pedidos do período, do mais recente para o mais antigo, paginados. Traz apenas dados de visão gerencial: sem histórico, observação nem justificativas.

| Parâmetro | Tipo | Padrão | Descrição |
|---|---|---|---|
| filtros comuns | | | |
| `status` | um dos 7 status | todos | Filtra por status |
| `pagina` | inteiro ≥ 0 | 0 | Página (começa em 0) |
| `tamanho` | inteiro 1–100 | 10 | Itens por página |

**Exemplo de requisição**
```bash
curl -u gerente01:gerente123 "http://localhost:8080/api/dashboard/pedidos?dataInicio=2026-10-01&dataFim=2026-10-31&status=FINALIZADO&tamanho=2"
```

**Resposta 200** (1 dos 2 itens mostrados)
```json
{
  "conteudo": [
    {
      "id": "6650a1b2c3d4e5f6a7e4fb29",
      "codigo": "E4FB29",
      "clienteNome": "Ana Paula Ribeiro",
      "tipoEntrega": "ENTREGA",
      "status": "FINALIZADO",
      "statusDescricao": "Finalizado",
      "quantidadeItens": 1,
      "valorTotal": 9.00,
      "percentualDesconto": 0,
      "dataHoraEntrada": "2026-10-31T22:56:00",
      "dataHoraSaida": "2026-10-31T23:45:00",
      "registradoPor": "atendente01",
      "minutosAtendimento": 74
    }
  ],
  "pagina": 0,
  "tamanho": 2,
  "totalElementos": 539,
  "totalPaginas": 270
}
```

`minutosAtendimento` é o tempo da entrada até a finalização (ou até a saída, se ainda não finalizado). É `null` para pedidos em andamento ou cancelados. Para o ADMIN, `clienteNome` vem mascarado.

**Erros:** 400 (`status`, `pagina` ou `tamanho` inválidos), 401, 403, 422.

---

## 3.4 Respostas de erro

Todas as respostas de erro usam o formato padrão do sistema (`ErroResposta`):

| Campo | Descrição |
|---|---|
| `status` | Código HTTP |
| `erro` | Nome do código HTTP |
| `mensagem` | Explicação em português do que corrigir |
| `caminho` | Rota chamada |
| `dataHora` | Momento do erro |

| HTTP | Quando acontece | Exemplos de causa |
|---|---|---|
| **400** Bad Request | Parâmetro com formato ou valor inválido | data `03/11/2026`, `tipoEntrega=DRONE`, `agrupamento=ANO`, `limite=500`, `limite=dez`, `status=ENTREGUE`, `tamanho=1000` |
| **401** Unauthorized | Sem login, sessão expirada ou senha incorreta | — |
| **403** Forbidden | Perfil sem permissão (ATENDENTE) ou método diferente de GET | Tentativa registrada na auditoria como `ACESSO_NEGADO` |
| **422** Unprocessable Entity | Parâmetros válidos que violam regra de negócio | `dataInicio` depois de `dataFim`; período maior que 366 dias |
| **500** Internal Server Error | Falha inesperada (ex.: banco indisponível) | A página de erro não expõe detalhes internos |

**422: data inicial posterior à final**
```json
{
  "status": 422,
  "erro": "Unprocessable Entity",
  "mensagem": "A data inicial (2026-11-03) não pode ser posterior à data final (2026-10-01).",
  "caminho": "/api/dashboard/resumo",
  "dataHora": "2026-11-03T17:26:38"
}
```

**400: data em formato inválido**
```json
{
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "O parâmetro 'dataInicio' deve ser uma data válida no formato aaaa-mm-dd (recebido: '03/11/2026').",
  "caminho": "/api/dashboard/resumo",
  "dataHora": "2026-11-03T17:26:38"
}
```

**400: opção inexistente** (a mensagem lista os valores aceitos)
```json
{
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Valor inválido para 'agrupamento': 'ANO'. Valores aceitos: DIA, SEMANA, MES.",
  "caminho": "/api/dashboard/vendas/serie",
  "dataHora": "2026-11-03T17:26:38"
}
```

**400: número inválido**
```json
{
  "status": 400,
  "erro": "Bad Request",
  "mensagem": "Valor inválido para o parâmetro 'limite': 'dez' (esperado um número inteiro).",
  "caminho": "/api/dashboard/produtos/mais-vendidos",
  "dataHora": "2026-11-03T17:26:38"
}
```

**403: perfil sem permissão**
```json
{
  "status": 403,
  "erro": "Forbidden",
  "mensagem": "Acesso negado: o perfil ATENDENTE não tem permissão para esta operação.",
  "caminho": "/api/dashboard/resumo",
  "dataHora": "2026-11-03T17:26:38"
}
```

**401: sem autenticação**
```json
{
  "status": 401,
  "erro": "Unauthorized",
  "mensagem": "Autenticação necessária. Faça login ou envie credenciais válidas.",
  "caminho": "/api/dashboard/resumo",
  "dataHora": "2026-11-03T17:26:38"
}
```

## 3.5 Coleção Postman e roteiro de testes

- [`PediuPatrao-Dashboard.postman_collection.json`](PediuPatrao-Dashboard.postman_collection.json): importe no Postman e rode a coleção inteira (Runner). Cada requisição já verifica o código HTTP e os campos esperados.
- [`testes-api.sh`](testes-api.sh): o mesmo roteiro com `curl` (Git Bash). Uso: `bash docs/dashboard/testes-api.sh`.
