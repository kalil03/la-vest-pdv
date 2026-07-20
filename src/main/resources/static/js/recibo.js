/**
 * Notinha/promissória para bobina térmica de 80mm.
 * Renderizada num iframe oculto e impressa via window.print() — sem popup,
 * sem biblioteca ESC/POS. A área útil de impressão fica em ~72mm.
 */

/**
 * Comprovante de venda no layout do sistema antigo (Set) — cupom NÃO fiscal,
 * 80mm: cabeçalho da loja em caixa, dados da venda, itens (código/descrição/
 * qtde/unit/total), totais (Sub-Total/Desconto/TOTAL/Pago/Troco) e a tabela de
 * parcelas. Sem o texto de "Reconheço dever" (era outro documento no Set).
 * Para o fiado JÁ com pagamento (reimpressão pelo carnê), mostra a via da loja
 * ATUALIZADA com o saldo por parcela e o que resta da nota (para grampear).
 */
function reciboHTML(venda, loja) {
  const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const dataObj = new Date(venda.data);
  const dataStr = dataObj.toLocaleDateString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  const horaStr = dataObj.toLocaleTimeString('pt-BR', { timeZone: 'America/Sao_Paulo', hour: '2-digit', minute: '2-digit' });
  const dataBr = (iso) => { const [a, m, d] = iso.split('-'); return `${d}/${m}/${a}`; };

  const larguraMm = Math.max(40, parseInt(loja?.impLarguraMm, 10) || 80);
  const fiado = venda.formaPagamento === 'FIADO';

  // itens: 2 linhas por produto (código+descrição / qtde x unit = total)
  const linhasItens = venda.itens.map((i) => `
    <tr><td colspan="3" class="desc">${esc(i.codigo || '')}  ${esc(i.descricao)}</td></tr>
    <tr>
      <td>${i.quantidade} x ${fmt(i.precoUnit)}</td>
      <td></td>
      <td class="dir">${fmt(i.subtotal)}</td>
    </tr>`).join('');

  // "Pago" no balcão: à vista é o total; no fiado é a entrada (o resto vira parcela)
  const pago = fiado ? Number(venda.entrada || 0) : Number(venda.total);
  const troco = 0;

  // no fiado o que importa é o que o cliente sai devendo: total − desconto − entrada.
  // Somo as parcelas (é o que está impresso logo abaixo) para não haver divergência.
  const aFinanciar = fiado
    ? venda.parcelas.reduce((s, p) => s + Number(p.valor), 0)
    : 0;

  // parcelas em aberto → nota já teve pagamento (reimpressão do carnê): via da loja atualizada
  const temPagamento = fiado && venda.parcelas.some(
    (p) => p.valorAberto != null && Number(p.valorAberto) < Number(p.valor));

  const linhasParcelas = fiado && venda.parcelas.length
    ? venda.parcelas.map((p) => {
        const situacao = temPagamento
          ? (Number(p.valorAberto) === 0 ? ' PAGA' : ` resta ${fmt(p.valorAberto)}`)
          : '';
        return `<tr><td>${p.numero}ª</td><td>${dataBr(p.vencimento)}</td><td class="dir">${fmt(p.valor)}${situacao}</td></tr>`;
      }).join('')
    : '';

  const blocoParcelas = fiado && linhasParcelas ? `
    <div class="cabtab">FORMA DE PAGTO: CREDIÁRIO (FIADO)</div>
    <table>
      <tr class="th"><td>PARC.</td><td>VENCTO</td><td class="dir">VALOR</td></tr>
      ${linhasParcelas}
    </table>` : '';

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

  const pagamentoAVista = !fiado
    ? rotuloForma(venda.formaPagamento) + (venda.formaPagamento === 'CARTAO' && venda.parcelasCartao > 1 ? ` ${venda.parcelasCartao}x` : '')
    : '';

  // fiado é PROMISSÓRIA: linha de assinatura do cliente. O texto de "Reconheço dever"
  // saiu a pedido do usuário — comia bobina e o valor devido agora está no bloco de
  // totais (A FINANCIAR), em destaque, em vez de escondido no meio da frase.
  const titulo = fiado ? 'PROMISSÓRIA' : 'CUPOM NÃO FISCAL';
  const blocoAssinatura = fiado ? `
    ${venda.observacao ? `<div class="promtexto">Obs.: ${esc(venda.observacao)}</div>` : ''}
    <div class="assinatura">
      <div class="linha-assinatura"></div>
      <div class="centro">${esc(venda.clienteNome || '')}</div>
      <div class="centro" style="font-size:11px">Assinatura do(a) cliente</div>
    </div>` : '';

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  /* tudo em negrito e tamanho uniforme: na térmica o traço fino sai fraco/falhado */
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 13px; font-weight: bold; color: #000; }
  .centro { text-align: center; }
  .negrito { font-weight: bold; }
  .dir { text-align: right; }
  .caixa { border: 1.5px solid #000; border-radius: 6px; padding: 3px 6px; margin: 3px 0; }
  .lojanome { text-align: center; font-size: 15px; }
  .info { text-align: center; font-size: 12px; margin: 0; }
  .titulo { text-align: center; font-size: 15px; margin: 4px 0 3px; letter-spacing: 1px; }
  .sep { border-top: 1.5px solid #000; margin: 4px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 13px; }
  .desc { font-size: 13px; }
  .th td { border-bottom: 1.5px solid #000; font-size: 12px; }
  .cabtab { font-size: 12px; margin-top: 5px; }
  .total td { font-size: 16px; padding-top: 4px; }
  .promtexto { font-size: 12px; margin: 6px 2px; text-align: justify; line-height: 1.3; }
  .assinatura { margin-top: 95px; }
  .linha-assinatura { border-top: 1.5px solid #000; margin: 0 10px 3px; }
  .rodape { text-align: center; font-size: 12px; margin-top: 6px; }
</style>
</head>
<body>
  <div class="caixa">
    <div class="lojanome">${esc(loja.nome)}</div>
    <p class="info">${esc(loja.endereco)}</p>
    <p class="info">${esc(loja.telefone)}</p>
  </div>
  <div class="titulo">${titulo}</div>
  <div class="caixa">
    <table>
      <tr><td style="width:70px">Número:</td><td>${venda.id}</td></tr>
      <tr><td>Data:</td><td>${fiado ? dataStr : `${dataStr} - ${horaStr}`}</td></tr>
      ${venda.clienteNome ? `<tr><td>Cliente:</td><td>${esc(venda.clienteNome)}</td></tr>` : ''}
      ${venda.vendedorNome ? `<tr><td>Vendedor:</td><td>${esc(venda.vendedorNome)}</td></tr>` : ''}
    </table>
  </div>
  <table>
    <tr class="th"><td>CÓDIGO</td><td>DESCRIÇÃO</td><td class="dir">VL.TOTAL</td></tr>
    ${linhasItens}
  </table>
  <div class="sep"></div>
  <table>
    <tr><td>Qtd. Total de Itens:</td><td></td><td class="dir">${venda.itens.length}</td></tr>
    <tr><td>Sub-Total:</td><td></td><td class="dir">${fmt(venda.subtotal)}</td></tr>
    <tr><td>Desconto:</td><td></td><td class="dir">${fmt(venda.desconto)}</td></tr>
    <tr${fiado ? '' : ' class="total"'}><td>TOTAL:</td><td></td><td class="dir">${fmt(venda.total)}</td></tr>
    ${fiado ? `
    <tr><td>Entrada:</td><td></td><td class="dir">${fmt(pago)}</td></tr>
    <tr class="total"><td>A FINANCIAR:</td><td></td><td class="dir">${fmt(aFinanciar)}</td></tr>` : `
    <tr><td>Pago:</td><td></td><td class="dir">${fmt(pago)}</td></tr>
    <tr><td>Troco:</td><td></td><td class="dir">${fmt(troco)}</td></tr>`}
  </table>
  ${pagamentoAVista ? `<div class="sep"></div><p class="info">Forma de pagamento: ${pagamentoAVista}</p>` : ''}
  ${blocoParcelas}
  ${blocoAtualizada}
  ${blocoAssinatura}
  <div class="sep"></div>
  <div class="rodape">Agradecemos a Preferência<br>Volte Sempre</div>
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
/**
 * Junta vários documentos térmicos num ÚNICO job de impressão, com quebra de
 * página entre eles (a térmica corta/avança entre as vias). Encadear jobs
 * separados na MP-4200 derrubava o segundo ("Falha na impressão"); um job só
 * elimina o problema na raiz — e o operador confere tudo num preview só.
 */
function juntarDocumentos(htmls) {
  if (htmls.length === 1) return htmls[0];
  const parser = new DOMParser();
  const docs = htmls.map((h) => parser.parseFromString(h, 'text/html'));
  const estilos = [...new Set(docs.map((d) => d.head.querySelector('style')?.textContent || ''))].join('\n');
  // Cada via é uma "página" com quebra forçada depois (menos a última): uso as duas
  // propriedades — a moderna `break-after` e a legada `page-break-after` — porque
  // o driver da térmica só corta/avança entre páginas se enxergar o page break.
  // Uma folga no pé (padding-bottom) evita que o corte coma a última linha.
  const paginas = docs.map((d, i) => {
    const quebra = i < docs.length - 1 ? 'page-break-after: always; break-after: page;' : '';
    return `<div style="${quebra} padding-bottom: 6mm;">${d.body.innerHTML}</div>`;
  }).join('');
  return `<!DOCTYPE html><html lang="pt-BR"><head><meta charset="UTF-8"><style>${estilos}</style></head><body>${paginas}</body></html>`;
}

// Impressão DIRETA, sem a tela "Confira antes de imprimir". O usuário pediu para
// tirar essa prévia — era uma etapa a mais, já que a impressão (kiosk / diálogo
// do navegador) vem logo em seguida. Mantido o nome da função para não mexer em
// todos os chamadores; devolve uma promise já resolvida.
function previewImprimir(html) {
  imprimirHTML(html);
  return Promise.resolve();
}

/**
 * Impressão genérica via iframe oculto (sem popup).
 *
 * O iframe só sai do DOM DEPOIS que o navegador termina de despachar o job
 * (evento afterprint). Removê-lo cedo demais aborta a impressão no meio do
 * caminho — o cupom tem ~1 MB/2 páginas e nem sempre despacha em 3s, o que
 * dava "Falha na impressão" intermitente (o job nem chegava ao spooler do
 * Windows). O timeout longo é só rede de segurança se o afterprint não vier.
 */
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
    const win = iframe.contentWindow;
    let removido = false;
    const remover = () => { if (!removido) { removido = true; iframe.remove(); } };
    win.addEventListener('afterprint', () => setTimeout(remover, 2000));
    setTimeout(remover, 60000);
    win.focus();
    win.print();
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
  * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 13px; font-weight: bold; color: #000; }
  .centro { text-align: center; }
  .dir { text-align: right; }
  h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
  .info { text-align: center; font-size: 12px; margin: 0; }
  .sep { border-top: 1.5px solid #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 13px; }
  .destaque { font-size: 15px; font-weight: bold; }
  .texto { font-size: 12px; margin: 6px 0; }
  .assinatura { margin-top: 60px; }
  .linha-assinatura { border-top: 1.5px solid #000; margin: 0 8px 2px; }
  .rodape { text-align: center; font-size: 12px; margin-top: 8px; }
  .nota-bloco { margin: 6px 0; padding-bottom: 4px; border-bottom: 1.5px solid #000; }
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

/**
 * Promissória com SALDO ATUALIZADO após uma baixa (80mm). A baixa quita as notas
 * em aberto do cliente; este cupom lista o que foi quitado e mostra o saldo atual
 * zerado — para grampear na promissória física do SET em vez de marcar à mão.
 */
function promissoriaBaixaHTML(c, loja) {
  const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const dataHora = new Date(c.data).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' });
  const larguraMm = Math.max(40, parseInt(loja?.impLarguraMm, 10) || 80);

  // uma baixa pode ser PARCIAL (ajuste) — só é "QUITADA" se não sobrou nada nela
  const linhas = (c.notas || []).map((n) => {
    const resta = Number(n.restante || 0);
    return `
    <tr><td>${esc(n.descricao)}</td><td class="dir">${fmt(n.valor)} — ${resta > 0 ? `resta ${fmt(resta)}` : 'QUITADA'}</td></tr>`;
  }).join('');

  // o cliente pode ter OUTRAS notinhas em aberto: o saldo vem calculado do back
  const saldo = Number(c.saldoRestante || 0);

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 13px; font-weight: bold; color: #000; }
  .centro { text-align: center; }
  .negrito { font-weight: bold; }
  .dir { text-align: right; }
  h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
  .info { text-align: center; font-size: 12px; margin: 0; }
  .sep { border-top: 1.5px solid #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 13px; }
  .destaque { font-size: 15px; font-weight: bold; }
  .assinatura { margin-top: 60px; }
  .linha-assinatura { border-top: 1.5px solid #000; margin: 0 8px 2px; }
  .rodape { text-align: center; font-size: 12px; margin-top: 8px; }
</style>
</head>
<body>
  <h1>${esc(loja.nome)}</h1>
  <p class="info">${esc(loja.endereco)}</p>
  <p class="info">${esc(loja.telefone)}</p>
  <div class="sep"></div>
  <div class="centro negrito">PROMISSÓRIA — SALDO ATUALIZADO</div>
  <p class="info">Cliente: ${esc(c.clienteNome)}</p>
  <p class="info">Baixa nº ${c.baixaId} — ${dataHora}</p>
  ${c.operador ? `<p class="info">Registrado por: ${esc(c.operador)}</p>` : ''}
  <div class="sep"></div>
  <table>${linhas}</table>
  <div class="sep"></div>
  <table>
    <tr><td class="destaque">TOTAL BAIXADO</td><td class="dir destaque">${fmt(c.total)}</td></tr>
    <tr><td class="negrito">SALDO ATUAL</td><td class="dir negrito">${saldo > 0 ? fmt(saldo) : 'R$ 0,00 — QUITADO'}</td></tr>
  </table>
  <div class="assinatura">
    <div class="linha-assinatura"></div>
    <div class="centro">${esc(c.clienteNome)}</div>
  </div>
  <div class="rodape">${esc(loja?.impRodape ?? 'Obrigado pela preferência!')}</div>
</body>
</html>`;
}

function imprimirPromissoriaBaixa(comprovante, loja) {
  return previewImprimir(promissoriaBaixaHTML(comprovante, loja));
}
