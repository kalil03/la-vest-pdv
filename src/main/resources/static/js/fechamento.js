/**
 * Fechamento mensal: renderiza o JSON de /api/fechamento-mensal.
 * Exportar CSV/PDF baixa do MESMO cálculo — a tela nunca faz conta própria.
 */

const $ = (id) => document.getElementById(id);
const fmt = (v) => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

function anoMes() {
  const [ano, mes] = $('fm-mes').value.split('-');
  return { ano, mes: Number(mes) };
}

async function carregar() {
  if (!$('fm-mes').value) {
    $('fm-mes').value = new Date().toLocaleDateString('sv-SE').slice(0, 7); // yyyy-mm local
  }
  const { ano, mes } = anoMes();
  const resp = await fetch(`/api/fechamento-mensal?ano=${ano}&mes=${mes}`);
  if (!resp.ok) return;
  const f = await resp.json();

  const semMovimento = f.qtdVendas === 0
    && Number(f.recebimentoMes.total) === 0
    && Number(f.entradasFiado.total) === 0
    && Number(f.retiradas.total) === 0;
  $('fm-vazio').hidden = !semMovimento;
  $('fm-conteudo').hidden = semMovimento;
  if (semMovimento) return;

  // por categoria (a ordem já vem fixa do servidor: Geral, Tênis, Sem tipo)
  const cat = (nome) => f.porCategoria.find((c) => c.categoria === nome);
  $('fm-cat-geral').textContent = fmt(cat('Geral')?.total ?? 0);
  $('fm-cat-tenis').textContent = fmt(cat('Tênis')?.total ?? 0);
  const semTipo = cat('Sem tipo');
  $('fm-cat-semtipo-box').hidden = !semTipo;
  if (semTipo) $('fm-cat-semtipo').textContent = fmt(semTipo.total);
  $('fm-total').textContent = fmt(f.totalGeral);

  $('fm-vendedores').innerHTML = f.porVendedor.map((v) => `
    <tr>
      <td class="font-medium">${v.vendedor}</td>
      <td class="num text-muted-foreground">${v.qtd}×</td>
      <td class="num">${fmt(v.aVista)}</td>
      <td class="num">${fmt(v.aPrazo)}</td>
      <td class="num font-bold">${fmt(v.total)}</td>
    </tr>`).join('');

  const outro = (rotulo, r, obs) => `
    <tr>
      <td>${rotulo}${obs ? ` <span class="text-muted-foreground text-[11px]">${obs}</span>` : ''}</td>
      <td class="num text-muted-foreground">${r.qtd}×</td>
      <td class="num font-semibold">${fmt(r.total)}</td>
    </tr>`;
  $('fm-outros').innerHTML =
    outro('Recebimento de carnê no balcão', f.recebimentoMes, '') +
    outro('Entradas de fiado', f.entradasFiado, 'já incluídas no total das vendas') +
    outro('Retiradas (sangria)', f.retiradas, 'dinheiro que saiu do caixa');
}

function exportar(formato) {
  const { ano, mes } = anoMes();
  location.href = `/api/fechamento-mensal/${formato}?ano=${ano}&mes=${mes}`;
}

$('fm-mes').addEventListener('input', carregar);
$('fm-csv').addEventListener('click', () => exportar('csv'));
$('fm-pdf').addEventListener('click', () => exportar('pdf'));
carregar();
