/**
 * Régua de cobrança: clientes com parcelas vencidas, agrupados.
 * Para cada um: abrir WhatsApp com a mensagem pronta, copiar a mensagem,
 * imprimir um lembrete na térmica (80mm) ou abrir o carnê para receber.
 * Só leitura — a dívida continua 100% calculada no backend.
 */

const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const $ = (id) => document.getElementById(id);
const dataBr = (iso) => { const [a, m, d] = iso.split('-'); return `${d}/${m}/${a}`; };
const primeiroNome = (nome) => (nome || '').trim().split(/\s+/)[0] || 'cliente';

let loja = { nome: 'Loja', endereco: '', telefone: '' };
fetch('/api/config').then((r) => r.json()).then((c) => { loja = c; });

let devedores = [];

/** Telefone só com dígitos, com DDI 55 (WhatsApp BR). Vazio se não der pra ligar. */
function telParaWhats(tel) {
  if (!tel) return '';
  let d = String(tel).replace(/\D/g, '');
  if (d.length < 10) return '';          // sem DDD não dá pra montar o link
  if (d.length > 11 && d.startsWith('55')) d = d.slice(2);
  return '55' + d;
}

function mensagem(dev) {
  const venc = dataBr(dev.vencimentoMaisAntigo);
  const parc = dev.parcelasVencidas > 1
    ? `${dev.parcelasVencidas} parcelas vencidas (a mais antiga em ${venc})`
    : `uma parcela vencida em ${venc}`;
  return `Olá, ${primeiroNome(dev.nome)}! Tudo bem? Aqui é da ${loja.nome}. `
    + `Passando para lembrar do seu carnê: consta ${parc}, totalizando ${fmt(dev.totalVencido)}. `
    + `Pode passar na loja ou pagar via PIX quando puder. Qualquer dúvida, estamos à disposição! 😊`;
}

function chipAtraso(dias) {
  // datas corrompidas do legado (ano absurdo) viram um nº de dias gigante
  const txt = dias > 3650 ? '+10 anos' : dias === 1 ? '1 dia' : `${dias} dias`;
  return `<span class="chip ${dias >= 60 ? 'grave' : 'leve'}">${txt}</span>`;
}

async function carregar() {
  const params = new URLSearchParams();
  if ($('f-q').value.trim()) params.set('q', $('f-q').value.trim());
  params.set('ordenar', $('f-ordenar').value);

  const r = await (await fetch(`/api/cobranca?${params}`)).json();
  devedores = r.devedores;

  $('k-devedores').textContent = Number(r.totais.devedores).toLocaleString('pt-BR');
  $('k-vencido').textContent = fmt(r.totais.totalVencido);
  $('k-aberto').textContent = fmt(r.totais.totalAberto);

  $('lista').innerHTML = devedores.map((d, i) => {
    const temWhats = !!telParaWhats(d.telefone);
    return `
    <tr>
      <td class="font-medium">${d.nome}</td>
      <td class="mono text-[12px]">${d.telefone || '<span class="text-muted-foreground">sem telefone</span>'}</td>
      <td>${chipAtraso(d.diasAtraso)}<div class="text-[11px] text-muted-foreground mt-0.5">desde ${dataBr(d.vencimentoMaisAntigo)}</div></td>
      <td class="num">${d.parcelasVencidas}</td>
      <td class="num font-semibold" style="color: var(--bad)">${fmt(d.totalVencido)}</td>
      <td class="num">${fmt(d.totalAberto)}</td>
      <td>
        <div class="flex flex-wrap gap-1.5">
          <button class="acao-btn zap" data-acao="zap" data-i="${i}" ${temWhats ? '' : 'disabled title="Sem telefone com DDD"'}><i data-lucide="message-circle" class="w-3.5 h-3.5"></i> WhatsApp</button>
          <button class="acao-btn" data-acao="copiar" data-i="${i}" title="Copiar a mensagem de cobrança"><i data-lucide="copy" class="w-3.5 h-3.5"></i></button>
          <button class="acao-btn" data-acao="imprimir" data-i="${i}" title="Imprimir lembrete na térmica"><i data-lucide="printer" class="w-3.5 h-3.5"></i></button>
          <button class="acao-btn" data-acao="carne" data-i="${i}" title="Abrir o carnê para receber"><i data-lucide="wallet" class="w-3.5 h-3.5"></i></button>
        </div>
      </td>
    </tr>`;
  }).join('') || '<tr><td colspan="7" class="text-center text-muted-foreground py-10">Nenhum cliente em atraso com esse filtro 🎉</td></tr>';

  $('info').textContent = `${devedores.length.toLocaleString('pt-BR')} cliente(s) na lista`;
  lucide.createIcons();
}

$('lista').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-acao]');
  if (!btn) return;
  const dev = devedores[Number(btn.dataset.i)];
  const acao = btn.dataset.acao;

  if (acao === 'carne') {
    location.href = `/carne.html?cliente=${dev.clienteId}`;
  } else if (acao === 'zap') {
    const fone = telParaWhats(dev.telefone);
    window.open(`https://wa.me/${fone}?text=${encodeURIComponent(mensagem(dev))}`, '_blank');
  } else if (acao === 'copiar') {
    navigator.clipboard.writeText(mensagem(dev))
      .then(() => toast('Mensagem copiada'))
      .catch(() => toast('Não foi possível copiar', 'erro'));
  } else if (acao === 'imprimir') {
    imprimirHTML(lembreteHTML(dev, loja));
    toast('Lembrete enviado para impressão');
  }
});

/** Lembrete de pagamento para a bobina térmica de 80mm. */
function lembreteHTML(dev, loja) {
  const e = (s) => String(s ?? '').replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]));
  const hoje = new Date().toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  return `<!DOCTYPE html>
<html lang="pt-BR"><head><meta charset="UTF-8"><style>
  @page { size: 80mm auto; margin: 0; }
  body { width: 72mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 12px; color: #000; }
  .centro { text-align: center; } .dir { text-align: right; }
  h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
  .info { text-align: center; font-size: 11px; margin: 0; }
  .sep { border-top: 1px dashed #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 12px; }
  .destaque { font-size: 15px; font-weight: bold; }
  .texto { font-size: 11px; margin: 6px 0; }
  .rodape { text-align: center; font-size: 10px; margin-top: 10px; }
</style></head><body>
  <h1>${e(loja.nome)}</h1>
  <p class="info">${e(loja.endereco)}</p>
  <p class="info">${e(loja.telefone)}</p>
  <div class="sep"></div>
  <div class="centro" style="font-weight:bold">LEMBRETE DE PAGAMENTO</div>
  <p class="info">${e(hoje)}</p>
  <div class="sep"></div>
  <p class="texto">Cliente: <b>${e(dev.nome)}</b></p>
  <table>
    <tr><td>Parcelas vencidas</td><td class="dir">${dev.parcelasVencidas}</td></tr>
    <tr><td>Venc. mais antigo</td><td class="dir">${dataBr(dev.vencimentoMaisAntigo)} (${dev.diasAtraso}d)</td></tr>
    <tr><td class="destaque">VALOR VENCIDO</td><td class="dir destaque">${fmt(dev.totalVencido)}</td></tr>
    <tr><td>Total em aberto</td><td class="dir">${fmt(dev.totalAberto)}</td></tr>
  </table>
  <div class="sep"></div>
  <p class="texto centro">Procure a loja para regularizar seu carnê.<br>Obrigado pela preferência!</p>
  <div class="rodape">Documento sem valor fiscal — apenas lembrete</div>
</body></html>`;
}

let timer = null;
$('f-q').addEventListener('input', () => { clearTimeout(timer); timer = setTimeout(carregar, 250); });
$('f-ordenar').addEventListener('change', carregar);

let toastTimer = null;
function toast(msg, tipo = '') {
  const $t = $('toast');
  $t.textContent = msg;
  $t.className = `toast ${tipo}`;
  $t.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { $t.hidden = true; }, 4000);
}

carregar();
