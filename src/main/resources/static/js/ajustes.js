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

// ---------- loja, carnê e impressão (tabela config) ----------
async function carregarConfig() {
  const c = await (await fetch('/api/config')).json();
  $('cfg-nome').value = c.nome || '';
  $('cfg-endereco').value = c.endereco || '';
  $('cfg-telefone').value = c.telefone || '';
  $('cfg-venc-dias').value = c.carneVencDias || '30';
  $('cfg-parcelas').value = c.carneParcelas || '1';
  $('cfg-largura').value = c.impLarguraMm || '80';
  $('cfg-rodape').value = c.impRodape || '';
}

$('form-config').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const resp = await fetch('/api/config', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        nome: $('cfg-nome').value, endereco: $('cfg-endereco').value,
        telefone: $('cfg-telefone').value,
        carneVencDias: $('cfg-venc-dias').value, carneParcelas: $('cfg-parcelas').value,
        impLarguraMm: $('cfg-largura').value, impRodape: $('cfg-rodape').value,
      }),
    });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      throw new Error(erro.erro || 'Erro ao salvar');
    }
    toast('Ajustes salvos', 'ok');
  } catch (erro) { toast(erro.message); }
});

// ---------- operadores (logins) ----------
async function carregarUsuarios() {
  const usuarios = await (await fetch('/api/usuarios')).json();
  $('qtd-usuarios').textContent = usuarios.length;
  $('lista-usuarios').innerHTML = usuarios.length
    ? usuarios.map((u) => `<div class="linha">
        <span class="mini-avatar" style="background:${corAvatar(u.nome)}">${iniciais(u.nome)}</span>
        <span style="flex:1">${u.nome}</span>
        <span class="mono">${u.login}</span>
        <button type="button" class="u-acao" data-acao="senha" data-id="${u.id}" data-nome="${u.nome}"
                style="margin-left:8px; padding:3px 8px; font-size:11px; border:1px solid var(--border); border-radius:6px; background:transparent; cursor:pointer">Trocar senha</button>
        <button type="button" class="u-acao" data-acao="desativar" data-id="${u.id}" data-nome="${u.nome}"
                style="padding:3px 8px; font-size:11px; border:1px solid var(--border); border-radius:6px; background:transparent; color:var(--destructive, #d4183d); cursor:pointer">Desativar</button>
      </div>`).join('')
    : '<div class="vazio-lista">Nenhum registro</div>';
}

$('lista-usuarios').addEventListener('click', async (e) => {
  const btn = e.target.closest('.u-acao');
  if (!btn) return;
  const { acao, id, nome } = btn.dataset;

  if (acao === 'desativar') {
    if (!confirm(`Desativar o operador "${nome}"? Ele não conseguirá mais entrar no sistema.`)) return;
    const resp = await fetch(`/api/usuarios/${id}/desativar`, { method: 'POST' });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || 'Não foi possível desativar');
      return;
    }
    toast(`Operador ${nome} desativado`, 'ok');
    carregarUsuarios();
    return;
  }

  if (acao === 'senha') {
    const senhaAtual = prompt(`Trocar a senha de ${nome}.\n\nSenha ATUAL:`);
    if (senhaAtual === null) return;
    const senhaNova = prompt('Senha NOVA:');
    if (senhaNova === null || !senhaNova.trim()) return;
    try {
      await enviar(`/api/usuarios/${id}/senha`, { senhaAtual, senhaNova });
      toast(`Senha de ${nome} trocada`, 'ok');
    } catch (erro) { toast(erro.message); }
  }
});

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
    ? vendedores.map((v) => `<div class="linha">
        <span class="mini-avatar" style="background:${corAvatar(v.nome)}">${iniciais(v.nome)}</span>
        <span class="flex-1" style="flex:1">${v.nome}</span>
        <span class="mono">${v.cpf || ''}</span>
        <button type="button" class="u-acao" data-acao="editar" data-id="${v.id}" data-nome="${v.nome}" data-cpf="${v.cpf || ''}"
                style="margin-left:8px; padding:3px 8px; font-size:11px; border:1px solid var(--border); border-radius:6px; background:transparent; cursor:pointer">Editar</button>
        <button type="button" class="u-acao" data-acao="desativar" data-id="${v.id}" data-nome="${v.nome}"
                style="padding:3px 8px; font-size:11px; border:1px solid var(--border); border-radius:6px; background:transparent; color:var(--destructive, #d4183d); cursor:pointer">Remover</button>
      </div>`).join('')
    : '<div class="vazio-lista">Nenhum registro</div>';
}

$('lista-vendedores').addEventListener('click', async (e) => {
  const btn = e.target.closest('.u-acao');
  if (!btn) return;
  const { acao, id, nome, cpf } = btn.dataset;

  if (acao === 'desativar') {
    if (!confirm(`Remover o vendedor "${nome}"? Vendas antigas continuam com ele, mas não aparece mais pra escolher.`)) return;
    const resp = await fetch(`/api/vendedores/${id}/desativar`, { method: 'POST' });
    if (!resp.ok) {
      const erro = await resp.json().catch(() => ({}));
      toast(erro.erro || 'Não foi possível remover');
      return;
    }
    toast(`Vendedor ${nome} removido`, 'ok');
    carregarVendedores();
    return;
  }

  if (acao === 'editar') {
    const novoNome = prompt('Nome do vendedor:', nome);
    if (novoNome === null || !novoNome.trim()) return;
    const novoCpf = prompt('CPF (opcional):', cpf || '');
    if (novoCpf === null) return;
    try {
      const resp = await fetch(`/api/vendedores/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nome: novoNome.trim(), cpf: novoCpf.trim() || null }),
      });
      if (!resp.ok) {
        const erro = await resp.json().catch(() => ({}));
        throw new Error(erro.erro || 'Erro ao salvar');
      }
      toast('Vendedor atualizado', 'ok');
      carregarVendedores();
    } catch (erro) { toast(erro.message); }
  }
});

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

carregarConfig();
carregarUsuarios();
carregarVendedores();
carregarMarcas();
