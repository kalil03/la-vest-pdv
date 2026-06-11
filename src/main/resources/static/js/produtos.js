/** Cadastro e edição de produtos com grade, marca e dados fiscais (NFC-e). */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);

const $form = $('form-produto');
const $temGrade = $('tem-grade');
const $comGrade = $('com-grade');
const $variacoes = $('variacoes');
const $lista = $('lista');
const $toast = $('toast');

let produtosCache = []; // último resultado da busca, para preencher edição

// ---------- grade ----------
$temGrade.addEventListener('change', () => {
  $comGrade.hidden = !$temGrade.checked;
  if ($temGrade.checked && $variacoes.children.length === 0) adicionarLinhaVariacao();
});

function adicionarLinhaVariacao(tamanho = '', cor = '') {
  const row = document.createElement('div');
  row.className = 'flex items-end gap-2 mb-2';
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
  $('marcas').innerHTML = marcas.map((m) => `<option value="${m.nome}"></option>`).join('');
  const sel = $('f-marca');
  const atual = sel.value;
  sel.innerHTML = '<option value="">Todas as marcas</option>' +
    marcas.map((m) => `<option value="${m.id}">${m.nome}</option>`).join('');
  sel.value = atual;
}

async function carregarCategorias() {
  const cats = await (await fetch('/api/produtos/categorias')).json();
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
  produtosCache.forEach((p) => {
    const variacoes = p.variacoes.filter((v) => !v.padrao)
      .map((v) => [v.tamanho, v.cor].filter(Boolean).join(' ')).join(', ');
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td>${p.codigo}</td>
      <td>${p.nome}${variacoes ? ` <small class="grade-chip">${variacoes}</small>` : ''}</td>
      <td>${p.marcaNome ?? ''}</td>
      <td>${p.categoria ?? ''}</td>
      <td>${p.ncm ?? '<small class="falta">falta</small>'}</td>
      <td>${fmt(p.preco)}</td>
      <td>${new Date(p.dataCriacao).toLocaleDateString('pt-BR')}</td>
      <td><button type="button" class="editar">editar</button></td>`;
    tr.querySelector('.editar').addEventListener('click', () => editarProduto(p));
    $lista.appendChild(tr);
  });
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
