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

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 12px; color: #000; }
  .centro { text-align: center; }
  .negrito { font-weight: bold; }
  .dir { text-align: right; }
  .caixa { border: 1px solid #000; border-radius: 7px; padding: 4px 8px; margin: 4px 0; }
  .lojanome { text-align: center; font-weight: bold; font-size: 13px; }
  .info { text-align: center; font-size: 10px; margin: 0; }
  .titulo { text-align: center; font-weight: bold; font-size: 12px; margin: 5px 0 3px; }
  .sep { border-top: 1px dashed #000; margin: 5px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 11px; }
  .desc { font-weight: bold; }
  .th td { font-weight: bold; border-bottom: 1px solid #000; font-size: 10px; }
  .cabtab { font-weight: bold; font-size: 10px; margin-top: 4px; }
  .total td { font-size: 14px; font-weight: bold; padding-top: 3px; }
  .rodape { text-align: center; font-size: 10px; margin-top: 8px; }
</style>
</head>
<body>
  <div class="caixa">
    <div class="lojanome">${esc(loja.nome)}</div>
    <p class="info">${esc(loja.endereco)}</p>
    <p class="info">${esc(loja.telefone)}</p>
  </div>
  <div class="titulo">CUPOM NÃO FISCAL</div>
  <div class="caixa">
    <table>
      <tr><td style="width:70px">Número:</td><td>${venda.id}</td></tr>
      <tr><td>Data:</td><td>${dataStr} - ${horaStr}</td></tr>
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
    <tr class="total"><td>TOTAL:</td><td></td><td class="dir">${fmt(venda.total)}</td></tr>
    <tr><td>Pago:</td><td></td><td class="dir">${fmt(pago)}</td></tr>
    <tr><td>Troco:</td><td></td><td class="dir">${fmt(troco)}</td></tr>
  </table>
  ${pagamentoAVista ? `<div class="sep"></div><p class="info">Forma de pagamento: ${pagamentoAVista}</p>` : ''}
  ${blocoParcelas}
  ${blocoAtualizada}
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

// preview aberto no momento — abrir outro fecha o anterior DE VERDADE
// (listener removido + promise resolvida); só remover o nó do DOM deixava um
// listener órfão de Enter/F10 que disparava impressão fantasma e travava
// pra sempre quem estivesse aguardando a promise antiga
let fecharPreviewAberto = null;

function previewImprimir(html) {
  return new Promise((resolve) => {
  fecharPreviewAberto?.();
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

  const limpar = () => {
    document.removeEventListener('keydown', teclas, true);
    overlay.remove();
    fecharPreviewAberto = null;
  };
  const fechar = () => { limpar(); resolve(); };
  const imprimir = () => { limpar(); imprimirHTML(html); resolve(); };
  fecharPreviewAberto = fechar;
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

/**
 * DANFE NFC-e (cupom fiscal 80mm) — impresso APÓS a autorização na SEFAZ.
 * `danfe` vem do backend (emissão): identidade fiscal do emitente, chave,
 * protocolo, data de autorização, URL de consulta e o QR Code já em SVG
 * (gerado offline). Os itens/total saem da própria venda.
 */
function danfeNfceHTML(venda, loja, danfe) {
  const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
  const larguraMm = Math.max(40, parseInt(loja?.impLarguraMm, 10) || 80);

  // número (nNF) e série saem da própria chave: cUF(2)AAMM(4)CNPJ(14)mod(2)serie(3)nNF(9)...
  const chave = danfe.chave || '';
  const serie = chave.length === 44 ? parseInt(chave.slice(22, 25), 10) : null;
  const numero = chave.length === 44 ? parseInt(chave.slice(25, 34), 10) : null;

  // itens: 2 linhas (código+descrição / qtde un x unit = total) — cabe em 80mm
  const linhasItens = venda.itens.map((i) => `
    <tr><td colspan="3" class="desc">${esc(i.codigo || '')}  ${esc(i.descricao)}</td></tr>
    <tr><td>${i.quantidade} UN x ${fmt(i.precoUnit)}</td><td></td><td class="dir">${fmt(i.subtotal)}</td></tr>`).join('');

  const consumidor = venda.clienteNome ? esc(venda.clienteNome) : 'CONSUMIDOR NÃO IDENTIFICADO';
  const aviso = danfe.homologacao
    ? `<div class="centro negrito" style="margin:5px 0">EMITIDA EM HOMOLOGAÇÃO<br>SEM VALOR FISCAL</div>` : '';

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 12px; color: #000; }
  .centro { text-align: center; }
  .negrito { font-weight: bold; }
  .dir { text-align: right; }
  .caixa { border: 1px solid #000; padding: 4px 8px; margin: 4px 0; }
  .info { text-align: center; font-size: 10px; margin: 0; }
  .titulo { text-align: center; font-weight: bold; font-size: 11px; margin: 5px 0 2px; }
  .sub { text-align: center; font-size: 9px; margin: 0 0 4px; }
  .sep { border-top: 1px solid #000; margin: 5px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 11px; }
  .desc { font-weight: bold; }
  .th td { font-weight: bold; border-bottom: 1px solid #000; font-size: 9px; }
  .total td { font-size: 12px; font-weight: bold; }
  .rotulo { text-align: center; font-weight: bold; font-size: 10px; margin-top: 4px; }
  .chave { font-size: 11px; word-break: break-all; text-align: center; letter-spacing: .5px; margin: 2px 0; }
  .qr { display: flex; justify-content: center; margin: 6px 0 2px; }
  .qr svg { width: ${Math.min(50, larguraMm - 20)}mm; height: ${Math.min(50, larguraMm - 20)}mm; }
</style>
</head>
<body>
  <div class="caixa">
    <div class="centro negrito">${esc(danfe.razaoSocial || loja.nome)}</div>
    <p class="info">${esc(danfe.endereco || loja.endereco || '')}</p>
    <p class="info">CNPJ: ${esc(danfe.cnpj || '')}${danfe.inscricaoEstadual ? ' - IE: ' + esc(danfe.inscricaoEstadual) : ''}${loja.telefone ? ' - TEL: ' + esc(loja.telefone) : ''}</p>
  </div>
  <div class="titulo">DANFE NFC-e - Documento Auxiliar da<br>Nota Fiscal Eletrônica de Consumidor Final</div>
  <div class="sub">Não permite aproveitamento de crédito do ICMS</div>
  <div class="sep"></div>
  <table>
    <tr class="th"><td>CÓD / DESCRIÇÃO</td><td></td><td class="dir">VL.TOTAL</td></tr>
    ${linhasItens}
  </table>
  <div class="sep"></div>
  <table>
    <tr><td>Qtd. Total de Itens:</td><td></td><td class="dir">${venda.itens.length}</td></tr>
    <tr class="total"><td>Valor Total R$:</td><td></td><td class="dir">${fmt(venda.total)}</td></tr>
    <tr><td>Valor Desconto R$:</td><td></td><td class="dir">${fmt(venda.desconto)}</td></tr>
  </table>
  <table>
    <tr class="th"><td>Forma de Pagamento</td><td class="dir">Valor Pago</td></tr>
    <tr><td>${rotuloForma(venda.formaPagamento)}</td><td class="dir">${fmt(venda.total)}</td></tr>
  </table>
  <div class="sep"></div>
  ${aviso}
  <p class="info">${numero != null ? `Número: ${numero} - Série: ${serie}` : ''}</p>
  <p class="info">Emissão: ${esc(danfe.dataAutorizacao || '')} - Via Consumidor</p>
  <div class="sep"></div>
  <p class="info">Consulte pela chave de acesso em:</p>
  <p class="info">${esc(danfe.urlConsulta || '')}</p>
  <div class="rotulo">CHAVE DE ACESSO</div>
  <div class="chave">${esc(danfe.chaveFormatada || chave)}</div>
  <div class="sep"></div>
  <div class="rotulo">CONSUMIDOR</div>
  <p class="info">${consumidor}</p>
  <div class="sep"></div>
  <div class="qr">${danfe.qrCodeSvg || ''}</div>
  <p class="info">Consulta via leitor de QR Code</p>
  <div class="rotulo">Protocolo de Autorização</div>
  <p class="info">${esc(danfe.protocolo || '')}${danfe.dataAutorizacao ? ' - ' + esc(danfe.dataAutorizacao) : ''}</p>
</body>
</html>`;
}

function imprimirDanfeNfce(venda, loja, danfe) {
  return previewImprimir(danfeNfceHTML(venda, loja, danfe));
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

  const linhas = (c.notas || []).map((n) => `
    <tr><td>${esc(n.descricao)}</td><td class="dir">${fmt(n.valor)} — QUITADA</td></tr>`).join('');

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
<meta charset="UTF-8">
<style>
  @page { size: ${larguraMm}mm auto; margin: 0; }
  body { width: ${larguraMm - 8}mm; margin: 0 auto; font-family: 'Courier New', monospace; font-size: 12px; color: #000; }
  .centro { text-align: center; }
  .negrito { font-weight: bold; }
  .dir { text-align: right; }
  h1 { font-size: 15px; margin: 6px 0 2px; text-align: center; }
  .info { text-align: center; font-size: 11px; margin: 0; }
  .sep { border-top: 1px dashed #000; margin: 6px 0; }
  table { width: 100%; border-collapse: collapse; }
  td { padding: 1px 0; vertical-align: top; font-size: 11px; }
  .destaque { font-size: 15px; font-weight: bold; }
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
  <div class="centro negrito">PROMISSÓRIA — SALDO ATUALIZADO</div>
  <p class="info">Cliente: ${esc(c.clienteNome)}</p>
  <p class="info">Baixa nº ${c.baixaId} — ${dataHora}</p>
  ${c.operador ? `<p class="info">Registrado por: ${esc(c.operador)}</p>` : ''}
  <div class="sep"></div>
  <table>${linhas}</table>
  <div class="sep"></div>
  <table>
    <tr><td class="destaque">TOTAL QUITADO</td><td class="dir destaque">${fmt(c.total)}</td></tr>
    <tr><td class="negrito">SALDO ATUAL</td><td class="dir negrito">R$ 0,00 — QUITADO</td></tr>
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
