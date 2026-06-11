/**
 * Recebimento de carnê.
 * Os pagamentos quitam sempre as parcelas mais antigas primeiro (FIFO) — por
 * isso a seleção é em "prefixo": marcar uma parcela marca todas as anteriores.
 * O status de cada parcela vem calculado do servidor, nada é gravado como flag.
 */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);
const round2 = (v) => Math.round(v * 100) / 100;

// ---------- estado ----------
let cliente = null;     // {id, nome, ...} selecionado
let carne = null;       // resposta de /api/clientes/{id}/carne
let selCount = 0;       // seleção em prefixo: as N primeiras parcelas
let tipo = 'DINHEIRO';
let loja = { nome: 'Loja', endereco: '', telefone: '' };
let recebendo = false;

fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

// ---------- operador (funcionário do caixa) ----------
const $operador = $('operador');

async function carregarOperadores() {
  const vendedores = await (await fetch('/api/vendedores')).json();
  vendedores.forEach((v) => {
    const opt = document.createElement('option');
    opt.value = v.id;
    opt.textContent = v.nome;
    $operador.appendChild(opt);
  });
  const salvo = localStorage.getItem('pdv.operadorId');
  if (salvo && vendedores.some((v) => String(v.id) === salvo)) $operador.value = salvo;
  atualizarOperador();
}

function atualizarOperador() {
  const nome = $operador.selectedOptions[0]?.textContent || '';
  const tem = !!$operador.value;
  localStorage.setItem('pdv.operadorId', $operador.value);
  $('op-avatar').textContent = tem ? iniciais(nome) : '?';
  $('header-operador-nome').textContent = tem ? `Operador: ${nome}` : 'Nenhum operador selecionado';
}

$operador.addEventListener('change', atualizarOperador);

// ---------- helpers visuais ----------
function iniciais(nome) {
  const partes = nome.trim().split(/\s+/);
  return ((partes[0]?.[0] || '') + (partes[1]?.[0] || '')).toUpperCase();
}

function corAvatar(nome) {
  let h = 0;
  for (const c of nome) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `hsl(${h}, 60%, 42%)`;
}

function mascararCpf(cpf) {
  if (!cpf) return 'CPF não informado';
  const d = cpf.replace(/\D/g, '');
  return `***.***.***-${d.slice(-2)}`;
}

const dataBr = (iso) => { const [a, m, d] = iso.split('-'); return `${d}/${m}/${a}`; };

// ---------- autocomplete de cliente ----------
const $busca = $('busca-cliente');
const $resultados = $('busca-resultados');
let resultados = [];
let ativo = -1;
let timerBusca = null;

function fecharDropdown() {
  $resultados.hidden = true;
  resultados = [];
  ativo = -1;
}

function abrirDropdown(lista) {
  resultados = lista;
  ativo = lista.length ? 0 : -1;
  $resultados.innerHTML = '';
  if (!lista.length) {
    $resultados.innerHTML = '<li class="!cursor-default text-muted-foreground">Nenhum cliente encontrado</li>';
    $resultados.hidden = false;
    return;
  }
  lista.forEach((c, i) => {
    const li = document.createElement('li');
    li.className = i === ativo ? 'ativo' : '';
    const deve = Number(c.saldoDevedor) > 0;
    li.innerHTML = `
      <span class="w-8 h-8 rounded-full flex items-center justify-center text-[11px] font-bold text-white flex-shrink-0"
            style="background:${corAvatar(c.nome)}">${iniciais(c.nome)}</span>
      <span class="flex-1 min-w-0">
        <span class="block font-medium truncate">${c.nome}</span>
        <span class="block text-[11px] text-muted-foreground mono">${mascararCpf(c.cpf)}</span>
      </span>
      ${deve ? `<span class="chip atrasada">deve ${fmt(c.saldoDevedor)}</span>` : '<span class="chip prazo">em dia</span>'}`;
    li.style.alignItems = 'center';
    li.addEventListener('mousedown', (e) => { e.preventDefault(); selecionarCliente(c); fecharDropdown(); });
    $resultados.appendChild(li);
  });
  $resultados.hidden = false;
}

$busca.addEventListener('input', () => {
  clearTimeout(timerBusca);
  const q = $busca.value.trim();
  if (q.length < 2) { fecharDropdown(); return; }
  timerBusca = setTimeout(async () => {
    try {
      abrirDropdown(await (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json());
    } catch {
      toast('Sem conexão com o servidor', 'erro');
    }
  }, 180);
});

$busca.addEventListener('keydown', (e) => {
  if ($resultados.hidden) return;
  const marcar = () => [...$resultados.children].forEach((li, i) => {
    li.classList.toggle('ativo', i === ativo);
    if (i === ativo) li.scrollIntoView({ block: 'nearest' });
  });
  if (e.key === 'ArrowDown') { e.preventDefault(); ativo = Math.min(ativo + 1, resultados.length - 1); marcar(); }
  else if (e.key === 'ArrowUp') { e.preventDefault(); ativo = Math.max(ativo - 1, 0); marcar(); }
  else if (e.key === 'Enter') { e.preventDefault(); if (ativo >= 0 && resultados[ativo]) { selecionarCliente(resultados[ativo]); fecharDropdown(); } }
  else if (e.key === 'Escape') fecharDropdown();
});

$busca.addEventListener('blur', () => setTimeout(fecharDropdown, 150));

// ---------- carregar e renderizar o carnê ----------
async function selecionarCliente(c) {
  cliente = c;
  $busca.value = c.nome;
  $('vazio').hidden = true;
  $('conteudo').hidden = true;
  $('skeleton').hidden = false;
  try {
    const resp = await fetch(`/api/clientes/${c.id}/carne`);
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || 'Não foi possível carregar o carnê', 'erro');
      $('skeleton').hidden = true;
      $('vazio').hidden = false;
      return;
    }
    carne = await resp.json();
    selCount = 0;
    renderCarne();
  } catch {
    toast('Sem conexão com o servidor', 'erro');
    $('skeleton').hidden = true;
    $('vazio').hidden = false;
  }
}

function renderCarne() {
  $('skeleton').hidden = true;
  $('conteudo').hidden = false;

  // card do cliente
  const c = carne.cliente;
  $('cli-avatar').textContent = iniciais(c.nome);
  $('cli-avatar').style.background = corAvatar(c.nome);
  $('cli-nome').textContent = c.nome;
  $('cli-cpf').textContent = mascararCpf(c.cpf);
  const $tel = $('cli-tel');
  if (c.telefone) {
    $tel.hidden = false;
    $tel.href = `tel:${c.telefone.replace(/\D/g, '')}`;
    $tel.querySelector('span').textContent = c.telefone;
  } else {
    $tel.hidden = true;
  }

  $('m-saldo').textContent = fmt(carne.saldoDevedor);
  $('m-qtd').textContent = carne.parcelasAbertas;

  const $antiga = $('m-antiga');
  if (carne.vencimentoMaisAntigo) {
    const atraso = carne.parcelas[0]?.diasAtraso || 0;
    $antiga.innerHTML = dataBr(carne.vencimentoMaisAntigo) +
        (atraso > 90 ? `<span class="chip atrasada">${atraso} dias</span>`
         : atraso > 0 ? `<span class="chip hoje">${atraso} dias</span>` : '');
  } else {
    $antiga.innerHTML = '<span class="chip prazo">nada em aberto</span>';
  }

  // últimos pagamentos
  const us = carne.ultimosPagamentos || [];
  $('ultimos').hidden = us.length === 0;
  $('ultimos-lista').innerHTML = us.map((p) => `
    <span class="chip prazo" title="${p.vendedorNome ? 'Recebido por ' + p.vendedorNome : ''}">
      ${new Date(p.data).toLocaleDateString('pt-BR')} · ${fmt(p.valor)} · ${rotuloTipo(p.tipo)}
    </span>`).join('');

  renderParcelas();
  if (window.lucide) lucide.createIcons();
}

function statusChip(p) {
  const parcial = Number(p.valorAberto) < Number(p.valor);
  const extra = parcial ? ` <span class="chip parcial">parcial · resta ${fmt(p.valorAberto)}</span>` : '';
  if (p.diasAtraso > 0) return `<span class="chip atrasada">Atrasada ${p.diasAtraso}d</span>${extra}`;
  const hoje = new Date().toLocaleDateString('sv-SE'); // yyyy-mm-dd local
  if (p.vencimento === hoje) return `<span class="chip hoje">Vence hoje</span>${extra}`;
  return `<span class="chip prazo">No prazo</span>${extra}`;
}

function renderParcelas() {
  const $corpo = $('parcelas');
  $corpo.innerHTML = '';
  carne.parcelas.forEach((p, i) => {
    const tr = document.createElement('tr');
    tr.tabIndex = 0;
    tr.setAttribute('role', 'checkbox');
    tr.setAttribute('aria-checked', i < selCount);
    tr.classList.toggle('sel', i < selCount);
    tr.innerHTML = `
      <td><input type="checkbox" ${i < selCount ? 'checked' : ''} tabindex="-1" class="pointer-events-none accent-current"></td>
      <td>${p.descricao}</td>
      <td class="mono">${dataBr(p.vencimento)}</td>
      <td>${statusChip(p)}</td>
      <td class="num font-semibold">${fmt(p.valorAberto)}</td>`;
    const alternar = () => {
      // seleção em prefixo: clicar marca até aqui; clicar de novo desmarca daqui pra frente
      selCount = (i < selCount) ? i : i + 1;
      renderParcelas();
    };
    tr.addEventListener('click', alternar);
    tr.addEventListener('keydown', (e) => {
      if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); alternar(); }
    });
    $corpo.appendChild(tr);
  });

  const sel = carne.parcelas.slice(0, selCount);
  const total = round2(sel.reduce((s, p) => s + Number(p.valorAberto), 0));
  $('tot-rotulo').textContent = sel.length
    ? `${sel.length} parcela${sel.length > 1 ? 's' : ''} selecionada${sel.length > 1 ? 's' : ''}`
    : 'Nenhuma parcela selecionada';
  $('tot-valor').textContent = fmt(total);

  $('resumo-sel').textContent = sel.length
    ? `${sel.length} parcela(s) · vencimentos de ${dataBr(sel[0].vencimento)} a ${dataBr(sel[sel.length - 1].vencimento)}`
    : 'Selecione parcelas na lista ao lado.';

  $('valor-receber').value = total ? total.toFixed(2) : '';
  atualizarPainel();
}

$('sel-atrasadas').addEventListener('click', () => {
  selCount = carne.parcelas.filter((p) => p.diasAtraso > 0).length;
  renderParcelas();
});

// ---------- painel de recebimento ----------
function valorDigitado() {
  return Math.max(0, parseFloat($('valor-receber').value) || 0);
}

function atualizarPainel() {
  const valor = valorDigitado();
  const totalSel = round2(carne.parcelas.slice(0, selCount)
      .reduce((s, p) => s + Number(p.valorAberto), 0));
  const $aviso = $('aviso-parcial');
  const $erro = $('erro-receber');
  $erro.hidden = true;

  if (valor > 0 && valor > Number(carne.saldoDevedor)) {
    $erro.textContent = `Maior que o saldo devedor (${fmt(carne.saldoDevedor)})`;
    $erro.hidden = false;
    $aviso.hidden = true;
  } else if (valor > 0 && totalSel > 0 && valor < totalSel) {
    $aviso.textContent = `Pagamento parcial — continuam em aberto ${fmt(round2(totalSel - valor))} das parcelas selecionadas.`;
    $aviso.hidden = false;
  } else if (valor > 0 && totalSel > 0 && valor > totalSel) {
    $aviso.textContent = `Valor cobre mais que o selecionado — o excedente quita as próximas parcelas.`;
    $aviso.hidden = false;
  } else {
    $aviso.hidden = true;
  }

  const ok = valor > 0 && valor <= Number(carne.saldoDevedor) && !recebendo;
  $('btn-receber').disabled = !ok;
  $('btn-receber').textContent = valor > 0 ? `Receber ${fmt(valor)}` : 'Receber';
}

$('valor-receber').addEventListener('input', atualizarPainel);

$('formas-receber').addEventListener('click', (e) => {
  const btn = e.target.closest('button');
  if (!btn) return;
  tipo = btn.dataset.tipo;
  [...$('formas-receber').children].forEach((b) => b.classList.toggle('ativa', b === btn));
});

async function receber() {
  if (recebendo || !carne) return;
  const valor = valorDigitado();
  if (valor <= 0) return;
  if (!$operador.value) {
    $('erro-receber').textContent = 'Selecione o operador no canto inferior esquerdo antes de receber.';
    $('erro-receber').hidden = false;
    $operador.focus();
    return;
  }

  recebendo = true;
  const $btn = $('btn-receber');
  const rotuloOriginal = $btn.textContent;
  $btn.disabled = true;
  $btn.innerHTML = '<span class="spinner"></span> Recebendo…';

  try {
    const resp = await fetch('/api/recebimentos', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        clienteId: cliente.id,
        valor: valor.toFixed(2),
        tipo,
        vendedorId: Number($operador.value),
      }),
    });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      $('erro-receber').textContent = erro.erro || 'Não foi possível registrar o recebimento';
      $('erro-receber').hidden = false;
      return;
    }
    const recibo = await resp.json();
    imprimirReciboCarne(recibo, loja);
    toast(`Recebido ${fmt(recibo.valor)} de ${recibo.clienteNome} — saldo agora ${fmt(recibo.saldoRestante)}`, 'ok');
    await selecionarCliente(cliente); // recarrega o carnê já atualizado
  } catch {
    $('erro-receber').textContent = 'Sem conexão com o servidor';
    $('erro-receber').hidden = false;
  } finally {
    recebendo = false;
    $btn.textContent = rotuloOriginal;
    atualizarPainel();
  }
}

$('btn-receber').addEventListener('click', receber);

document.addEventListener('keydown', (e) => {
  if (e.key === 'F10') {
    e.preventDefault();
    if (!$('conteudo').hidden) receber();
  }
});

// ---------- util ----------
function rotuloTipo(t) {
  return { DINHEIRO: 'Dinheiro', PIX: 'Pix', CARTAO: 'Cartão', VALE_CREDITO: 'Vale-crédito' }[t] || t;
}

let toastTimer = null;
function toast(msg, tipoToast = '') {
  const $t = $('toast');
  $t.textContent = msg;
  $t.className = `toast-carne ${tipoToast}`;
  $t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $t.hidden = true; }, 4500);
}

carregarOperadores();
