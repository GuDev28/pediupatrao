/*
 * Dashboard administrativo - Pediu Patrão
 *
 * Consome a API /api/dashboard/** (RF06) e monta indicadores, gráficos e tabelas.
 * RF07 - atualização e tratamento de dados:
 *  - atualização manual e automática (pausa quando a aba não está visível);
 *  - requisições antigas são canceladas quando um filtro muda (AbortController),
 *    evitando que uma resposta atrasada sobrescreva a mais recente;
 *  - durante a atualização a tela anterior continua visível (esmaecida), sem "piscar";
 *  - erros da API (400/422) mostram a mensagem do servidor; 401 leva ao login;
 *    falha de conexão mantém os últimos dados e informa o horário deles;
 *  - todo texto vindo da API é escapado antes de ir para o HTML.
 * Sem dependências além do Chart.js 2.9.4 já distribuído com o projeto.
 */
(function () {
    'use strict';

    const raiz = document.getElementById('dashboard');
    if (!raiz) {
        return;
    }
    const API = raiz.dataset.api || '/api/dashboard';
    const URL_LOGIN = raiz.dataset.login || '/login';
    const URL_PEDIDO = raiz.dataset.pedidos || '/pedidos/';

    // ===================== cores (uma série por gráfico; status com rótulo) =====================
    const COR = {
        azul: '#2a78d6',
        azulFundo: 'rgba(42, 120, 214, 0.10)',
        bom: '#0ca30c',
        critico: '#d03b3b',
        grade: '#ecebe8',
        eixo: '#d9d8d4',
        texto: '#52514e'
    };

    const SELO_STATUS = {
        RECEBIDO: 'text-bg-secondary',
        EM_PREPARACAO: 'text-bg-warning',
        PRONTO: 'text-bg-info',
        SAIU_PARA_ENTREGA: 'text-bg-primary',
        RETIRADO: 'text-bg-primary',
        FINALIZADO: 'text-bg-success',
        CANCELADO: 'text-bg-danger'
    };

    // ===================== formatação (pt-BR) =====================
    const fmtMoeda = new Intl.NumberFormat('pt-BR', {style: 'currency', currency: 'BRL'});
    const fmtInteiro = new Intl.NumberFormat('pt-BR');
    const fmtDecimal = new Intl.NumberFormat('pt-BR', {minimumFractionDigits: 1, maximumFractionDigits: 1});
    const fmtCompacto = new Intl.NumberFormat('pt-BR', {notation: 'compact', maximumFractionDigits: 1});

    const moeda = (v) => fmtMoeda.format(Number(v) || 0);
    const inteiro = (v) => fmtInteiro.format(Number(v) || 0);
    const percentual = (v) => fmtDecimal.format(Number(v) || 0) + '%';

    function dataBR(iso) {
        if (!iso) {
            return '—';
        }
        const [a, m, d] = iso.substring(0, 10).split('-');
        return `${d}/${m}/${a}`;
    }

    function dataHoraCurta(iso) {
        if (!iso) {
            return '—';
        }
        const [data, hora] = iso.split('T');
        const [, m, d] = data.split('-');
        return `${d}/${m} ${hora.substring(0, 5)}`;
    }

    function duracao(minutos) {
        if (minutos === null || minutos === undefined) {
            return '—';
        }
        const m = Math.round(Number(minutos));
        if (m < 60) {
            return `${m} min`;
        }
        const h = Math.floor(m / 60);
        const resto = m % 60;
        return resto ? `${h} h ${resto} min` : `${h} h`;
    }

    function escapar(texto) {
        return String(texto === null || texto === undefined ? '' : texto)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    const $ = (id) => document.getElementById(id);
    const telaEstreita = () => window.innerWidth < 576;

    // ===================== estado da tela =====================
    const estado = {
        opcoes: null,
        hoje: null,
        serieMedida: 'faturamento',
        pedidosPagina: 0,
        ultimaAtualizacao: null,
        carregamentos: 0,
        temporizador: null,
        controladores: {},
        dados: {},
        graficos: {}
    };

    // ===================== acesso à API =====================
    class ErroApi extends Error {
        constructor(status, mensagem) {
            super(mensagem);
            this.status = status;
        }
    }

    async function api(caminho, parametros, sinal) {
        const url = new URL(API + caminho, window.location.origin);
        Object.keys(parametros || {}).forEach((chave) => {
            const valor = parametros[chave];
            if (valor !== '' && valor !== null && valor !== undefined) {
                url.searchParams.set(chave, valor);
            }
        });
        let resposta;
        try {
            resposta = await fetch(url, {
                headers: {Accept: 'application/json'},
                credentials: 'same-origin',
                cache: 'no-store',
                signal: sinal
            });
        } catch (e) {
            if (e.name === 'AbortError') {
                throw e;
            }
            throw new ErroApi(0, 'Não foi possível conectar ao servidor.');
        }
        let corpo = null;
        try {
            corpo = await resposta.json();
        } catch (e) {
            corpo = null;
        }
        if (resposta.status === 401) {
            throw new ErroApi(401, 'Sua sessão expirou. Você será levado para a tela de login.');
        }
        if (!resposta.ok) {
            const mensagem = corpo && corpo.mensagem ? corpo.mensagem : `Erro ${resposta.status} ao consultar o dashboard.`;
            throw new ErroApi(resposta.status, mensagem);
        }
        if (corpo === null || typeof corpo !== 'object') {
            throw new ErroApi(resposta.status, 'A API respondeu em um formato inesperado.');
        }
        return corpo;
    }

    // ===================== filtros (RF05) =====================
    const campos = {
        inicio: $('dash-inicio'),
        fim: $('dash-fim'),
        tipo: $('dash-tipo'),
        atendente: $('dash-atendente')
    };

    function filtrosComuns() {
        return {
            dataInicio: campos.inicio.value,
            dataFim: campos.fim.value,
            tipoEntrega: campos.tipo.value,
            atendente: campos.atendente.value
        };
    }

    function somarDias(iso, dias) {
        const d = new Date(iso + 'T12:00:00');
        d.setDate(d.getDate() + dias);
        return d.toISOString().substring(0, 10);
    }

    function diasEntre(inicio, fim) {
        return Math.round((new Date(fim + 'T12:00:00') - new Date(inicio + 'T12:00:00')) / 86400000) + 1;
    }

    function aplicarPreset(preset) {
        const hoje = estado.hoje;
        campos.fim.value = hoje;
        if (preset === 'hoje') {
            campos.inicio.value = hoje;
        } else if (preset === 'mes') {
            campos.inicio.value = hoje.substring(0, 8) + '01';
        } else {
            campos.inicio.value = somarDias(hoje, -(Number(preset) - 1));
        }
        destacarPreset();
    }

    function destacarPreset() {
        const inicio = campos.inicio.value;
        const fim = campos.fim.value;
        document.querySelectorAll('#dash-presets [data-preset]').forEach((botao) => {
            const p = botao.dataset.preset;
            let ativo = false;
            if (fim === estado.hoje) {
                if (p === 'hoje') {
                    ativo = inicio === fim;
                } else if (p === 'mes') {
                    ativo = inicio === estado.hoje.substring(0, 8) + '01' && inicio !== fim;
                } else {
                    ativo = inicio === somarDias(estado.hoje, -(Number(p) - 1));
                }
            }
            botao.classList.toggle('active', ativo);
            botao.setAttribute('aria-pressed', ativo ? 'true' : 'false');
        });
    }

    /** Validação no navegador (a API valida de novo e é a fonte da verdade). */
    function validarFiltros() {
        const erro = $('dash-erro-filtro');
        const inicio = campos.inicio.value;
        const fim = campos.fim.value;
        let mensagem = '';
        if (inicio && fim && inicio > fim) {
            mensagem = 'A data inicial não pode ser posterior à data final.';
        } else if (inicio && fim && estado.opcoes && diasEntre(inicio, fim) > estado.opcoes.periodoMaximoDias) {
            mensagem = `O período pode ter no máximo ${estado.opcoes.periodoMaximoDias} dias.`;
        }
        campos.inicio.classList.toggle('is-invalid', !!mensagem);
        campos.fim.classList.toggle('is-invalid', !!mensagem);
        erro.textContent = mensagem;
        erro.hidden = !mensagem;
        if (mensagem && !estado.ultimaAtualizacao) {
            $('dash-atualizado').textContent = 'Corrija o período para carregar os dados';
        }
        return !mensagem;
    }

    /** Mantém os filtros na URL: dá para atualizar a página ou compartilhar o link. */
    function salvarNaUrl() {
        const parametros = new URLSearchParams();
        const f = filtrosComuns();
        Object.keys(f).forEach((k) => {
            if (f[k]) {
                parametros.set(k, f[k]);
            }
        });
        const busca = parametros.toString();
        window.history.replaceState(null, '', window.location.pathname + (busca ? '?' + busca : ''));
    }

    function lerDaUrl() {
        const p = new URLSearchParams(window.location.search);
        return {
            dataInicio: p.get('dataInicio') || '',
            dataFim: p.get('dataFim') || '',
            tipoEntrega: p.get('tipoEntrega') || '',
            atendente: p.get('atendente') || ''
        };
    }

    function preencherSelect(select, opcoes, primeiro) {
        const atual = select.value;
        select.innerHTML = '';
        if (primeiro) {
            select.appendChild(new Option(primeiro, ''));
        }
        opcoes.forEach((o) => select.appendChild(new Option(o.descricao, o.valor)));
        if (Array.from(select.options).some((o) => o.value === atual)) {
            select.value = atual;
        }
    }

    function aplicarOpcoes(opcoes, daUrl) {
        estado.opcoes = opcoes;
        estado.hoje = opcoes.periodoPadrao.dataFim;
        preencherSelect(campos.tipo, opcoes.tiposEntrega, 'Todos');
        preencherSelect(campos.atendente, opcoes.atendentes.map((a) => ({valor: a, descricao: a})), 'Todos');
        preencherSelect($('ranking-categoria'), opcoes.categorias.map((c) => ({valor: c, descricao: c})),
            'Todas as categorias');
        preencherSelect($('pedidos-status'), opcoes.status, 'Todos os status');

        campos.inicio.value = daUrl.dataInicio || opcoes.periodoPadrao.dataInicio;
        campos.fim.value = daUrl.dataFim || opcoes.periodoPadrao.dataFim;
        if (daUrl.tipoEntrega) {
            campos.tipo.value = daUrl.tipoEntrega;
        }
        if (daUrl.atendente) {
            if (!opcoes.atendentes.includes(daUrl.atendente)) {
                campos.atendente.appendChild(new Option(daUrl.atendente, daUrl.atendente));
            }
            campos.atendente.value = daUrl.atendente;
        }
        destacarPreset();
    }

    // ===================== alertas =====================
    function mostrarAlerta(mensagem, tipo) {
        const alerta = $('dash-alerta');
        alerta.className = `alert alert-${tipo || 'warning'} d-flex align-items-start gap-2 py-2 small`;
        $('dash-alerta-texto').textContent = mensagem;
        alerta.hidden = false;
    }

    function esconderAlerta() {
        $('dash-alerta').hidden = true;
    }

    function tratarErros(erros) {
        if (!erros.length) {
            return;
        }
        if (erros.some((e) => e.status === 401)) {
            mostrarAlerta('Sua sessão expirou. Redirecionando para o login…', 'danger');
            setTimeout(() => {
                window.location.href = URL_LOGIN;
            }, 2000);
            return;
        }
        if (erros.some((e) => e.status === 0)) {
            const quando = estado.ultimaAtualizacao
                ? ` Os dados exibidos são de ${estado.ultimaAtualizacao.toLocaleTimeString('pt-BR')}.` : '';
            mostrarAlerta('Não foi possível conectar ao servidor.' + quando + ' Tente atualizar novamente.', 'warning');
            return;
        }
        const mensagens = [];
        erros.forEach((e) => {
            if (!mensagens.includes(e.message)) {
                mensagens.push(e.message);
            }
        });
        const grave = erros.some((e) => e.status >= 500 || e.status === 403);
        mostrarAlerta(mensagens.join(' '), grave ? 'danger' : 'warning');
    }

    // ===================== painéis: cada um sabe buscar e desenhar seus dados =====================
    const paineis = {
        resumo: {
            buscar: (sinal) => api('/resumo', filtrosComuns(), sinal),
            desenhar: desenharResumo
        },
        serie: {
            buscar: (sinal) => api('/vendas/serie',
                Object.assign(filtrosComuns(), {agrupamento: $('serie-agrupamento').value}), sinal),
            desenhar: desenharSerie
        },
        distribuicao: {
            buscar: (sinal) => api('/vendas/distribuicao',
                Object.assign(filtrosComuns(), {dimensao: $('distribuicao-dimensao').value}), sinal),
            desenhar: desenharDistribuicao
        },
        situacao: {
            buscar: (sinal) => api('/pedidos/situacao', filtrosComuns(), sinal),
            desenhar: desenharSituacao
        },
        ranking: {
            buscar: (sinal) => api('/produtos/mais-vendidos', Object.assign(filtrosComuns(), {
                ordenarPor: $('ranking-ordem').value,
                categoria: $('ranking-categoria').value,
                limite: $('ranking-limite').value
            }), sinal),
            desenhar: desenharRanking
        },
        fila: {
            buscar: (sinal) => api('/pedidos/em-aberto', {
                tipoEntrega: campos.tipo.value,
                limiteMinutos: $('fila-limite').value
            }, sinal),
            desenhar: desenharFila
        },
        pedidos: {
            buscar: (sinal) => api('/pedidos', Object.assign(filtrosComuns(), {
                status: $('pedidos-status').value,
                pagina: estado.pedidosPagina,
                tamanho: 10
            }), sinal),
            desenhar: desenharPedidos
        }
    };

    /** Atualiza os painéis informados (ou todos). */
    async function carregar(nomes) {
        const lista = nomes || Object.keys(paineis);
        if (!validarFiltros()) {
            return;
        }
        salvarNaUrl();
        estado.carregamentos += 1;
        raiz.classList.add('carregando');
        $('dash-atualizar').disabled = true;

        const execucoes = lista.map((nome) => {
            if (estado.controladores[nome]) {
                estado.controladores[nome].abort();
            }
            const controlador = new AbortController();
            estado.controladores[nome] = controlador;
            return paineis[nome].buscar(controlador.signal).then((dados) => {
                if (controlador.signal.aborted) {
                    return null;
                }
                estado.dados[nome] = dados;
                paineis[nome].desenhar(dados);
                return null;
            }).catch((erro) => {
                if (erro.name === 'AbortError') {
                    return null;
                }
                if (!(erro instanceof ErroApi)) {
                    console.error('Falha ao exibir o painel ' + nome, erro);
                    return new ErroApi(-1, 'Não foi possível exibir parte dos dados.');
                }
                return erro;
            });
        });

        const resultados = await Promise.all(execucoes);
        const erros = resultados.filter((r) => r);
        estado.carregamentos -= 1;
        if (estado.carregamentos === 0) {
            raiz.classList.remove('carregando');
            $('dash-atualizar').disabled = false;
        }
        if (erros.length) {
            tratarErros(erros);
        } else {
            esconderAlerta();
            estado.ultimaAtualizacao = new Date();
            $('dash-atualizado').textContent = 'Atualizado às ' + estado.ultimaAtualizacao.toLocaleTimeString('pt-BR');
        }
    }

    // ===================== RF01 - indicadores =====================
    function desenharResumo(r) {
        const formatos = {
            faturamento: moeda,
            pedidos: inteiro,
            ticketMedio: moeda,
            itensVendidos: inteiro
        };
        Object.keys(formatos).forEach((chave) => {
            const cartao = raiz.querySelector(`[data-kpi="${chave}"]`);
            const indicador = r[chave];
            cartao.querySelector('[data-valor]').textContent = formatos[chave](indicador.atual);
            cartao.querySelector('[data-delta]').innerHTML = textoVariacao(indicador, formatos[chave]);
        });
        $('kpi-cancelados').textContent = inteiro(r.pedidosCancelados);
        $('kpi-cancelados-detalhe').textContent =
            `${percentual(r.taxaCancelamento)} dos pedidos · ${moeda(r.valorCancelado)}`;
        $('kpi-descontos').textContent = moeda(r.valorDescontos);
        $('kpi-descontos-detalhe').textContent = `em ${inteiro(r.pedidosComDesconto)} pedido(s)`;
        $('kpi-andamento').textContent = inteiro(r.pedidosEmAndamento);
        $('kpi-comparacao').textContent = `Período: ${dataBR(r.periodo.dataInicio)} a ${dataBR(r.periodo.dataFim)} `
            + `(${r.periodo.dias} dias). Variações comparadas a ${dataBR(r.periodoAnterior.dataInicio)} a `
            + `${dataBR(r.periodoAnterior.dataFim)}. Faturamento considera pedidos não cancelados.`;
    }

    /** Variação com seta, sinal e texto: a cor nunca é a única pista. */
    function textoVariacao(indicador, formato) {
        const v = indicador.variacaoPercentual;
        const anterior = `<span class="text-muted">antes: ${escapar(formato(indicador.anterior))}</span>`;
        if (v === null || v === undefined) {
            return `<span class="text-muted">Sem base de comparação</span>`;
        }
        const numero = Number(v);
        if (numero === 0) {
            return `<span>= estável</span> · ${anterior}`;
        }
        const classe = numero > 0 ? 'sobe' : 'desce';
        const seta = numero > 0 ? '▲' : '▼';
        const sinal = numero > 0 ? '+' : '−';
        return `<span class="${classe}">${seta} ${sinal}${fmtDecimal.format(Math.abs(numero))}%</span> · ${anterior}`;
    }

    // ===================== RF02 - evolução das vendas =====================
    const MEDIDAS = {
        faturamento: {rotulo: 'Faturamento', formato: moeda, eixo: (v) => 'R$ ' + fmtCompacto.format(v)},
        pedidos: {rotulo: 'Pedidos', formato: inteiro, eixo: (v) => fmtCompacto.format(v)},
        ticketMedio: {rotulo: 'Ticket médio', formato: moeda, eixo: (v) => 'R$ ' + fmtCompacto.format(v)}
    };

    /** Linha vertical que acompanha o mouse no gráfico de linha (crosshair). */
    const pluginLinhaGuia = {
        afterDatasetsDraw(grafico) {
            const ativos = grafico.tooltip && grafico.tooltip._active;
            if (!ativos || !ativos.length) {
                return;
            }
            const x = ativos[0]._view.x;
            const eixoY = grafico.scales['y-axis-0'];
            const ctx = grafico.ctx;
            ctx.save();
            ctx.beginPath();
            ctx.moveTo(x, eixoY.top);
            ctx.lineTo(x, eixoY.bottom);
            ctx.lineWidth = 1;
            ctx.strokeStyle = 'rgba(0, 0, 0, 0.25)';
            ctx.stroke();
            ctx.restore();
        }
    };

    function desenharSerie(r) {
        const medida = MEDIDAS[estado.serieMedida];
        const pontos = r.pontos || [];
        const nomeAgrupamento = {DIA: 'dia', SEMANA: 'semana', MES: 'mês'}[r.agrupamento] || '';
        $('serie-resumo').textContent = `Total: ${moeda(r.totalFaturamento)} em ${inteiro(r.totalPedidos)} pedidos`
            + ` · agrupado por ${nomeAgrupamento}`;

        alternarVazio('painel-serie', r.totalPedidos === 0);

        const rotulos = pontos.map((p) => p.rotulo);
        const valores = pontos.map((p) => Number(p[estado.serieMedida]) || 0);
        const config = {
            type: 'line',
            data: {
                labels: rotulos,
                datasets: [{
                    label: medida.rotulo,
                    data: valores,
                    borderColor: COR.azul,
                    backgroundColor: COR.azulFundo,
                    borderWidth: 2,
                    pointRadius: pontos.length > 45 ? 0 : 2,
                    pointBackgroundColor: COR.azul,
                    pointHoverRadius: 5,
                    pointHitRadius: 12,
                    lineTension: 0.25,
                    fill: true
                }]
            },
            options: opcoesBase({
                legend: {display: false},
                hover: {mode: 'index', intersect: false},
                tooltips: {
                    mode: 'index',
                    intersect: false,
                    callbacks: {
                        title: (itens) => {
                            const p = pontos[itens[0].index];
                            return p.inicio === p.fim ? dataBR(p.inicio) : `${dataBR(p.inicio)} a ${dataBR(p.fim)}`;
                        },
                        label: (item) => {
                            const p = pontos[item.index];
                            return [`Faturamento: ${moeda(p.faturamento)}`, `Pedidos: ${inteiro(p.pedidos)}`,
                                `Ticket médio: ${moeda(p.ticketMedio)}`];
                        }
                    }
                },
                scales: {
                    xAxes: [{gridLines: {display: false},
                        ticks: {maxRotation: 0, autoSkip: true, maxTicksLimit: telaEstreita() ? 5 : 12}}],
                    yAxes: [eixoValores(medida.eixo)]
                }
            }),
            plugins: [pluginLinhaGuia]
        };
        desenharGrafico('serie', 'grafico-serie', config);

        tabelaAlternativa('tabela-serie', ['Período', 'Faturamento', 'Pedidos', 'Ticket médio'], pontos.map((p) => [
            p.inicio === p.fim ? dataBR(p.inicio) : `${dataBR(p.inicio)} a ${dataBR(p.fim)}`,
            moeda(p.faturamento), inteiro(p.pedidos), moeda(p.ticketMedio)
        ]));
    }

    // ===================== RF02 - distribuição =====================
    function desenharDistribuicao(r) {
        let itens = r.itens || [];
        const total = Number(r.totalFaturamento) || 0;
        alternarVazio('painel-distribuicao', total === 0);

        if (r.dimensao === 'HORA') {
            // Mostra só o intervalo de horas com vendas (o resto do dia é zero)
            const comVenda = itens.map((i, n) => (i.pedidos > 0 ? n : -1)).filter((n) => n >= 0);
            if (comVenda.length) {
                itens = itens.slice(comVenda[0], comVenda[comVenda.length - 1] + 1);
            }
        }
        const maior = itens.reduce((a, b) => (b.pedidos > 0 && (!a || Number(b.faturamento) > Number(a.faturamento)) ? b : a), null);
        const nomeDimensao = {HORA: 'Horário de pico', DIA_SEMANA: 'Dia mais forte', CATEGORIA: 'Categoria líder',
            TIPO_ENTREGA: 'Maior participação'}[r.dimensao];
        $('distribuicao-destaque').textContent = maior
            ? `${nomeDimensao}: ${maior.rotulo} — ${percentual(maior.percentualFaturamento)} do faturamento `
              + `(${moeda(maior.faturamento)} em ${inteiro(maior.pedidos)} pedidos)`
            : '';

        const horizontal = r.dimensao === 'CATEGORIA' || r.dimensao === 'TIPO_ENTREGA';
        const eixoNumerico = eixoValores((v) => 'R$ ' + fmtCompacto.format(v));
        // Barras verticais (horas/dias): rótulos pulam automaticamente em telas estreitas
        const eixoCategorias = {gridLines: {display: false}, ticks: {autoSkip: !horizontal, maxRotation: 0}};
        const config = {
            type: horizontal ? 'horizontalBar' : 'bar',
            data: {
                labels: itens.map((i) => i.rotulo),
                datasets: [{
                    label: 'Faturamento',
                    data: itens.map((i) => Number(i.faturamento) || 0),
                    backgroundColor: COR.azul,
                    hoverBackgroundColor: '#1c5cab',
                    barPercentage: 0.85,
                    categoryPercentage: 0.9,
                    maxBarThickness: 36
                }]
            },
            options: opcoesBase({
                legend: {display: false},
                tooltips: {
                    callbacks: {
                        title: (t) => itens[t[0].index].rotulo,
                        label: (t) => {
                            const i = itens[t.index];
                            return [`Faturamento: ${moeda(i.faturamento)} (${percentual(i.percentualFaturamento)})`,
                                `Pedidos: ${inteiro(i.pedidos)} · itens: ${inteiro(i.quantidadeItens)}`];
                        }
                    }
                },
                scales: horizontal
                    ? {xAxes: [eixoNumerico], yAxes: [eixoCategorias]}
                    : {xAxes: [eixoCategorias], yAxes: [eixoNumerico]}
            })
        };
        desenharGrafico('distribuicao', 'grafico-distribuicao', config);

        tabelaAlternativa('tabela-distribuicao', ['', 'Faturamento', '%', 'Pedidos', 'Itens'],
            (r.itens || []).filter((i) => i.pedidos > 0).map((i) => [i.rotulo, moeda(i.faturamento),
                percentual(i.percentualFaturamento), inteiro(i.pedidos), inteiro(i.quantidadeItens)]));
    }

    // ===================== RF03 - situação dos pedidos =====================
    function desenharSituacao(r) {
        $('sit-andamento').textContent = inteiro(r.emAndamento);
        $('sit-finalizados').textContent = inteiro(r.finalizados);
        $('sit-cancelados').textContent = inteiro(r.cancelados);
        $('sit-tempos').innerHTML = `<i class="bi bi-stopwatch me-1"></i>Tempo médio até a saída: `
            + `<strong>${escapar(duracao(r.tempoMedioAteSaidaMinutos))}</strong> · até a finalização: `
            + `<strong>${escapar(duracao(r.tempoMedioAteFinalizacaoMinutos))}</strong>`;

        const status = r.porStatus || [];
        const cores = status.map((s) => (s.status === 'CANCELADO' ? COR.critico
            : (s.status === 'FINALIZADO' ? COR.bom : COR.azul)));
        const config = {
            type: 'horizontalBar',
            data: {
                labels: status.map((s) => s.descricao),
                datasets: [{
                    label: 'Pedidos',
                    data: status.map((s) => s.quantidade),
                    backgroundColor: cores,
                    barPercentage: 0.8,
                    categoryPercentage: 0.9,
                    maxBarThickness: 22
                }]
            },
            options: opcoesBase({
                legend: {display: false},
                // A linha inteira do status responde ao mouse/clique, não só a barra (alvo maior)
                hover: {mode: 'index', intersect: false, axis: 'y'},
                onClick: (evento, elementos) => {
                    if (elementos.length) {
                        filtrarPedidosPorStatus(status[elementos[0]._index].status);
                    }
                },
                onHover: (evento, elementos) => {
                    evento.target.style.cursor = elementos.length ? 'pointer' : 'default';
                },
                tooltips: {
                    mode: 'index',
                    intersect: false,
                    axis: 'y',
                    callbacks: {
                        label: (t) => {
                            const s = status[t.index];
                            return [`${inteiro(s.quantidade)} pedido(s) · ${percentual(s.percentual)}`,
                                `Valor: ${moeda(s.valorTotal)}`];
                        }
                    }
                },
                scales: {
                    xAxes: [eixoValores((v) => (Number.isInteger(v) ? fmtInteiro.format(v) : ''))],
                    yAxes: [{gridLines: {display: false}, ticks: {autoSkip: false}}]
                }
            })
        };
        desenharGrafico('situacao', 'grafico-situacao', config);

        tabelaAlternativa('tabela-situacao', ['Status', 'Pedidos', '%', 'Valor'], status.map((s) => [
            s.descricao, inteiro(s.quantidade), percentual(s.percentual), moeda(s.valorTotal)]));
    }

    function filtrarPedidosPorStatus(status) {
        $('pedidos-status').value = status || '';
        estado.pedidosPagina = 0;
        carregar(['pedidos']);
        $('painel-pedidos').scrollIntoView({behavior: 'smooth', block: 'start'});
    }

    // ===================== RF04 - produtos mais vendidos =====================
    function desenharRanking(r) {
        const corpo = $('ranking-linhas');
        const itens = r.itens || [];
        if (!itens.length) {
            corpo.innerHTML = '<tr><td colspan="5" class="text-center text-muted py-4">'
                + 'Nenhum produto vendido no período selecionado.</td></tr>';
            $('ranking-rodape').textContent = '';
            return;
        }
        const porFaturamento = r.ordenarPor === 'FATURAMENTO';
        const maximo = Math.max(...itens.map((i) => Number(porFaturamento ? i.faturamento : i.quantidade)), 1);
        corpo.innerHTML = itens.map((i) => {
            const valor = Number(porFaturamento ? i.faturamento : i.quantidade);
            const largura = Math.max(2, Math.round((valor / maximo) * 100));
            const participacao = porFaturamento ? i.percentualFaturamento : i.percentualQuantidade;
            return `<tr>
                <td class="ps-3 text-muted">${i.posicao}</td>
                <td>${escapar(i.nome)}<div class="categoria-produto">${escapar(i.categoria)}</div></td>
                <td class="text-end">${inteiro(i.quantidade)}</td>
                <td class="text-end">${moeda(i.faturamento)}</td>
                <td class="coluna-barra" title="${escapar(percentual(participacao))} do total">
                    <div class="barra-participacao" role="img" aria-label="${escapar(percentual(participacao))} do total">
                        <span style="width:${largura}%"></span></div>
                </td></tr>`;
        }).join('');
        const categoria = r.categoria ? ` da categoria ${r.categoria}` : '';
        $('ranking-rodape').textContent = `${inteiro(r.totalProdutos)} produto(s)${categoria} vendido(s) no período · `
            + `total: ${inteiro(r.quantidadeTotal)} itens, ${moeda(r.faturamentoTotal)} (valor líquido de descontos).`;
    }

    // ===================== RF03 - fila em tempo real =====================
    function seloStatus(status, descricao) {
        return `<span class="badge selo-status ${SELO_STATUS[status] || 'text-bg-secondary'}">${escapar(descricao)}</span>`;
    }

    function desenharFila(r) {
        $('fila-total').textContent = inteiro(r.total);
        $('fila-resumo').innerHTML = r.atrasados > 0
            ? `<span class="text-danger fw-semibold"><i class="bi bi-exclamation-triangle-fill me-1"></i>`
              + `${inteiro(r.atrasados)} pedido(s) atrasado(s)</span> <span class="text-muted">— em aberto há mais de `
              + `${r.limiteMinutos} min</span>`
            : `<span class="text-success"><i class="bi bi-check-circle-fill me-1"></i>Nenhum pedido atrasado</span>`
              + ` <span class="text-muted">(limite de ${r.limiteMinutos} min)</span>`;

        const corpo = $('fila-linhas');
        if (!r.pedidos.length) {
            corpo.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">Nenhum pedido em aberto.</td></tr>';
            return;
        }
        corpo.innerHTML = r.pedidos.map((p) => `<tr>
            <td class="ps-3"><a href="${escapar(URL_PEDIDO + encodeURIComponent(p.id))}">#${escapar(p.codigo)}</a>
                <div class="categoria-produto">${p.tipoEntrega === 'RETIRADA' ? 'Retirada' : 'Entrega'}</div></td>
            <td>${escapar(p.clienteNome || '—')}</td>
            <td>${seloStatus(p.status, p.statusDescricao)}</td>
            <td class="text-end pe-3 espera ${p.atrasado ? 'atrasado' : ''}">
                ${p.atrasado ? '<i class="bi bi-exclamation-triangle-fill me-1" aria-hidden="true"></i>' : ''}
                ${escapar(duracao(p.minutosDesdeEntrada))}${p.atrasado ? '<span class="visually-hidden"> (atrasado)</span>' : ''}
            </td></tr>`).join('');
    }

    // ===================== RF03/RF05 - pedidos do período =====================
    function desenharPedidos(pagina) {
        const corpo = $('pedidos-linhas');
        const linhas = pagina.conteudo || [];
        if (!linhas.length) {
            corpo.innerHTML = '<tr><td colspan="7" class="text-center text-muted py-4">'
                + 'Nenhum pedido encontrado com esses filtros.</td></tr>';
        } else {
            corpo.innerHTML = linhas.map((p) => {
                const desconto = Number(p.percentualDesconto) > 0
                    ? ` <span class="badge text-bg-light border text-success">−${escapar(fmtDecimal.format(p.percentualDesconto))}%</span>` : '';
                let atendimento = duracao(p.minutosAtendimento);
                if (p.minutosAtendimento === null && p.status !== 'CANCELADO') {
                    atendimento = 'em andamento';
                }
                return `<tr>
                    <td class="ps-3"><a href="${escapar(URL_PEDIDO + encodeURIComponent(p.id))}">#${escapar(p.codigo)}</a></td>
                    <td class="text-nowrap">${escapar(dataHoraCurta(p.dataHoraEntrada))}</td>
                    <td>${escapar(p.clienteNome || '—')}</td>
                    <td>${p.tipoEntrega === 'RETIRADA' ? 'Retirada' : (p.tipoEntrega === 'ENTREGA' ? 'Entrega' : '—')}</td>
                    <td>${seloStatus(p.status, p.statusDescricao)}</td>
                    <td class="text-end text-nowrap">${moeda(p.valorTotal)}${desconto}</td>
                    <td class="text-end pe-3 text-nowrap">${escapar(atendimento)}</td></tr>`;
            }).join('');
        }
        const inicio = pagina.totalElementos ? pagina.pagina * pagina.tamanho + 1 : 0;
        const fim = pagina.pagina * pagina.tamanho + linhas.length;
        $('pedidos-info').textContent = `Mostrando ${inteiro(inicio)}–${inteiro(fim)} de ${inteiro(pagina.totalElementos)} pedidos`;
        $('pedidos-anterior').disabled = pagina.pagina <= 0;
        $('pedidos-proxima').disabled = pagina.pagina + 1 >= pagina.totalPaginas;
    }

    // ===================== apoio aos gráficos =====================
    function opcoesBase(extra) {
        return Object.assign({
            responsive: true,
            maintainAspectRatio: false,
            animation: {duration: 250},
            layout: {padding: {top: 4, right: 8}}
        }, extra);
    }

    function eixoValores(formato) {
        return {
            gridLines: {color: COR.grade, drawBorder: false, zeroLineColor: COR.eixo, drawTicks: false},
            ticks: {beginAtZero: true, padding: 8, maxTicksLimit: 6, callback: formato}
        };
    }

    /** Reaproveita o gráfico quando o tipo é o mesmo (sem piscar); recria quando muda. */
    function desenharGrafico(nome, idCanvas, config) {
        if (typeof Chart === 'undefined') {
            // Sem a biblioteca de gráficos: mostra a tabela com os mesmos números
            const painel = $(idCanvas).closest('.painel');
            painel.querySelector('.grafico').hidden = true;
            painel.querySelector('.tabela-alternativa').hidden = false;
            return;
        }
        const existente = estado.graficos[nome];
        if (existente && existente.config.type === config.type) {
            existente.data.labels = config.data.labels;
            existente.data.datasets[0].data = config.data.datasets[0].data;
            Object.assign(existente.data.datasets[0], config.data.datasets[0]);
            existente.options = Object.assign(existente.options, config.options);
            existente.update();
            return;
        }
        if (existente) {
            existente.destroy();
        }
        estado.graficos[nome] = new Chart($(idCanvas).getContext('2d'), config);
    }

    function alternarVazio(idPainel, vazio) {
        const painel = $(idPainel);
        const aviso = painel.querySelector('.vazio');
        const grafico = painel.querySelector('.grafico');
        const tabela = painel.querySelector('.tabela-alternativa');
        const mostrandoTabela = tabela && !tabela.hidden;
        if (aviso) {
            aviso.hidden = !vazio;
        }
        if (grafico) {
            grafico.hidden = vazio || mostrandoTabela;
        }
    }

    function tabelaAlternativa(idTabela, cabecalho, linhas) {
        const destino = $(idTabela);
        if (!linhas.length) {
            destino.innerHTML = '<p class="text-muted small mb-0">Sem dados no período.</p>';
            return;
        }
        destino.innerHTML = '<table class="table table-sm mb-0"><thead><tr>'
            + cabecalho.map((c, i) => `<th scope="col" class="${i ? 'text-end' : ''}">${escapar(c)}</th>`).join('')
            + '</tr></thead><tbody>'
            + linhas.map((l) => '<tr>' + l.map((c, i) => `<td class="${i ? 'text-end' : ''}">${escapar(c)}</td>`)
                .join('') + '</tr>').join('')
            + '</tbody></table>';
    }

    // ===================== atualização automática (RF07) =====================
    function programarAtualizacao() {
        if (estado.temporizador) {
            clearInterval(estado.temporizador);
            estado.temporizador = null;
        }
        const segundos = Number($('dash-auto').value);
        if (segundos > 0) {
            estado.temporizador = setInterval(() => {
                if (!document.hidden) {
                    carregar();
                }
            }, segundos * 1000);
        }
    }

    document.addEventListener('visibilitychange', () => {
        const segundos = Number($('dash-auto').value);
        if (!document.hidden && segundos > 0 && estado.ultimaAtualizacao
            && (Date.now() - estado.ultimaAtualizacao.getTime()) > segundos * 1000) {
            carregar();
        }
    });

    // ===================== eventos =====================
    function ligarEventos() {
        $('dash-atualizar').addEventListener('click', () => carregar());
        $('dash-auto').addEventListener('change', programarAtualizacao);

        document.querySelectorAll('#dash-presets [data-preset]').forEach((botao) => {
            botao.addEventListener('click', () => {
                aplicarPreset(botao.dataset.preset);
                estado.pedidosPagina = 0;
                carregar();
            });
        });
        [campos.inicio, campos.fim].forEach((campo) => campo.addEventListener('change', () => {
            destacarPreset();
            estado.pedidosPagina = 0;
            carregar();
        }));
        campos.tipo.addEventListener('change', () => {
            estado.pedidosPagina = 0;
            carregar();
        });
        campos.atendente.addEventListener('change', () => {
            estado.pedidosPagina = 0;
            carregar(['resumo', 'serie', 'distribuicao', 'situacao', 'ranking', 'pedidos']);
        });
        $('dash-limpar').addEventListener('click', () => {
            campos.tipo.value = '';
            campos.atendente.value = '';
            campos.inicio.value = estado.opcoes.periodoPadrao.dataInicio;
            campos.fim.value = estado.opcoes.periodoPadrao.dataFim;
            destacarPreset();
            estado.pedidosPagina = 0;
            carregar();
        });

        document.querySelectorAll('#serie-medida [data-medida]').forEach((botao) => {
            botao.addEventListener('click', () => {
                document.querySelectorAll('#serie-medida [data-medida]').forEach((b) => b.classList.remove('active'));
                botao.classList.add('active');
                estado.serieMedida = botao.dataset.medida;
                if (estado.dados.serie) {
                    desenharSerie(estado.dados.serie); // só troca a medida: não precisa buscar de novo
                }
            });
        });
        $('serie-agrupamento').addEventListener('change', () => carregar(['serie']));
        $('distribuicao-dimensao').addEventListener('change', () => carregar(['distribuicao']));
        ['ranking-ordem', 'ranking-categoria', 'ranking-limite'].forEach((id) =>
            $(id).addEventListener('change', () => carregar(['ranking'])));
        $('fila-limite').addEventListener('change', () => carregar(['fila']));
        $('pedidos-status').addEventListener('change', () => {
            estado.pedidosPagina = 0;
            carregar(['pedidos']);
        });
        $('pedidos-anterior').addEventListener('click', () => {
            estado.pedidosPagina = Math.max(0, estado.pedidosPagina - 1);
            carregar(['pedidos']);
        });
        $('pedidos-proxima').addEventListener('click', () => {
            estado.pedidosPagina += 1;
            carregar(['pedidos']);
        });

        // Alterna gráfico <-> tabela (acessibilidade: o valor nunca depende só do tooltip)
        document.querySelectorAll('[data-alternar-tabela]').forEach((botao) => {
            botao.setAttribute('aria-pressed', 'false');
            botao.addEventListener('click', () => {
                const nome = botao.dataset.alternarTabela;
                const tabela = $('tabela-' + nome);
                const painel = tabela.closest('.painel');
                const mostrar = tabela.hidden;
                tabela.hidden = !mostrar;
                const vazio = painel.querySelector('.vazio');
                painel.querySelector('.grafico').hidden = mostrar || (vazio && !vazio.hidden);
                botao.textContent = mostrar ? 'Gráfico' : 'Tabela';
                botao.setAttribute('aria-pressed', mostrar ? 'true' : 'false');
            });
        });
    }

    // ===================== início =====================
    async function iniciar() {
        if (typeof Chart === 'undefined') {
            mostrarAlerta('A biblioteca de gráficos não foi carregada. Os números continuam disponíveis nas tabelas.',
                'warning');
        } else {
            Chart.defaults.global.defaultFontFamily =
                'system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';
            Chart.defaults.global.defaultFontColor = COR.texto;
            Chart.defaults.global.tooltips.backgroundColor = 'rgba(33, 37, 41, 0.92)';
            Chart.defaults.global.tooltips.xPadding = 10;
            Chart.defaults.global.tooltips.yPadding = 8;
            Chart.defaults.global.tooltips.displayColors = false;
        }
        ligarEventos();
        try {
            aplicarOpcoes(await api('/filtros', {}), lerDaUrl());
        } catch (erro) {
            tratarErros([erro instanceof ErroApi ? erro : new ErroApi(-1, 'Não foi possível carregar os filtros.')]);
            return;
        }
        await carregar();
        programarAtualizacao();
    }

    iniciar();
})();
