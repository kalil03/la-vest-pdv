/** Cadastro mínimo de produtos com variações (grade tamanho/cor). */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

const $form = document.getElementById('form-produto');
const $temGrade = document.getElementById('tem-grade');
const $semGrade = document.getElementById('sem-grade');
const $comGrade = document.getElementById('com-grade');
const $variacoes = document.getElementById('variacoes');
const $lista = document.getElementById('lista');
const $filtro = document.getElementById('filtro');
const $toast = document.getElementById('toast');

$temGrade.addEventListener('change', () => {
  $semGrade.hidden = $temGrade.checked;
  $comGrade.hidden = !$temGrade.checked;
  if ($temGrade.checked && $variacoes.children.length === 0) adicionarLinhaVariacao();
});

function adicionarLinhaVariacao(tamanho = '', cor = '', estoque = 0) {
  const row = document.createElement('div');
  row.className = 'variacao-row';
  row.innerHTML = `
    <div><label>Tamanho</label><input class="v-tamanho" type="text" value="${tamanho}"></div>
    <div><label>Cor</label><input class="v-cor" type="text" value="${cor}"></div>
    <div><label>Estoque</label><input class="v-estoque" type="number" min="0" value="${estoque}"></div>
    <button type="button" class="remover" title="Remover">×</button>`;
  row.querySelector('.remover').addEventListener('click', () => row.remove());
  $variacoes.appendChild(row);
}

document.getElementById('add-variacao').addEventListener('click', () => adicionarLinhaVariacao());

// Atalho para calçados: gera as linhas 34 a 44 de uma vez
document.getElementById('grade-calcados').addEventListener('click', () => {
  for (let t = 34; t <= 44; t++) adicionarLinhaVariacao(String(t), '', 0);
});

$form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const body = {
    codigo: document.getElementById('codigo').value.trim() || null,
    nome: document.getElementById('nome').value.trim(),
    categoria: document.getElementById('categoria').value.trim() || null,
    preco: document.getElementById('preco').value,
  };

  if ($temGrade.checked) {
    body.variacoes = [...$variacoes.querySelectorAll('.variacao-row')].map((row) => ({
      tamanho: row.querySelector('.v-tamanho').value.trim() || null,
      cor: row.querySelector('.v-cor').value.trim() || null,
      estoque: parseInt(row.querySelector('.v-estoque').value, 10) || 0,
    }));
    if (body.variacoes.length === 0) { toast('Adicione ao menos uma variação'); return; }
  } else {
    // Produto sem grade: uma única variação "padrão" (tamanho/cor nulos)
    body.variacoes = [{ tamanho: null, cor: null, estoque: parseInt(document.getElementById('estoque-unico').value, 10) || 0 }];
  }

  const resp = await fetch('/api/produtos', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    toast(erro.erro || 'Erro ao salvar produto');
    return;
  }
  const produto = await resp.json();
  toast(`Produto salvo — código ${produto.codigo}`, 'ok');
  $form.reset();
  $variacoes.innerHTML = '';
  $semGrade.hidden = false;
  $comGrade.hidden = true;
  carregarLista();
  document.getElementById('codigo').focus();
});

async function carregarLista() {
  const q = $filtro.value.trim();
  const produtos = await (await fetch(`/api/produtos?q=${encodeURIComponent(q)}`)).json();
  $lista.innerHTML = '';
  produtos.forEach((p) => {
    const tr = document.createElement('tr');
    const estoques = p.variacoes.map((v) => {
      const rotulo = [v.tamanho, v.cor].filter(Boolean).join(' ') || 'padrão';
      const neg = v.estoque < 0 ? ' neg' : '';
      return `<span class="grade-chip${neg}">${rotulo}: ${v.estoque}</span>`;
    }).join('');
    tr.innerHTML = `
      <td>${p.codigo}</td>
      <td>${p.nome}</td>
      <td>${p.categoria ?? ''}</td>
      <td>${fmt(p.preco)}</td>
      <td>${estoques}</td>`;
    $lista.appendChild(tr);
  });
}

let filtroTimer = null;
$filtro.addEventListener('input', () => {
  clearTimeout(filtroTimer);
  filtroTimer = setTimeout(carregarLista, 200);
});

let toastTimer = null;
function toast(msg, tipo = '') {
  $toast.textContent = msg;
  $toast.className = `toast ${tipo}`;
  $toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $toast.hidden = true; }, 4000);
}

carregarLista();
