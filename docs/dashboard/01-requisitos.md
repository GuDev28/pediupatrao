# 1. Requisitos do Dashboard Administrativo

[← Índice](README.md) · [Análise e projeto →](02-analise-e-projeto.md)

## 1.1 Contexto e objetivo

O Pediu Patrão já registra pedidos com itens, valores calculados no servidor, fluxo de status (entrada → preparo → saída → finalização), descontos, cancelamentos e auditoria. O que faltava era uma **visão gerencial** desses dados. O responsável pelo estabelecimento não tinha onde ver quanto vendeu, quais produtos saem mais, em que horários há mais movimento nem quais pedidos estão atrasados.

O **Dashboard Administrativo** resolve isso com:

- uma **API REST de leitura** (`/api/dashboard/**`) que consulta o MongoDB e calcula os indicadores no servidor;
- uma **tela** (`/dashboard`) que consome essa API e apresenta indicadores, gráficos, rankings e listas, com filtros e atualização automática.

**Público-alvo:** perfis **GERENTE** (responsável pelo estabelecimento) e **ADMIN**. O **ATENDENTE** não tem acesso, pois faturamento e desempenho são informações gerenciais.

## 1.2 Atores

| Ator | Interesse no dashboard |
|---|---|
| GERENTE | Acompanhar vendas, ticket médio, cancelamentos, descontos concedidos, produtos campeões, horários de pico e pedidos atrasados, para decidir promoções, escala de equipe e estoque. |
| ADMIN | Visão geral da operação. Por minimização de dados pessoais, os nomes de clientes aparecem mascarados para ele (mesmo critério já usado na auditoria). |
| ATENDENTE | Sem acesso: a tentativa é bloqueada (HTTP 403) e registrada na auditoria. |

## 1.3 Requisitos funcionais

### RF01 — Indicadores gerais de vendas

O sistema deve apresentar, para o período filtrado:

| Indicador | Definição |
|---|---|
| Faturamento | Soma do `valorTotal` (já com desconto) dos pedidos **válidos** (não cancelados) |
| Pedidos | Quantidade de pedidos válidos |
| Ticket médio | Faturamento ÷ pedidos válidos (R$ 0,00 quando não há pedidos) |
| Itens vendidos | Soma das quantidades dos itens dos pedidos válidos |
| Cancelamentos | Quantidade, taxa (% sobre todos os pedidos registrados) e valor dos pedidos cancelados |
| Descontos concedidos | Quantidade de pedidos com desconto e valor total descontado |
| Em andamento | Pedidos do período que ainda não foram finalizados nem cancelados |

Faturamento, pedidos, ticket médio e itens são **comparados com o período anterior de mesma duração** (ex.: 30 dias × os 30 dias anteriores), com variação percentual. Sem base de comparação, a API informa `null` e a tela mostra "Sem base de comparação".

**Atendido por:** `GET /api/dashboard/resumo` e os cartões de indicadores da tela.

### RF02 — Análise de vendas (gráficos)

- **Evolução das vendas** ao longo do período, agrupada por **dia, semana ou mês** (automático conforme o tamanho do período, ou escolhido pelo usuário), alternando entre faturamento, pedidos e ticket médio. Dias sem venda aparecem com zero.
- **Distribuição do faturamento** por **hora do dia** (horário de pico), **dia da semana**, **categoria de produto** e **tipo de entrega**, com destaque automático do maior valor.

**Atendido por:** `GET /api/dashboard/vendas/serie` e `GET /api/dashboard/vendas/distribuicao`, com gráfico de linha e gráfico de barras.

### RF03 — Situação dos pedidos

- Quantidade e percentual de pedidos **por status** no período (todos os 7 status, inclusive os com zero), mais os agrupamentos *em andamento*, *finalizados* e *cancelados*.
- **Tempos médios de atendimento:** entrada até a saída e entrada até a finalização.
- **Fila em tempo real** dos pedidos em aberto, do mais antigo ao mais recente, com o tempo de espera e o destaque dos **atrasados** (acima de um limite configurável, padrão 45 min).
- **Lista paginada** dos pedidos do período, filtrável por status. Clicar em um status no gráfico filtra a lista.

**Atendido por:** `GET /api/dashboard/pedidos/situacao`, `GET /api/dashboard/pedidos/em-aberto` e `GET /api/dashboard/pedidos`.

### RF04 — Produtos mais vendidos

Ranking dos produtos vendidos no período (pedidos válidos), ordenável por **quantidade** ou **faturamento**, com filtro por **categoria** e limite (Top 5/10/20, até 50 pela API). Cada produto mostra posição, categoria, quantidade, faturamento líquido, número de pedidos e participação percentual.

**Atendido por:** `GET /api/dashboard/produtos/mais-vendidos`.

### RF05 — Filtros de consulta

| Filtro | Onde vale | Regras |
|---|---|---|
| Período (`dataInicio`, `dataFim`) | Todos os painéis, exceto a fila em tempo real | Datas `aaaa-mm-dd`, inclusivas. Padrão: últimos 30 dias. Máximo de 366 dias. Atalhos na tela: Hoje, 7 dias, 30 dias, Este mês, 90 dias |
| Tipo de entrega (`tipoEntrega`) | Todos os painéis | `ENTREGA` ou `RETIRADA` |
| Registrado por (`atendente`) | Todos, exceto a fila | Usuário que registrou o pedido |
| Status (`status`) | Lista de pedidos | Um dos 7 status |
| Categoria (`categoria`) | Ranking de produtos | Categorias do cadastro de produtos |
| Agrupamento, dimensão, ordenação e limites | Painel correspondente | Valores listados em `GET /api/dashboard/filtros` |

Os filtros ficam numa única barra acima de todos os painéis e são mantidos na URL, de modo que é possível recarregar a página ou compartilhar o link com os mesmos filtros.

### RF06 — Integração com a API

A tela não calcula nem acessa o banco: ela busca todos os números da API `/api/dashboard/**` via `fetch` com a sessão do usuário logado. A API também pode ser usada por outras ferramentas (Postman, curl) com HTTP Basic, como as demais APIs do projeto.

### RF07 — Atualização e tratamento de dados

**Atualização**
- Botão **Atualizar** e **atualização automática** configurável (desligada, 30 s, 1 min ou 5 min). A atualização pausa enquanto a aba está oculta e retoma ao voltar.
- Indicação de **"Atualizado às HH:MM:SS"**, e todas as respostas trazem `geradoEm`.
- Requisições antigas são **canceladas** quando um filtro muda, para que uma resposta atrasada não sobrescreva a mais recente.
- Durante a atualização, a tela anterior permanece visível (esmaecida), sem piscar.
- As respostas não são guardadas em cache (`Cache-Control: no-store`).

**Tratamento de dados**
- Validação dos filtros no navegador e no servidor. Formato inválido gera **400** e regra violada gera **422**, ambos com mensagem explicativa.
- Valores monetários em `BigDecimal` com 2 casas, e percentuais e médias com 1 casa (arredondamento HALF_UP).
- Desconto do pedido **rateado entre os itens**, de forma que o faturamento por produto e por categoria feche exatamente com o faturamento total.
- Dados antigos ou incompletos não quebram os cálculos:
  - valor nulo conta como zero;
  - pedido sem data é ignorado nas séries;
  - item sem categoria usa a categoria atual do cadastro, ou "Sem categoria";
  - intervalos de tempo negativos são descartados.
- Datas no horário de Brasília (`America/Sao_Paulo`).
- Erros de conexão mantêm os últimos dados e informam o horário deles. Sessão expirada (401) leva ao login.
- Textos vindos da API são escapados antes de ir para o HTML (proteção contra XSS).

## 1.4 Requisitos não funcionais

| Código | Categoria | Requisito | Como foi atendido |
|---|---|---|---|
| RNF01 | Segurança | Somente GERENTE e ADMIN acessam o dashboard e a API | Regra por rota no `SecurityConfig` e `@PreAuthorize` no `DashboardService` (duas camadas). Testado em `DashboardApiTest` e `DashboardAutorizacaoTest` |
| RNF02 | Segurança | API somente leitura | Apenas `GET` é liberado; `POST`/`PUT`/`DELETE` são negados (403) |
| RNF03 | Segurança / auditoria | Tentativas de acesso indevido registradas | O `AcessoNegadoHandler` já existente grava `ACESSO_NEGADO` na auditoria |
| RNF04 | Privacidade (LGPD) | Minimização de dados pessoais | O ADMIN vê nomes de clientes mascarados (`M*** S***`). A API não expõe telefone, endereço, observações nem justificativas |
| RNF05 | Desempenho | Respostas rápidas para o volume de uma pizzaria (milhares de pedidos por mês) | Filtros aplicados no MongoDB com índices em `dataHoraEntrada` e `status`, projeção só dos campos necessários e período limitado a 366 dias |
| RNF06 | Usabilidade | Painel legível e consistente com o sistema | Mesmo layout, menu e Bootstrap das demais telas. Uma cor por gráfico, sem eixo duplo, tooltips, formato brasileiro (R$ 1.234,56; 05/10/2026) |
| RNF07 | Acessibilidade | Informação não depende só de cor nem de mouse | Variações com seta, sinal e texto; atrasos com ícone e texto; botão "Tabela" em cada gráfico; `aria-label` e regiões `aria-live` |
| RNF08 | Responsividade | Uso em celular e tablet | Grid do Bootstrap, sem rolagem horizontal da página (verificado a 390 px de largura) |
| RNF09 | Confiabilidade | Valores monetários exatos | `BigDecimal`/`Decimal128`, sem `double` nos cálculos |
| RNF10 | Disponibilidade | Gráficos funcionam sem internet | Chart.js 2.9.4 já distribuído em `static/plugins`. Sem ele, a tela mostra as tabelas com os mesmos números |
| RNF11 | Manutenibilidade e testabilidade | Regras isoladas e testadas | Cálculos em `CalculadoraDashboard` (sem Spring nem banco). 71 testes novos. Suíte total: 139 testes, sem precisar de MongoDB |
| RNF12 | Compatibilidade | Não quebrar o que existe | Nenhum endpoint existente mudou de contrato. Os 68 testes anteriores continuam passando |
| RNF13 | Portabilidade da demonstração | Demonstrar sem poluir o banco real | Perfil `demo` usa outro banco (`pediupatrao_demo`) e gera histórico de exemplo apenas se a coleção estiver vazia |

## 1.5 Regras de negócio

| Código | Regra |
|---|---|
| RN01 | **Venda válida** é todo pedido não cancelado, inclusive os ainda em andamento: o cancelamento só pode ocorrer antes da saída e é contabilizado à parte |
| RN02 | O período considera a **data/hora de entrada** do pedido, no horário de Brasília, com as duas datas inclusivas. Na consulta, o intervalo é semiaberto: `[dataInicio 00:00, dataFim+1 00:00)` |
| RN03 | Sem datas, o período é **os últimos 30 dias incluindo hoje**. Só `dataInicio`: até hoje. Só `dataFim`: os 30 dias que terminam nela |
| RN04 | `dataInicio` posterior a `dataFim` ou período maior que **366 dias** é rejeitado com 422 |
| RN05 | O **período de comparação** tem a mesma quantidade de dias e termina na véspera de `dataInicio` |
| RN06 | Variação % = (atual − anterior) ÷ anterior × 100; `null` quando o anterior é zero |
| RN07 | Taxa de cancelamento = cancelados ÷ total de pedidos registrados no período × 100 |
| RN08 | **Faturamento por item** = subtotal do item × (100 − % de desconto do pedido) ÷ 100. Os centavos de arredondamento vão para o último item, para fechar com o total do pedido |
| RN09 | **Categoria do item** = a gravada no item no momento do pedido (novo campo). Para pedidos antigos, a categoria atual do produto ou "Sem categoria" |
| RN10 | No ranking, empates são decididos pelo outro critério (faturamento ou quantidade) e depois pelo nome do produto |
| RN11 | **Pedido em aberto** é o que não está FINALIZADO nem CANCELADO. Considera-se **atrasado** quando o tempo desde a entrada passa do limite (padrão 45 min, de 5 a 240) |
| RN12 | Agrupamento automático da série: até 62 dias por **dia**, até 182 dias por **semana** (segunda a domingo), acima disso por **mês** |
| RN13 | O ADMIN vê o nome do cliente mascarado nas listas. O GERENTE vê o nome completo |
