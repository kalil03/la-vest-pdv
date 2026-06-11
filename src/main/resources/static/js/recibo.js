/**
 * Notinha/promissória para bobina térmica de 80mm.
 * Renderizada num iframe oculto e impressa via window.print() — sem popup,
 * sem biblioteca ESC/POS. A área útil de impressão fica em ~72mm.
 */

function reciboHTML(venda, loja) {
  const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const dataHora = new Date(venda.data).toLocaleString('pt-BR');

  const linhasItens = venda.itens.map((i) => `
    <tr>
      <td colspan="3" class="desc">${esc(i.descricao)}</td>
    </tr>
    <tr>
      <td>${i.quantidade} x ${fmt(i.precoUnit)}</td>
      <td></td>
      <td class="dir">${fmt(i.subtotal)}</td>
    </tr>`).join('');

  const fiado = venda.formaPagamento === 'FIADO';

  const blocoFiado = fiado ? `
    <div class="sep"></div>
    <div class="centro negrito">PROMISSÓRIA</div>
    <p class="texto">
      Reconheço dever a importância de <b>${fmt(venda.total)}</b>
      referente às mercadorias acima descritas.
    </p>
    <p class="texto">Saldo devedor total: <b>${fmt(venda.saldoDevedor)}</b></p>
    <div class="assinatura">
      <div class="linha-assinatura"></div>
      <div class="centro">${esc(venda.clienteNome || '')}</div>
    </div>` : '';

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: 80mm auto; margin: 0; }
  body {
    width: 72mm;
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
  <div class="sep"></div>
  <table>${linhasItens}
    <tr class="total">
      <td>TOTAL</td><td></td><td class="dir">${fmt(venda.total)}</td>
    </tr>
  </table>
  <div class="sep"></div>
  <p class="info">Pagamento: ${rotuloForma(venda.formaPagamento)}</p>
  ${blocoFiado}
  <div class="rodape">Obrigado pela preferência!</div>
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
