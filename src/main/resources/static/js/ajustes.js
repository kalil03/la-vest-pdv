/** Ajustes: tema, operadores (logins), vendedores e marcas. */

const $ = (id) => document.getElementById(id);

// ---------- tema (cartões de pré-visualização) ----------
function marcarTema() {
  const escuro = document.documentElement.classList.contains('dark');
  $('tema-claro').classList.toggle('sel', !escuro);
  $('tema-escuro').classList.toggle('sel', escuro);
}

$('tema-claro').addEventListener('click', () => {
  if (document.documentElement.classList.contains('dark')) alternarTema();
  marcarTema();
});
$('tema-escuro').addEventListener('click', () => {
  if (!document.documentElement.classList.contains('dark')) alternarTema();
  marcarTema();
});
marcarTema();

// ---------- helpers ----------
function iniciais(nome) {
  const p = nome.trim().split(/\s+/);
  return ((p[0]?.[0] || '') + (p[1]?.[0] || '')).toUpperCase();
}

function corAvatar(nome) {
  let h = 0;
  for (const c of nome) h = (h * 31 + c.charCodeAt(0)) % 360;
  return `hsl(${h}, 55%, 45%)`;
}

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

function linhaPessoa(nome, sub) {
  return `<div class="linha">
    <span class="mini-avatar" style="background:${corAvatar(nome)}">${iniciais(nome)}</span>
    <span class="flex-1" style="flex:1">${nome}</span>
    <span class="mono">${sub || ''}</span>
  </div>`;
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
  $('qtd-usuarios').textContent = usuarios.length;
  $('lista-usuarios').innerHTML = usuarios.length
    ? usuarios.map((u) => linhaPessoa(u.nome, u.login)).join('')
    : '<div class="vazio-lista">Nenhum registro</div>';
}

$('form-usuario').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await enviar('/api/usuarios', {
      nome: $('u-nome').value.trim(), login: $('u-login').value.trim(), senha: $('u-senha').value,
    });
    toast('Operador criado', 'ok');
    e.target.reset();
    $('u-nome').focus();
    carregarUsuarios();
  } catch (erro) { toast(erro.message); }
});

// ---------- vendedores ----------
async function carregarVendedores() {
  const vendedores = await (await fetch('/api/vendedores')).json();
  $('qtd-vendedores').textContent = vendedores.length;
  $('lista-vendedores').innerHTML = vendedores.length
    ? vendedores.map((v) => linhaPessoa(v.nome, v.cpf)).join('')
    : '<div class="vazio-lista">Nenhum registro</div>';
}

$('form-vendedor').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await enviar('/api/vendedores', {
      nome: $('v-nome').value.trim(), cpf: $('v-cpf').value.trim() || null,
    });
    toast('Vendedor adicionado', 'ok');
    e.target.reset();
    $('v-nome').focus();
    carregarVendedores();
  } catch (erro) { toast(erro.message); }
});

// ---------- marcas ----------
let marcas = [];

async function carregarMarcas() {
  marcas = await (await fetch('/api/marcas')).json();
  $('qtd-marcas').textContent = marcas.length.toLocaleString('pt-BR');
  renderMarcas();
}

function renderMarcas() {
  const filtro = $('m-filtro').value.trim().toLowerCase();
  const filtradas = filtro ? marcas.filter((m) => m.nome.toLowerCase().includes(filtro)) : marcas;
  const visiveis = filtradas.slice(0, 80);
  $('lista-marcas').innerHTML =
    visiveis.map((m) => `<span class="chip-marca">${m.nome}</span>`).join('')
    + (filtradas.length > 80
        ? `<span class="chip-mais">+${(filtradas.length - 80).toLocaleString('pt-BR')} marcas — refine o filtro</span>`
        : (filtradas.length === 0 ? '<span class="chip-mais">Nenhuma marca encontrada</span>' : ''));
}

$('m-filtro').addEventListener('input', renderMarcas);

$('form-marca').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    await enviar('/api/marcas', { nome: $('m-nome').value.trim() });
    toast('Marca criada', 'ok');
    $('m-filtro').value = $('m-nome').value.trim(); // mostra a recém-criada
    $('m-nome').value = '';
    carregarMarcas();
  } catch (erro) { toast(erro.message); }
});

carregarUsuarios();
carregarVendedores();
carregarMarcas();
