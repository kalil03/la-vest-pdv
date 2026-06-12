/** Cadastro/edição de clientes (dados p/ NFC-e e cobrança) e de vendedores. */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);

const $form = $('form-cliente');
const $lista = $('lista');
const $toast = $('toast');

const CAMPOS = ['nome', 'cpf', 'telefone', 'email', 'logradouro', 'numero', 'bairro', 'cidade', 'uf', 'cep', 'tipo', 'rg', 'dataNasc', 'limiteCred', 'bloqueado', 'pfisProfissao', 'pfisRendaConj', 'anotacoes'];

// ---------- salvar (cria ou edita) ----------
$form.addEventListener('submit', async (e) => {
  e.preventDefault();

  const id = $('cliente-id').value;
  const body = {};
  CAMPOS.forEach((c) => { body[c] = $(c).value.trim() || null; });

  const resp = await fetch(id ? `/api/clientes/${id}` : '/api/clientes', {
    method: id ? 'PUT' : 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    toast(erro.erro || 'Erro ao salvar cliente');
    return;
  }
  toast(id ? 'Cliente atualizado' : 'Cliente cadastrado', 'ok');
  limparFormulario();
  carregarLista();
});

function limparFormulario() {
  $form.reset();
  $('cliente-id').value = '';
  $('form-titulo').textContent = 'Novo cliente';
  $('salvar').textContent = 'Salvar cliente';
  $('cancelar-edicao').hidden = true;
  $('nome').focus();
}

$('cancelar-edicao').addEventListener('click', limparFormulario);

function editarCliente(c) {
  $('cliente-id').value = c.id;
  CAMPOS.forEach((campo) => { 
    let val = c[campo] ?? '';
    if ($(campo) && $(campo).type === 'date' && val) val = val.split('T')[0];
    if ($(campo)) $(campo).value = val; 
  });
  $('form-titulo').textContent = `Editando: ${c.nome}`;
  $('salvar').textContent = 'Salvar alterações';
  $('cancelar-edicao').hidden = false;
  window.scrollTo({ top: 0, behavior: 'smooth' });
  $('nome').focus();
}

// ---------- listagem com filtro ----------
async function carregarLista() {
  const q = $('f-texto').value.trim();
  const clientes = await (await fetch(`/api/clientes?q=${encodeURIComponent(q)}`)).json();
  $lista.innerHTML = '';
  clientes.forEach((c) => {
    const deve = Number(c.saldoDevedor) > 0;
    const tr = document.createElement('tr');
    tr.className = 'cursor-pointer hover:bg-muted transition-colors';
    tr.innerHTML = `
      <td>${c.nome}</td>
      <td>${c.cpf ?? '<small class="text-muted-foreground">falta</small>'}</td>
      <td>${c.cidade ?? ''}</td>
      <td class="num text-right ${deve ? 'neg' : ''}">${deve ? fmt(c.saldoDevedor) : '—'}</td>`;
    tr.addEventListener('click', () => editarCliente(c));
    $lista.appendChild(tr);
  });
}

let filtroTimer = null;
$('f-texto').addEventListener('input', () => {
  clearTimeout(filtroTimer);
  filtroTimer = setTimeout(carregarLista, 200);
});

$('f-limpar').addEventListener('click', () => {
  $('f-texto').value = '';
  carregarLista();
});

// ---------- vendedores ----------
$('form-vendedor').addEventListener('submit', async (e) => {
  e.preventDefault();
  const resp = await fetch('/api/vendedores', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ nome: $('v-nome').value.trim(), cpf: $('v-cpf').value.trim() || null }),
  });
  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    toast(erro.erro || 'Erro ao salvar vendedor');
    return;
  }
  $('v-nome').value = '';
  $('v-cpf').value = '';
  toast('Vendedor adicionado', 'ok');
  carregarVendedores();
});

async function carregarVendedores() {
  const vendedores = await (await fetch('/api/vendedores')).json();
  $('lista-vendedores').innerHTML = vendedores
    .map((v) => `<tr><td>${v.nome}</td><td>${v.cpf ?? ''}</td></tr>`)
    .join('');
}

let toastTimer = null;
function toast(msg, tipo = '') {
  $toast.textContent = msg;
  $toast.className = `toast ${tipo}`;
  $toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $toast.hidden = true; }, 4000);
}

carregarLista();
carregarVendedores();

window.novoCadastro = () => {
  limparFormulario();
  document.getElementById('detalhe-produto').hidden = false;
  $('nome').focus();
};
