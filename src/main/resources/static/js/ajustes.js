/** Ajustes: tema, operadores (logins), vendedores e marcas. */

const $ = (id) => document.getElementById(id);

// ---------- tema ----------
const $tema = $('tema-switch');
$tema.addEventListener('click', alternarTema);
$tema.addEventListener('keydown', (e) => {
  if (e.key === ' ' || e.key === 'Enter') { e.preventDefault(); alternarTema(); }
});

// ---------- helpers ----------
async function enviar(url, body) {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  if (!resp.ok) {
    const erro = await resp.json().catch(() => ({}));
    throw new Error(erro.erro || 'Erro ao salvar');
  }
  return resp.json();
}

let toastTimer = null;
function toast(msg, tipo = '') {
  const $t = $('toast');
  $t.textContent = msg;
  $t.className = `toast ${tipo}`;
  $t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $t.hidden = true; }, 4000);
}

// ---------- operadores (logins) ----------
async function carregarUsuarios() {
  const usuarios = await (await fetch('/api/usuarios')).json();
  $('lista-usuarios').innerHTML = usuarios.map((u) => `
    <div class="linha"><span class="flex-1">${u.nome}</span><span class="mono">${u.login}</span></div>`).join('');
}

$('form-usuario').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await enviar('/api/usuarios', {
      nome: $('u-nome').value.trim(), login: $('u-login').value.trim(), senha: $('u-senha').value,
    });
    toast('Operador criado', 'ok');
    e.target.reset();
    carregarUsuarios();
  } catch (erro) { toast(erro.message); }
});

// ---------- vendedores ----------
async function carregarVendedores() {
  const vendedores = await (await fetch('/api/vendedores')).json();
  $('lista-vendedores').innerHTML = vendedores.map((v) => `
    <div class="linha"><span class="flex-1">${v.nome}</span><span class="mono">${v.cpf ?? ''}</span></div>`).join('');
}

$('form-vendedor').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await enviar('/api/vendedores', {
      nome: $('v-nome').value.trim(), cpf: $('v-cpf').value.trim() || null,
    });
    toast('Vendedor adicionado', 'ok');
    e.target.reset();
    carregarVendedores();
  } catch (erro) { toast(erro.message); }
});

// ---------- marcas ----------
let marcas = [];

async function carregarMarcas() {
  marcas = await (await fetch('/api/marcas')).json();
  renderMarcas();
}

function renderMarcas() {
  const filtro = $('m-filtro').value.trim().toLowerCase();
  const visiveis = filtro ? marcas.filter((m) => m.nome.toLowerCase().includes(filtro)) : marcas.slice(0, 60);
  $('lista-marcas').innerHTML = visiveis.map((m) => `
    <div class="linha"><span class="flex-1">${m.nome}</span><span class="mono">#${m.id}</span></div>`).join('')
    + (!filtro && marcas.length > 60
        ? `<div class="linha" style="color: var(--muted-foreground)">… e mais ${marcas.length - 60} — use o filtro</div>` : '');
}

$('m-filtro').addEventListener('input', renderMarcas);

$('form-marca').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await enviar('/api/marcas', { nome: $('m-nome').value.trim() });
    toast('Marca criada', 'ok');
    $('m-nome').value = '';
    carregarMarcas();
  } catch (erro) { toast(erro.message); }
});

carregarUsuarios();
carregarVendedores();
carregarMarcas();
