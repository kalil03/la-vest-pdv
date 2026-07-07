/**
 * Notinha/promissória para bobina térmica de 80mm.
 * Renderizada num iframe oculto e impressa via window.print() — sem popup,
 * sem biblioteca ESC/POS. A área útil de impressão fica em ~72mm.
 */

function reciboHTML(venda, loja) {
  const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const dataHora = new Date(venda.data).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  const dataBr = (iso) => {
    const [a, m, d] = iso.split('-');
    return `${d}/${m}/${a}`;
  };

  const linhasItens = venda.itens.map((i) => `
    <tr>
      <td colspan="3" class="desc">${esc(i.descricao)}</td>
    </tr>
    <tr>
      <td>${i.quantidade} x ${fmt(i.precoUnit)}</td>
      <td></td>
      <td class="dir">${fmt(i.subtotal)}</td>
    </tr>`).join('');

  const temDesconto = Number(venda.desconto) > 0;
  const linhasTotais = `
    ${temDesconto ? `
    <tr><td>Subtotal</td><td></td><td class="dir">${fmt(venda.subtotal)}</td></tr>
    <tr><td>Desconto</td><td></td><td class="dir">-${fmt(venda.desconto)}</td></tr>` : ''}
    <tr class="total">
      <td>TOTAL</td><td></td><td class="dir">${fmt(venda.total)}</td>
    </tr>`;

  const fiado = venda.formaPagamento === 'FIADO';

  // nota que já recebeu pagamento: a reimpressão vira a VIA DA LOJA atualizada,
  // com o que resta por parcela — na venda original (nada pago) nada disso aparece
  const temPagamento = fiado && venda.parcelas.some(
    (p) => p.valorAberto != null && Number(p.valorAberto) < Number(p.valor));

  const linhasParcelas = fiado && venda.parcelas.length
    ? venda.parcelas.map((p) => {
        const situacao = temPagamento
          ? (Number(p.valorAberto) === 0 ? ' — PAGA' : ` — resta ${fmt(p.valorAberto)}`)
          : '';
        return `<tr><td>${p.numero}ª parcela</td><td>${dataBr(p.vencimento)}</td><td class="dir">${fmt(p.valor)}${situacao}</td></tr>`;
      }).join('')
    : '';

  const pagoParcelas = fiado
    ? venda.parcelas.reduce((s, p) => s + (Number(p.valor) - Number(p.valorAberto ?? p.valor)), 0)
    : 0;
  const restaNota = fiado
    ? venda.parcelas.reduce((s, p) => s + Number(p.valorAberto ?? p.valor), 0)
    : 0;
  const blocoAtualizada = temPagamento ? `
    <div class="sep"></div>
    <div class="centro negrito">VIA DA LOJA — ATUALIZADA EM ${new Date().toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' })}</div>
    <table>
      <tr><td>Pago até hoje (parcelas)</td><td></td><td class="dir">${fmt(pagoParcelas)}</td></tr>
      <tr class="total"><td>RESTA DESTA NOTA</td><td></td><td class="dir">${fmt(restaNota)}</td></tr>
    </table>` : '';

  const blocoFiado = fiado ? `
    <div class="sep"></div>
    <div class="centro negrito">PROMISSÓRIA</div>
    <p class="texto">
      Reconheço dever a importância de <b>${fmt(venda.total)}</b>
      referente às mercadorias acima descritas.
    </p>
    ${venda.observacao ? `<p class="texto">Obs.: <b>${esc(venda.observacao)}</b></p>` : ''}
    ${venda.entrada ? `<p class="texto">Entrada: <b>${fmt(venda.entrada)}</b></p>` : ''}
    ${linhasParcelas ? `<table>${linhasParcelas}</table>` : ''}
    ${blocoAtualizada}
    <div class="assinatura">
      <div class="linha-assinatura"></div>
      <div class="centro">${esc(venda.clienteNome || '')}</div>
    </div>` : '';

  const pagamento = rotuloForma(venda.formaPagamento) +
    (venda.formaPagamento === 'CARTAO' && venda.parcelasCartao > 1 ? ` ${venda.parcelasCartao}x` : '');

  const larguraMm = Math.max(40, parseInt(loja?.impLarguraMm, 10) || 80);

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  body {
    width: ${larguraMm - 8}mm;
    margin: 0 auto;
    font-family: 'Courier New', monospace;
    font-size: 12px;
    color: #000;
  }
  .centro { text-align: center; }
  .negrito { font-weight: bold; }
  .dir { text-align: right; }
  h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
  .info { text-align: center; font-size: 11px; margin: 0; }
  .sep { border-top: 1px dashed #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; }
  .desc { font-weight: bold; }
  .total td { font-size: 15px; font-weight: bold; padding-top: 4px; }
  .texto { font-size: 11px; margin: 6px 0; }
  .assinatura { margin-top: 36px; }
  .linha-assinatura { border-top: 1px solid #000; margin: 0 8px 2px; }
  .rodape { text-align: center; font-size: 10px; margin-top: 8px; }
</style>
</head>
<body>
  <h1>${esc(loja.nome)}</h1>
  <p class="info">${esc(loja.endereco)}</p>
  <p class="info">${esc(loja.telefone)}</p>
  <div class="sep"></div>
  <p class="info">Venda nº ${venda.id} — ${dataHora}</p>
  ${venda.clienteNome ? `<p class="info">Cliente: ${esc(venda.clienteNome)}</p>` : ''}
  ${venda.vendedorNome ? `<p class="info">Vendedor(a): ${esc(venda.vendedorNome)}</p>` : ''}
  <div class="sep"></div>
  <table>${linhasItens}${linhasTotais}</table>
  <div class="sep"></div>
  <p class="info">Pagamento: ${pagamento}</p>
  ${blocoFiado}
  <div class="rodape">${esc(loja?.impRodape ?? 'Obrigado pela preferência!')}</div>
</body>
</html>`;
}

function rotuloForma(f) {
  return { DINHEIRO: 'Dinheiro', PIX: 'PIX', CARTAO: 'Cartão', FIADO: 'Fiado (a prazo)' }[f] || f;
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"]/g, (c) =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

function imprimirRecibo(venda, loja) {
  return previewImprimir(reciboHTML(venda, loja));
}

/**
 * Preview do cupom antes do papel: no kiosk a impressão sai DIRETO
 * (--kiosk-printing), então este modal é a única chance de conferir.
 * Mostra exatamente o HTML que vai para a térmica; "Imprimir" manda,
 * "Cancelar" fecha SEM imprimir (o registro já foi gravado antes).
 * Autocontido (estilos inline) para servir qualquer tela.
 */
function previewImprimir(html) {
  return new Promise((resolve) => {
  document.getElementById('preview-cupom')?.remove();
  const overlay = document.createElement('div');
  overlay.id = 'preview-cupom';
  overlay.style.cssText = 'position:fixed;inset:0;z-index:1000;background:rgba(0,0,0,.55);display:flex;align-items:center;justify-content:center;';
  overlay.innerHTML = `
    <div style="background:#fff;color:#111;border-radius:14px;box-shadow:0 20px 60px rgba(0,0,0,.35);width:420px;max-width:94vw;max-height:92vh;display:flex;flex-direction:column;overflow:hidden;font-family:Inter,system-ui,sans-serif">
      <div style="padding:12px 16px;border-bottom:1px solid #e5e5ea;font-weight:700;font-size:15px">Confira antes de imprimir</div>
      <div style="flex:1;overflow:auto;background:#9a9aa0;padding:14px;display:flex;justify-content:center">
        <iframe style="width:330px;height:460px;border:0;background:#fff;box-shadow:0 2px 12px rgba(0,0,0,.35);flex-shrink:0"></iframe>
      </div>
      <div style="padding:10px 16px;font-size:12px;color:#6b6b70;border-top:1px solid #e5e5ea">
        Já está gravado no sistema — "Cancelar" cancela só a IMPRESSÃO, não a venda/recebimento.
      </div>
      <div style="display:flex;gap:10px;padding:0 16px 14px">
        <button data-acao="cancelar" style="flex:1;padding:13px;border-radius:10px;border:1px solid #d4d4d8;background:#fff;color:#111;font-weight:600;font-size:14px;cursor:pointer">Cancelar impressão (Esc)</button>
        <button data-acao="imprimir" style="flex:1.4;padding:13px;border-radius:10px;border:0;background:#030213;color:#fff;font-weight:700;font-size:14px;cursor:pointer">Imprimir (Enter)</button>
      </div>
    </div>`;
  document.body.appendChild(overlay);

  const frame = overlay.querySelector('iframe');
  frame.srcdoc = html;
  frame.addEventListener('load', () => {
    // altura do papel de verdade, até o limite do modal
    try { frame.style.height = Math.max(220, frame.contentDocument.body.scrollHeight + 30) + 'px'; } catch (e) { /* cross-origin não acontece com srcdoc */ }
  });

  const fechar = () => {
    document.removeEventListener('keydown', teclas, true);
    overlay.remove();
    resolve();
  };
  const imprimir = () => { fechar(); imprimirHTML(html); };
  const teclas = (e) => {
    // captura: enquanto o preview está aberto, os atalhos da página (F10 etc.) não valem
    if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); fechar(); }
    else if (e.key === 'Enter' || e.key === 'F10') { e.preventDefault(); e.stopPropagation(); imprimir(); }
  };
  document.addEventListener('keydown', teclas, true);
  overlay.querySelector('[data-acao="cancelar"]').addEventListener('click', fechar);
  overlay.querySelector('[data-acao="imprimir"]').addEventListener('click', imprimir);
  overlay.querySelector('[data-acao="imprimir"]').focus();
  });
}

/** Impressão genérica via iframe oculto (sem popup). */
function imprimirHTML(html) {
  const iframe = document.createElement('iframe');
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = 'none';
  iframe.srcdoc = html;
  iframe.onload = () => {
    iframe.contentWindow.focus();
    iframe.contentWindow.print();
    setTimeout(() => iframe.remove(), 3000);
  };
  document.body.appendChild(iframe);
}

/** Recibo térmico (80mm) do recebimento de carnê. */
function reciboCarneHTML(r, loja) {
  const fmtR = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const dataHora = new Date(r.data).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  const dataBr = (iso) => { const [a, m, d] = iso.split('-'); return `${d}/${m}/${a}`; };

  // comprovante da(s) nota(s): valor original, pago agora, já pago e o que RESTA —
  // é o papel que o cliente grampeia junto da promissória (pagamento parcial)
  const notas = (r.itens || []).map((p) => {
    const quitada = Number(p.restante) === 0;
    const jaPago = Number(p.valorOriginal) - Number(p.restante);
    return `
    <div class="nota-bloco">
      <div><b>${esc(p.descricao)}</b> — venc. ${dataBr(p.vencimento)}</div>
      <table>
        <tr><td>Valor da nota</td><td class="dir">${fmtR(p.valorOriginal)}</td></tr>
        <tr><td>Pago agora</td><td class="dir">${fmtR(p.valorAplicado)}</td></tr>
        ${jaPago > Number(p.valorAplicado) ? `<tr><td>Pago até hoje</td><td class="dir">${fmtR(jaPago)}</td></tr>` : ''}
        <tr><td><b>${quitada ? 'NOTA QUITADA' : 'Resta desta nota'}</b></td><td class="dir"><b>${quitada ? '—' : fmtR(p.restante)}</b></td></tr>
      </table>
    </div>`;
  }).join('');

  const rotulo = { DINHEIRO: 'Dinheiro', PIX: 'PIX', CARTAO: 'Cartão' }[r.tipo] || r.tipo;

  const larguraMm = Math.max(40, parseInt(loja?.impLarguraMm, 10) || 80);

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 12px; color: #000; }
  .centro { text-align: center; }
  .dir { text-align: right; }
  h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
  .info { text-align: center; font-size: 11px; margin: 0; }
  .sep { border-top: 1px dashed #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 11px; }
  .destaque { font-size: 15px; font-weight: bold; }
  .texto { font-size: 11px; margin: 6px 0; }
  .assinatura { margin-top: 34px; }
  .linha-assinatura { border-top: 1px solid #000; margin: 0 8px 2px; }
  .rodape { text-align: center; font-size: 10px; margin-top: 8px; }
  .nota-bloco { margin: 6px 0; padding-bottom: 4px; border-bottom: 1px dashed #000; }
</style>
</head>
<body>
  <h1>${esc(loja.nome)}</h1>
  <p class="info">${esc(loja.endereco)}</p>
  <p class="info">${esc(loja.telefone)}</p>
  <div class="sep"></div>
  <div class="centro" style="font-weight:bold">RECIBO DE PAGAMENTO — CARNÊ</div>
  <p class="info">Recibo nº ${r.id} — ${dataHora}</p>
  <p class="info">Cliente: ${esc(r.clienteNome)}</p>
  <p class="info">Recebido por: ${esc(r.vendedorNome)}</p>
  <div class="sep"></div>
  ${notas}
  <div class="sep"></div>
  <table>
    <tr><td class="destaque">VALOR RECEBIDO</td><td class="dir destaque">${fmtR(r.valor)}</td></tr>
    <tr><td>Forma de pagamento</td><td class="dir">${rotulo}</td></tr>
    <tr><td>Saldo anterior</td><td class="dir">${fmtR(r.saldoAnterior)}</td></tr>
    <tr><td><b>Saldo restante</b></td><td class="dir"><b>${fmtR(r.saldoRestante)}</b></td></tr>
  </table>
  <div class="assinatura">
    <div class="linha-assinatura"></div>
    <div class="centro">${esc(r.clienteNome)}</div>
  </div>
  <div class="rodape">${esc(loja?.impRodape ?? 'Obrigado pela preferência!')}</div>
</body>
</html>`;
}

function imprimirReciboCarne(recibo, loja) {
  return previewImprimir(reciboCarneHTML(recibo, loja));
}
