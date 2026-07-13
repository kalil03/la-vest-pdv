/** Cadastro e edição de produtos com grade, marca e dados fiscais. */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);

const $form = $('form-produto');
const $temGrade = $('tem-grade');
const $comGrade = $('com-grade');
const $variacoes = $('variacoes');
const $lista = $('lista');
const $toast = $('toast');

let produtosCache = []; // último resultado da busca, para preencher edição

// ---------- combobox genérico ----------
function initCombobox(inputId, dropdownId, getOptions) {
  const input = $(inputId);
  const dropdown = $(dropdownId);
  let activeIdx = -1;

  function renderOptions(q) {
    const opts = getOptions().filter((o) => !q || o.toLowerCase().includes(q.toLowerCase()));
    if (opts.length === 0) {
      dropdown.innerHTML = '<li class="cb-empty">Nenhum resultado</li>';
    } else {
      dropdown.innerHTML = opts.slice(0, 80).map((o, i) =>
        `<li data-val="${o.replace(/"/g, '&quot;')}" data-idx="${i}">${o}</li>`).join('');
    }
    activeIdx = -1;
    dropdown.querySelectorAll('li[data-val]').forEach((li) => {
      li.addEventListener('mousedown', (e) => {
        e.preventDefault();
        input.value = li.dataset.val;
        dropdown.hidden = true;
      });
    });
  }

  input.addEventListener('focus', () => { renderOptions(input.value); dropdown.hidden = false; });
  input.addEventListener('input', () => { renderOptions(input.value); dropdown.hidden = false; });
  input.addEventListener('blur', () => setTimeout(() => { dropdown.hidden = true; }, 150));
  input.addEventListener('keydown', (e) => {
    const items = [...dropdown.querySelectorAll('li[data-val]')];
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      activeIdx = Math.min(activeIdx + 1, items.length - 1);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      activeIdx = Math.max(activeIdx - 1, 0);
    } else if (e.key === 'Enter' && activeIdx >= 0) {
      e.preventDefault();
      input.value = items[activeIdx].dataset.val;
      dropdown.hidden = true;
      return;
    } else if (e.key === 'Escape') {
      dropdown.hidden = true;
      return;
    }
    items.forEach((li, i) => li.classList.toggle('active', i === activeIdx));
    if (activeIdx >= 0) items[activeIdx].scrollIntoView({ block: 'nearest' });
  });
}

let _marcasOpts = [];
let _categoriasOpts = [];
initCombobox('marca', 'marca-dropdown', () => _marcasOpts);
initCombobox('categoria', 'categoria-dropdown', () => _categoriasOpts);

// ---------- grade ----------
$temGrade.addEventListener('change', () => {
  $comGrade.hidden = !$temGrade.checked;
  if ($temGrade.checked && $variacoes.children.length === 0) adicionarLinhaVariacao();
});

function adicionarLinhaVariacao(tamanho = '', cor = '') {
  const row = document.createElement('div');
  row.className = 'variacao-row flex items-end gap-2 mb-2';
  row.innerHTML = `
    <div class="flex-1"><label class="block text-[10px] font-semibold text-muted-foreground uppercase mb-1">Tamanho</label><input class="v-tamanho w-full bg-background border border-border rounded-md px-2 py-1.5 text-[12px] focus:border-primary outline-none" type="text" value="${tamanho}"></div>
    <div class="flex-1"><label class="block text-[10px] font-semibold text-muted-foreground uppercase mb-1">Cor</label><input class="v-cor w-full bg-background border border-border rounded-md px-2 py-1.5 text-[12px] focus:border-primary outline-none" type="text" value="${cor}"></div>
    <button type="button" class="remover px-2 py-1.5 bg-destructive text-destructive-foreground rounded-md hover:opacity-90 transition-opacity flex items-center justify-center h-[30px]" title="Remover">&times;</button>`;
  row.querySelector('.remover').addEventListener('click', () => row.remove());
  $variacoes.appendChild(row);
}

$('add-variacao').addEventListener('click', () => adicionarLinhaVariacao());

// Atalho para calçados: gera as linhas 34 a 44 de uma vez
$('grade-calcados').addEventListener('click', () => {
  for (let t = 34; t <= 44; t++) adicionarLinhaVariacao(String(t), '');
});

// ---------- salvar (cria ou edita) ----------
$form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const id = $('produto-id').value;
  const body = {
    codigo: $('codigo').value.trim() || null,
    nome: $('nome').value.trim(),
    marcaNome: $('marca').value.trim() || null,
    categoria: $('categoria').value.trim() || null,
    preco: $('preco').value,
    ncm: $('ncm').value.trim() || null,
    cest: $('cest').value.trim() || null,
    unidade: $('unidade').value.trim() || 'UN',
    codigoBarras: $('codigo-barras').value.trim() || null,
    origem: Number($('origem').value),
    pCusto: $('pCusto').value.trim() || null,
    pLucro: $('pLucro').value.trim() || null,
    pAtacado: $('pAtacado').value.trim() || null,
    pLucroAtacado: $('pLucroAtacado').value.trim() || null,
    estoque: $('estoque').value.trim() || null,
    estMinimo: $('estMinimo').value.trim() || null,
    tributado: $('tributado').value
  };

  // Variações só na criação: itens vendidos apontam para elas (edição de grade fica para depois)
  if (!id) {
    body.variacoes = $temGrade.checked
      ? [...$variacoes.querySelectorAll('.variacao-row')].map((row) => ({
          tamanho: row.querySelector('.v-tamanho').value.trim() || null,
          cor: row.querySelector('.v-cor').value.trim() || null,
        }))
      : [];
    if ($temGrade.checked && body.variacoes.length === 0) {
      toast('Adicione ao menos uma variação');
      return;
    }
  }

  const resp = await fetch(id ? `/api/produtos/${id}` : '/api/produtos', {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    toast(erro.erro || 'Erro ao salvar produto');
    return;
  }
  const produto = await resp.json();
  toast(id ? `Produto ${produto.codigo} atualizado` : `Produto salvo — código ${produto.codigo}`, 'ok');
  if (typeof Rascunho !== 'undefined') Rascunho.limpar('produto');
  limparFormulario();
  carregarMarcas();
  carregarCategorias();
  carregarLista();
});

function limparFormulario() {
  $form.reset();
  $('produto-id').value = '';
  $('unidade').value = 'UN';
  $('form-titulo').textContent = 'Novo produto';
  $('salvar').textContent = 'Salvar produto';
  $('cancelar-edicao').hidden = true;
  $variacoes.innerHTML = '';
  $comGrade.hidden = true;
  $temGrade.disabled = false;
  $('codigo').focus();
  document.getElementById('detalhe-produto').hidden = true;
}

$('cancelar-edicao').addEventListener('click', limparFormulario);

function editarProduto(p) {
  $('produto-id').value = p.id;
  $('codigo').value = p.codigo;
  $('nome').value = p.nome;
  $('marca').value = p.marcaNome ?? '';
  $('categoria').value = p.categoria ?? '';
  $('preco').value = Number(p.preco).toFixed(2);
  $('ncm').value = p.ncm ?? '';
  $('cest').value = p.cest ?? '';
  $('unidade').value = p.unidade ?? 'UN';
  $('codigo-barras').value = p.codigoBarras ?? '';
  $('origem').value = p.origem ?? 0;
  $('pCusto').value = p.pCusto ?? '';
  $('pLucro').value = p.pLucro ?? '';
  $('pAtacado').value = p.pAtacado ?? '';
  $('pLucroAtacado').value = p.pLucroAtacado ?? '';
  $('estoque').value = p.estoque ?? '';
  $('estMinimo').value = p.estMinimo ?? '';
  $('tributado').value = p.tributado ?? 'S';
  // grade não é editável por aqui (itens vendidos apontam para as variações)
  $temGrade.checked = false;
  $temGrade.disabled = true;
  $comGrade.hidden = true;
  $('form-titulo').textContent = `Editando: ${p.nome}`;
  $('salvar').textContent = 'Salvar alterações';
  $('cancelar-edicao').hidden = false;
  
  // Exibe o painel de detalhes no layout dual-pane
  document.getElementById('detalhe-produto').hidden = false;
  $('nome').focus();
}

// ---------- filtros e listagem ----------
async function carregarMarcas() {
  const marcas = await (await fetch('/api/marcas')).json();
  _marcasOpts = marcas.map((m) => m.nome);
  // select do filtro da lista
  const sel = $('f-marca');
  const atual = sel.value;
  sel.innerHTML = '<option value="">Todas as marcas</option>' +
    marcas.map((m) => `<option value="${m.id}">${m.nome}</option>`).join('');
  sel.value = atual;
}

async function carregarCategorias() {
  const cats = await (await fetch('/api/produtos/categorias')).json();
  _categoriasOpts = cats;
  // select do filtro da lista
  const sel = $('f-categoria');
  const atual = sel.value;
  sel.innerHTML = '<option value="">Todas as categorias</option>' +
    cats.map((c) => `<option value="${c}">${c}</option>`).join('');
  sel.value = atual;
}

async function carregarLista() {
  const params = new URLSearchParams();
  if ($('f-texto').value.trim()) params.set('q', $('f-texto').value.trim());
  if ($('f-marca').value) params.set('marcaId', $('f-marca').value);
  if ($('f-categoria').value) params.set('categoria', $('f-categoria').value);
  if ($('f-data-de').value) params.set('dataDe', $('f-data-de').value);
  if ($('f-data-ate').value) params.set('dataAte', $('f-data-ate').value);

  produtosCache = await (await fetch(`/api/produtos?${params}`)).json();
  $lista.innerHTML = '';

  if (produtosCache.length === 0) {
    const temFiltro = params.toString() !== '';
    $lista.innerHTML = `
      <tr><td colspan="9" class="px-4 py-14 text-center text-muted-foreground">
        <div class="text-[14px] font-medium">${temFiltro ? 'Nenhum produto com esses filtros' : 'Nenhum produto ainda'}</div>
        <div class="text-[12px] mt-1">${temFiltro ? 'Ajuste a busca ou limpe os filtros.' : 'Clique em "Novo Produto" para cadastrar o primeiro.'}</div>
      </td></tr>`;
  }

  produtosCache.forEach((p) => {
    const variacoes = p.variacoes.filter((v) => !v.padrao)
      .map((v) => [v.tamanho, v.cor].filter(Boolean).join(' ')).join(', ');
    const tr = document.createElement('tr');
    tr.className = 'cursor-pointer hover:bg-muted transition-colors border-b border-border';
    tr.innerHTML = `
      <td class="px-4 py-3 text-[13px] font-mono text-muted-foreground">${p.codigo}</td>
      <td class="px-4 py-3 text-[13px] font-semibold">${p.nome}${variacoes ? ` <small class="grade-chip">${variacoes}</small>` : ''}</td>
      <td class="px-4 py-3 text-[12px]">${p.marcaNome ? `<span class="px-2 py-0.5 rounded-full bg-secondary text-[11px] font-medium">${p.marcaNome}</span>` : ''}</td>
      <td class="px-4 py-3 text-[12px]">${p.categoria ? `<span class="px-2 py-0.5 rounded-full bg-secondary text-[11px] font-medium">${p.categoria}</span>` : ''}</td>
      <td class="px-4 py-3 text-[12px] font-mono text-muted-foreground">${p.ncm || ''}</td>
      <td class="px-4 py-3 text-[13px] font-mono text-right text-muted-foreground">${fmt(p.custo || 0)}</td>
      <td class="px-4 py-3 text-[14px] font-mono text-right text-primary font-bold">${fmt(p.preco || 0)}</td>
      <td class="px-4 py-3 text-[12px] text-muted-foreground">${new Date(p.dataCriacao).toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' })}</td>
      <td class="px-4 py-3 text-right"><button type="button" class="px-2.5 py-1 border border-border rounded-md text-[12px] font-medium bg-background hover:bg-secondary">Editar</button></td>`;
    tr.addEventListener('click', () => editarProduto(p));
    $lista.appendChild(tr);
  });

  const rodape = document.getElementById('lista-rodape');
  if (rodape) {
    rodape.textContent = `${produtosCache.length.toLocaleString('pt-BR')} produto${produtosCache.length === 1 ? '' : 's'}`
      + (produtosCache.length >= 200 ? ' (mostrando os primeiros 200 — refine a busca)' : '');
  }
}

let filtroTimer = null;
['f-texto', 'f-marca', 'f-categoria', 'f-data-de', 'f-data-ate'].forEach((id) => {
  $(id).addEventListener('input', () => {
    clearTimeout(filtroTimer);
    filtroTimer = setTimeout(carregarLista, 200);
  });
});

$('f-limpar').addEventListener('click', () => {
  ['f-texto', 'f-marca', 'f-categoria', 'f-data-de', 'f-data-ate'].forEach((id) => { $(id).value = ''; });
  carregarLista();
});

let toastTimer = null;
function toast(msg, tipo = '') {
  $toast.textContent = msg;
  $toast.className = `toast ${tipo}`;
  $toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $toast.hidden = true; }, 4000);
}

carregarMarcas();
carregarCategorias();
carregarLista();

window.novoCadastro = () => {
  limparFormulario();
  document.getElementById('detalhe-produto').hidden = false;
  $('nome').focus();
};

// ---------- memória de rascunho do cadastro (produto novo volta ao reabrir) ----------
const CAMPOS_PRODUTO = ['codigo', 'nome', 'marca', 'categoria', 'preco', 'ncm', 'cest', 'unidade',
  'codigo-barras', 'origem', 'pCusto', 'pLucro', 'pAtacado', 'pLucroAtacado', 'estoque', 'estMinimo', 'tributado'];

function coletarRascunhoProduto() {
  const f = {};
  CAMPOS_PRODUTO.forEach((id) => { const el = $(id); if (el) f[id] = el.value; });
  f._grade = $temGrade.checked;
  f._variacoes = [...$variacoes.children].map((r) => ({
    tamanho: r.querySelector('.v-tamanho')?.value || '',
    cor: r.querySelector('.v-cor')?.value || '',
  }));
  return f;
}

function temConteudoProduto(f) {
  return !!((f.nome && f.nome.trim()) || (f.codigo && f.codigo.trim()) || (f.preco && String(f.preco).trim()));
}

const _agendaProduto = (typeof Rascunho !== 'undefined')
  ? Rascunho.autoSave('produto', coletarRascunhoProduto, temConteudoProduto)
  : function () {};

// só guarda rascunho de PRODUTO NOVO (a edição é carregada do servidor)
function agendarRascunhoProduto() { if (!$('produto-id').value) _agendaProduto(); }

$form.addEventListener('input', agendarRascunhoProduto);
$form.addEventListener('change', agendarRascunhoProduto);
$('cancelar-edicao').addEventListener('click', () => { if (typeof Rascunho !== 'undefined') Rascunho.limpar('produto'); });

function restaurarRascunhoProduto() {
  if (typeof Rascunho === 'undefined') return;
  const f = Rascunho.carregar('produto');
  if (!f || !temConteudoProduto(f)) return;
  if (typeof window.novoCadastro === 'function') window.novoCadastro(); // abre o painel limpo
  CAMPOS_PRODUTO.forEach((id) => { const el = $(id); if (el && f[id] != null) el.value = f[id]; });
  if (f._grade) {
    $temGrade.checked = true;
    $comGrade.hidden = false;
    $variacoes.innerHTML = '';
    (f._variacoes || []).forEach((v) => adicionarLinhaVariacao(v.tamanho, v.cor));
    if ($variacoes.children.length === 0) adicionarLinhaVariacao();
  }
  Rascunho.salvar('produto', coletarRascunhoProduto()); // mantém o rascunho vivo (preenchi via JS)
  Rascunho.aviso('Rascunho de produto recuperado', () => { Rascunho.limpar('produto'); limparFormulario(); });
}

// roda depois do window.onload do produtos.html (que define novoCadastro/abrirPainel)
window.addEventListener('load', () => setTimeout(restaurarRascunhoProduto, 0));

