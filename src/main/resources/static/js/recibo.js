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

  const linhasParcelas = fiado && venda.parcelas.length
    ? venda.parcelas.map((p) =>
        `<tr><td>${p.numero}ª parcela</td><td>${dataBr(p.vencimento)}</td><td class="dir">${fmt(p.valor)}</td></tr>`).join('')
    : '';

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
    <p class="texto">Saldo devedor total: <b>${fmt(venda.saldoDevedor)}</b></p>
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
  const iframe = document.createElement('iframe');
  iframe.style.position = 'fixed';
  iframe.style.right = '0';
  iframe.style.bottom = '0';
  iframe.style.width = '0';
  iframe.style.height = '0';
  iframe.style.border = 'none';
  iframe.srcdoc = reciboHTML(venda, loja);
  iframe.onload = () => {
    iframe.contentWindow.focus();
    iframe.contentWindow.print();
    // remove depois que o diálogo de impressão fechar
    setTimeout(() => iframe.remove(), 3000);
  };
  document.body.appendChild(iframe);
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

  const linhas = (r.parcelasQuitadas || []).map((p) => `
    <tr><td>${esc(p.descricao)}</td><td>${dataBr(p.vencimento)}</td><td class="dir">${fmtR(p.valor)}</td></tr>`).join('');

  const parcial = r.parcelaParcial ? `
    <p class="texto">Parcela ${esc(r.parcelaParcial.descricao)} (${dataBr(r.parcelaParcial.vencimento)}):
    pagamento parcial — resta <b>${fmtR(r.parcelaParcial.valorAberto)}</b></p>` : '';

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
  ${linhas ? `<table><tr><td><b>Parcelas quitadas</b></td><td><b>Venc.</b></td><td class="dir"><b>Valor</b></td></tr>${linhas}</table>` : ''}
  ${parcial}
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
  imprimirHTML(reciboCarneHTML(recibo, loja));
}
