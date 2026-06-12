/**
 * Guarda de login + tema. Incluir no <head> de toda página (exceto login.html):
 * sem usuário no localStorage, redireciona para o login antes de renderizar.
 * É controle de balcão (quem está usando o caixa), não segurança de banco.
 */
(function () {
  // tema escuro persistido
  if (localStorage.getItem('pdv.tema') === 'escuro') {
    document.documentElement.classList.add('dark');
  }

  let usuario = null;
  try { usuario = JSON.parse(localStorage.getItem('pdv.usuario') || 'null'); } catch (e) { /* corrompido */ }
  if (!usuario || !usuario.id) {
    location.replace('/login.html');
    return;
  }
  window.usuarioLogado = usuario;

  document.addEventListener('DOMContentLoaded', () => {
    const nome = document.getElementById('usuario-nome');
    if (nome) nome.textContent = usuario.nome;
    const avatar = document.getElementById('usuario-avatar');
    if (avatar) {
      const partes = usuario.nome.trim().split(/\s+/);
      avatar.textContent = ((partes[0]?.[0] || '') + (partes[1]?.[0] || '')).toUpperCase();
    }
    const sair = document.getElementById('sair');
    if (sair) {
      sair.addEventListener('click', () => {
        localStorage.removeItem('pdv.usuario');
        location.href = '/login.html';
      });
    }
  });
})();

function alternarTema() {
  const escuro = document.documentElement.classList.toggle('dark');
  localStorage.setItem('pdv.tema', escuro ? 'escuro' : 'claro');
}
