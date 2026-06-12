/**
 * Campos de moeda: digitou "10" e saiu do campo → mostra "R$ 10,00".
 * No foco volta ao número cru para editar. Aceita vírgula ou ponto.
 *
 *   instalarMoeda(input)  — ativa o comportamento num <input>
 *   lerMoeda(input)       — lê o valor numérico atual (Number)
 *   formatarMoeda(input)  — força a exibição formatada
 */

function lerMoeda(input) {
  let s = String(input.value || '').replace(/[^\d.,-]/g, '');
  if (!s) return 0;
  if (s.includes(',')) {
    s = s.replace(/\./g, '').replace(',', '.'); // 1.234,56 → 1234.56
  }
  const v = parseFloat(s);
  return Number.isFinite(v) ? Math.round(v * 100) / 100 : 0;
}

function formatarMoeda(input) {
  const v = lerMoeda(input);
  input.value = v ? v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }) : '';
}

function instalarMoeda(input, aoMudar) {
  if (input.dataset.moeda) return; // não instalar duas vezes
  input.dataset.moeda = '1';
  if (input.type === 'number') input.type = 'text';
  input.inputMode = 'decimal';
  input.autocomplete = 'off';

  input.addEventListener('focus', () => {
    const v = lerMoeda(input);
    input.value = v ? String(v).replace('.', ',') : '';
    setTimeout(() => input.select(), 0);
  });
  input.addEventListener('blur', () => formatarMoeda(input));
  if (aoMudar) input.addEventListener('input', aoMudar);
  formatarMoeda(input);
}
