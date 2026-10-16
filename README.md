# PediuPatrão — Evolução do Sistema de Pedidos (Prática M1 · Parte 2)

Sistema de pedidos de pizzaria em **Java 21 + Spring Boot 3.4 + Spring Security + MongoDB (Atlas) + Thymeleaf**.
Esta versão evolui o projeto fornecido mantendo a arquitetura em camadas (entidades → repositórios → serviços → controladores/telas) e acrescenta:

- três perfis de acesso (**ADMIN**, **GERENTE** e **ATENDENTE**) com autorização aplicada no backend;
- pedido com itens, quantidades, preços, desconto, total calculado no servidor, responsável e data/hora de entrada;
- fluxo obrigatório de status com registro de entrada e saída e bloqueio de transições inválidas;
- cancelamento (com justificativa) e desconto (até 20%) exclusivos do GERENTE;
- auditoria persistente e imutável no MongoDB para pedidos, clientes, produtos, usuários e tentativas de acesso negado.

---

## 1. Como executar

### Pré-requisitos
- JDK 21
- Acesso à internet (MongoDB Atlas e download das dependências pelo Maven)

### Executando
```bash
# Linux/macOS/Git Bash
./mvnw spring-boot:run

# Windows (PowerShell ou CMD)
mvnw.cmd spring-boot:run
```
Acesse **http://localhost:8080** (também é possível rodar a classe `PizzariaPedidosApplication` pela IDE).

### Banco de dados
A conexão continua sendo a do Atlas fornecida no projeto, mas a aplicação usa um **banco próprio** (`pediupatrao_gustavo`) para não misturar usuários e pedidos com o banco compartilhado `pizzaria`. Os dois valores podem ser trocados por variáveis de ambiente:

| Variável | Padrão |
|---|---|
| `MONGODB_URI` | URI do Atlas fornecida no projeto |
| `MONGODB_DATABASE` | `pediupatrao_gustavo` |

Na primeira execução são criadas as coleções (incluindo `auditoria`), os índices e alguns produtos de exemplo.

### Usuários iniciais
Criados automaticamente se ainda não existirem (`DataInitializer`). As senhas podem ser alteradas pelas variáveis `SENHA_ADMIN`, `SENHA_GERENTE` e `SENHA_ATENDENTE` e nunca são gravadas no log.

| Usuário | Senha | Perfil |
|---|---|---|
| `admin` | `teste123` | ADMIN |
| `gerente01` | `gerente123` | GERENTE |
| `atendente01` | `atendente123` | ATENDENTE |

Usuários antigos com role `USER` (ou sem role) são migrados na inicialização: o `admin` vira ADMIN e os demais recebem o perfil de menor privilégio (ATENDENTE).

### Testes automatizados
```bash
./mvnw test        # Windows: mvnw.cmd test
```
São 68 testes (JUnit 5 + Mockito + AssertJ + MockMvc) que **não precisam de MongoDB**:

| Classe de teste | O que verifica |
|---|---|
| `SegurancaApiTest` | Requisições diretas às APIs passando pela cadeia real do Spring Security: 401/403 por perfil, GERENTE como único que cancela/aplica desconto, auditoria só leitura, `/api/usuarios` não é mais público, senha não aparece no JSON e CSRF obrigatório para sessões do navegador. |
| `AutorizacaoServicosTest` | Segunda camada: o `@PreAuthorize` dos serviços bloqueia o perfil errado mesmo se o serviço for chamado diretamente. |
| `PedidoServiceTest` | Cálculo do total no servidor (ignorando `valorTotal`, `status` e preços enviados), fluxo completo de status, transições inválidas, saída com data/hora e responsável, cancelamento, desconto e bloqueios após cancelamento. |
| `ClienteServiceTest`, `UsuarioServiceTest`, `AuditoriaServiceTest` | Auditoria de endereço/telefone com valores anterior e novo, proteção contra autoelevação, senha nunca auditada, mascaramento de dados pessoais. |
| `StatusPedidoTest`, `PedidoTest`, `CampoAlteradoTest`, `MapeamentoMongoTest` | Regras das entidades e o mapeamento para o MongoDB (Decimal128, enums, datas, auditoria imutável). |

---

## 2. Requisitos implementados

### RF01 — Usuários e permissões

| Funcionalidade | ADMIN | GERENTE | ATENDENTE |
|---|:-:|:-:|:-:|
| Administrar usuários e atribuir perfis | ✅ | ❌ | ❌ |
| Consultar pedidos | ✅ | ✅ | ✅ |
| Registrar pedido / alterar itens (enquanto RECEBIDO) | ❌ | ✅ | ✅ |
| Avançar status (preparo, pronto, saída, finalização) | ❌ | ✅ | ✅ |
| **Aplicar desconto** | ❌ | ✅ | ❌ |
| **Cancelar pedido** | ❌ | ✅ | ❌ |
| Consultar clientes | ❌ | ✅ | ✅ |
| Cadastrar e atualizar clientes | ❌ | ❌ | ✅ |
| Excluir cliente (somente sem pedidos) | ❌ | ✅ | ❌ |
| Consultar produtos | ✅ | ✅ | ✅ |
| Manter produtos e preços | ❌ | ✅ | ❌ |
| Visualizar auditoria | ✅ | ✅ | ❌ |

Como a regra é aplicada:
- **`UsuarioDetailsService`** passou a atribuir a autoridade do perfil gravado (`ROLE_ADMIN`, `ROLE_GERENTE`, `ROLE_ATENDENTE`) em vez da role fixa `USER`.
- **`SecurityConfig`** define as permissões por rota e método HTTP para telas e APIs, com **negação por padrão** (`anyRequest().denyAll()`): o que não está explicitamente liberado é bloqueado.
- **`@EnableMethodSecurity` + `@PreAuthorize`** nos serviços (`PedidoService.cancelar` e `aplicarDesconto` exigem `hasRole('GERENTE')`, `UsuarioService` inteiro exige ADMIN etc.). Mesmo que um controlador novo chame o serviço, o perfil é verificado.
- Os menus e botões são exibidos conforme o perfil, mas isso é só conveniência visual.

Administração segura de perfis (proteção contra autoelevação):
- `/api/usuarios` era **público** (qualquer pessoa podia criar um ADMIN); agora é exclusivo do ADMIN, assim como `/usuarios/**`.
- O perfil é um `enum` (`Perfil`): valores fora de ADMIN/GERENTE/ATENDENTE são rejeitados.
- Ninguém altera o próprio perfil ou username nem exclui a si mesmo; o último ADMIN não pode ser rebaixado nem excluído.
- Os serviços copiam apenas os campos permitidos (não aceitam `id` ou outros campos enviados no formulário/JSON).
- Ao alterar o perfil/senha ou excluir um usuário, as sessões abertas dele são encerradas (`SessionRegistry`), para que as permissões antigas não continuem valendo.
- A senha (hash BCrypt) nunca é devolvida pela API (`@JsonProperty(WRITE_ONLY)`) nem enviada para a tela de edição.

### RF02 — Evolução do cadastro de pedidos
`Pedido` agora tem cliente, tipo de saída (`ENTREGA`/`RETIRADA`), observação, lista de `ItemPedido` (produto, nome, quantidade, preço unitário e subtotal), subtotal, percentual e valor do desconto, valor total, status, data/hora e responsável de entrada, saída, finalização, desconto e cancelamento, histórico de status e controle de versão (`@Version`) para evitar que duas alterações simultâneas se sobrescrevam.

O valor é **sempre calculado no servidor** (`Pedido.recalcularValores()`):
- a requisição (`PedidoRequest`) só aceita cliente, tipo de saída, observação e pares produto/quantidade;
- o preço de cada item vem do cadastro de produtos; campos como `valorTotal`, `precoUnitario`, `status` ou `percentualDesconto` enviados na requisição são ignorados;
- validações: cliente existente, pelo menos um item, quantidade de 1 a 99, produto existente/ativo/com preço, observação até 500 caracteres;
- valores monetários usam `BigDecimal` com 2 casas (`HALF_UP`) e são gravados como `Decimal128` no MongoDB.

### RF03 — Entrada, saída e transições

```
RECEBIDO ──► EM_PREPARACAO ──► PRONTO ──► SAIU_PARA_ENTREGA (pedido de ENTREGA) ──► FINALIZADO
                                     └──► RETIRADO          (pedido de RETIRADA) ──► FINALIZADO

CANCELADO: somente pelo GERENTE, com justificativa, a partir de RECEBIDO, EM_PREPARACAO ou PRONTO.
FINALIZADO e CANCELADO são estados finais.
```
- **Entrada**: ao registrar, o pedido recebe `RECEBIDO`, `dataHoraEntrada` e `registradoPor`.
- **Saída**: ao passar para `SAIU_PARA_ENTREGA` ou `RETIRADO`, são gravados obrigatoriamente `dataHoraSaida` e `responsavelSaida` (usuário autenticado).
- Transições fora do fluxo (ex.: finalizar um pedido em preparação, pular etapas, voltar status, pedido de retirada "sair para entrega") são recusadas com HTTP 422 e mensagem explicando o próximo passo permitido.
- O endpoint original `PUT /api/pedidos/{id}/status?status=...` foi mantido e agora valida as transições.
- Cada mudança entra no histórico do pedido (exibido na tela de detalhes) e na auditoria.

### RF04 — Cancelamentos e descontos (somente GERENTE)
- **Cancelamento**: exige justificativa (10 a 500 caracteres), só é permitido antes da saída, preserva itens/valores/histórico e bloqueia qualquer alteração posterior (status, desconto, itens ou novo cancelamento).
- **Desconto**: percentual de 0% a **20%** do valor do pedido (0% remove o desconto), no máximo duas casas decimais, apenas antes da saída e nunca em pedido cancelado. Um novo desconto substitui o anterior (não acumula). Motivo opcional.
- Ambas as operações vão para a auditoria com usuário, data/hora, valores anteriores e novos (status; percentual, valor do desconto e total) e justificativa.

### RF05 — Auditoria
Coleção `auditoria` (`RegistroAuditoria`, `RegistroAuditoriaRepository`, `AuditoriaService`). Cada registro contém:

| Campo | Exemplo |
|---|---|
| Usuário e perfil | `atendente01` / `ATENDENTE` |
| Data e hora | `23/09/2026 - 14:35:00` (horário de Brasília) |
| Operação | `ALTERACAO_CLIENTE` (Alteração de cliente) |
| Entidade e identificador | `Cliente` / `6650a1...` |
| Campos alterados | `logradouro`: `Rua das Flores` → `Av. Brasil` |
| Justificativa | obrigatória no cancelamento; opcional no desconto |

Operações auditadas: inclusão e alteração de pedidos, mudanças de status, descontos, cancelamentos, inclusão/alteração/exclusão de clientes (cada campo de endereço e telefone com valor anterior e novo), produtos, usuários/perfis e **tentativas de acesso negado** (ex.: ATENDENTE tentando cancelar).

Integridade e proteção de dados:
- **Imutável**: a entidade não tem setters e o repositório estende `Repository` (não `MongoRepository`), expondo só `save` de novos registros e consultas — não existe `delete` nem `update`. Não há endpoints de alteração/exclusão e o `SecurityConfig` ainda nega qualquer método diferente de GET em `/auditoria/**` e `/api/auditoria/**`.
- **Consulta restrita** a ADMIN e GERENTE (rota + `@PreAuthorize`), com filtros por entidade, id, operação, usuário e período, e paginação (tela `/auditoria` e API `GET /api/auditoria`).
- **Sem credenciais**: campos cujo nome contém senha/password/token/secret têm o valor substituído por `[valor protegido]` automaticamente (`CampoAlterado`); a troca de senha aparece apenas como "senha alterada". Logs não registram senhas, tokens nem cabeçalhos.
- **Minimização de dados pessoais**: o GERENTE (responsável pelo atendimento) vê os valores completos; para o ADMIN (cuja finalidade é administrar o sistema) nome, telefone e endereço de clientes aparecem mascarados (ex.: `(**) *****-7777`, `R***`).

---

## 3. Segurança das rotas e CSRF
- **CSRF ativado** (antes estava desativado). Formulários Thymeleaf com `th:action` recebem o token automaticamente; as chamadas `fetch()` das telas enviam o token lido de `<meta name="_csrf">` no `layout.html`. O logout passou a ser `POST`.
- Chamadas às APIs feitas por ferramentas (curl/Postman) usam **HTTP Basic** no cabeçalho `Authorization` e, por isso, ficam isentas de CSRF: o navegador não envia esse cabeçalho sozinho em requisições vindas de outro site, e a aplicação nunca envia o desafio `WWW-Authenticate`, então o navegador também não memoriza essas credenciais. Requisições com sessão de navegador continuam exigindo o token.
- APIs respondem **401/403 em JSON** (em vez de redirecionar para o login) e erros de regra de negócio respondem **422** com a mensagem.
- A lista de clientes passou a escapar o HTML vindo do cadastro (antes era vulnerável a XSS).
- Cookie de sessão `HttpOnly` e `SameSite=Lax`, sessão expira após 30 minutos e páginas de erro não exibem stack trace.

---

## 4. Principais alterações

| Camada | Arquivos |
|---|---|
| Configuração | `SecurityConfig` (perfis, negação por padrão, CSRF, Basic para APIs, sessões), `DataInitializer` (migração de perfis, usuários e produtos iniciais), `MongoInitConfig` (coleção `auditoria`), `AppConfig` (relógio em America/Sao_Paulo) |
| Segurança | `security/UsuarioLogado`, `AcessoNegadoHandler` (403 + auditoria da tentativa), `ApiAuthenticationEntryPoint` (401 JSON), `RespostaJson` |
| Entidades | `Perfil`, `Usuario` (role enum, senha write-only), `Pedido` (reescrito), `ItemPedido`, `HistoricoStatus`, `StatusPedido` (máquina de estados), `TipoEntrega`, `RegistroAuditoria`, `CampoAlterado`, `TipoOperacao` |
| DTOs | `PedidoRequest`, `ItemPedidoRequest`, `DescontoRequest`, `CancelamentoRequest`, `FiltroAuditoria`, `ErroResposta`, `PaginaResposta` |
| Repositórios | `RegistroAuditoriaRepository` (sem exclusão/alteração), consultas novas em `PedidoRepository` e `UsuarioRepository` |
| Serviços | `PedidoService` (regras RF02–RF04), `AuditoriaService`, `Alteracoes` (diferenças anterior/novo), `ClienteService`, `UsuarioService`, `ProdutoService` (validação + auditoria), `UsuarioDetailsService` |
| Controladores | `PedidoController` (API), `PedidoWebController` e `AuditoriaWebController` (telas), `AuditoriaController` (API), `ApiExceptionHandler`, `DadosDoUsuarioNasTelas`; `HomeController`, `ClienteController`, `UsuarioController` e `ProdutoController` ajustados |
| Telas | `layout` (token CSRF, mensagens), `sidebar` (menu por perfil, usuário logado, logout), `pedidos/lista`, `pedidos/form`, `pedidos/detalhe`, `auditoria/lista`, `usuarios/*` (três perfis), `clientes/lista`, `produtos/lista`, `login`, `home` |

---

## 5. Endpoints da API

| Método e rota | Perfis | Descrição |
|---|---|---|
| `GET /api/pedidos[?status=]` · `GET /api/pedidos/{id}` | ADMIN, GERENTE, ATENDENTE | Consulta |
| `POST /api/pedidos` | GERENTE, ATENDENTE | Registra (entrada) |
| `PUT /api/pedidos/{id}` | GERENTE, ATENDENTE | Altera itens enquanto RECEBIDO |
| `PUT /api/pedidos/{id}/status?status=` | GERENTE, ATENDENTE | Próxima etapa do fluxo |
| `POST /api/pedidos/{id}/desconto` | GERENTE | `{"percentual": 10, "justificativa": "..."}` |
| `POST /api/pedidos/{id}/cancelar` | GERENTE | `{"justificativa": "..."}` |
| `GET /api/auditoria?entidade=&entidadeId=&operacao=&usuario=&dataInicio=&dataFim=&pagina=&tamanho=` | ADMIN, GERENTE | Consulta paginada |
| `GET/POST/PUT/DELETE /api/usuarios` | ADMIN | Usuários e perfis |
| `GET /api/clientes` · `POST` · `PUT /{id}` · `DELETE /{id}` | GET: GERENTE/ATENDENTE · POST/PUT: ATENDENTE · DELETE: GERENTE | Clientes |
| `GET /api/produtos` · `POST` · `PUT` · `DELETE` | GET: todos · demais: GERENTE | Produtos |

---

## 6. Demonstração das restrições no backend

A pasta `docs/` contém a coleção **`PediuPatrao.postman_collection.json`** (importar no Postman e executar as pastas na ordem). Ela cria cliente e pedido, tenta operações proibidas para cada perfil e mostra os registros de auditoria resultantes; cada requisição já verifica o status HTTP esperado.

Os mesmos testes com `curl` (Git Bash; no PowerShell use `curl.exe`):

```bash
# ATENDENTE tentando cancelar -> 403 (e a tentativa fica na auditoria)
curl -i -u atendente01:atendente123 -X POST http://localhost:8080/api/pedidos/ID_DO_PEDIDO/cancelar \
     -H "Content-Type: application/json" -d '{"justificativa":"tentativa indevida"}'

# ADMIN tentando aplicar desconto -> 403
curl -i -u admin:teste123 -X POST http://localhost:8080/api/pedidos/ID_DO_PEDIDO/desconto \
     -H "Content-Type: application/json" -d '{"percentual":10}'

# GERENTE com desconto acima de 20% -> 422
curl -i -u gerente01:gerente123 -X POST http://localhost:8080/api/pedidos/ID_DO_PEDIDO/desconto \
     -H "Content-Type: application/json" -d '{"percentual":25}'

# Transição inválida (finalizar pedido em preparação) -> 422
curl -i -u atendente01:atendente123 -X PUT "http://localhost:8080/api/pedidos/ID_DO_PEDIDO/status?status=FINALIZADO"

# Criar usuário ADMIN sem login (antes era público) -> bloqueado
curl -i -X POST http://localhost:8080/api/usuarios -H "Content-Type: application/json" \
     -d '{"username":"invasor","password":"123456","role":"ADMIN"}'

# Auditoria: ATENDENTE -> 403; GERENTE -> 200
curl -i -u atendente01:atendente123 http://localhost:8080/api/auditoria
curl -s -u gerente01:gerente123 "http://localhost:8080/api/auditoria?entidade=Pedido&entidadeId=ID_DO_PEDIDO"
```

---

## 7. Decisões de projeto
- **Quem avança o status**: GERENTE ("gerenciar pedidos") e ATENDENTE ("acompanhar o andamento"), pois na operação da pizzaria é o atendimento quem registra preparo, pronto e saída. O ADMIN só consulta.
- **Responsável pela saída**: o usuário autenticado que registra a saída (entrega ou retirada), gravado junto com a data/hora.
- **Alteração de itens**: permitida apenas enquanto o pedido está RECEBIDO (antes de ir para a cozinha), mantendo o desconto já autorizado.
- **Exclusão de cliente**: não estava atribuída a nenhum perfil; foi restrita ao GERENTE e bloqueada quando o cliente já tem pedidos, para preservar o histórico.
- **Produtos**: o preço define o total dos pedidos (informação comercial), por isso a manutenção é do GERENTE e é auditada.
