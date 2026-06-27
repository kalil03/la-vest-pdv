"""Importa o crediario do SET (contasrec.csv) para o PostgreSQL — v2.

ABERTAS ("a Receber"): viram PagamentoFiado tipo DEBITO_INICIAL com valor
NEGATIVO (soma divida no saldo calculado) e valor_aberto = valor.

PAGAS ("Recebida"): viram um PAR de lancamentos com saldo liquido zero —
  debito  DEBITO_INICIAL -valor, valor_aberto = 0, data = vencimento
  credito +valor, tipo DINHEIRO, data = DATAPAG (historico real de pagamento)
Assim o contas a receber mostra o historico completo sem mexer no saldo.

Ambos carregam `documento` = NDOC ("66/01" = notinha 66, parcela 01) — o
codigo que a loja conhece do sistema antigo.

Idempotente por substituicao: apaga tudo que veio do SET (tipo DEBITO_INICIAL
ou documento preenchido) e reinsere. Recebimentos feitos PELO SISTEMA nunca
sao tocados; depois da reinsercao, o rateio deles e reaplicado FIFO sobre as
parcelas legadas para restaurar o invariante SUM(valor_aberto) == saldo.

Uso: python3 tools/import-crediario.py [--dir ~/Downloads/export-set]
"""
import argparse
import csv
import os
import sys
from datetime import date

import psycopg2

DSN = dict(host='localhost', port=5433, dbname='pdv', user='pdv', password='pdv')


def so_digitos(s):
    d = ''.join(c for c in (s or '') if c.isdigit())
    return d if d else None


def carregar_mapa_legado(pasta):
    """IDCLIENTE do SET -> (cpf, nome)."""
    mapa = {}
    with open(f'{pasta}/clientes.csv', encoding='utf-8') as f:
        for c in csv.DictReader(f):
            idc = c['IDCLIENTE'].strip()
            if idc:
                mapa[idc] = (so_digitos(c['CNPJCPF']), c['RAZAO'].strip())
    return mapa


def resolver_cliente(cur, cache, cpf, nome, criados):
    chave = cpf or f'nome:{nome.upper()}'
    if chave in cache:
        return cache[chave]
    cliente_id = None
    if cpf:
        cur.execute('SELECT id FROM cliente WHERE cpf = %s', (cpf,))
        r = cur.fetchone()
        cliente_id = r[0] if r else None
    if cliente_id is None and nome:
        cur.execute('SELECT id FROM cliente WHERE UPPER(nome) = %s ORDER BY id LIMIT 1',
                    (nome.upper(),))
        r = cur.fetchone()
        cliente_id = r[0] if r else None
    if cliente_id is None and nome:
        cur.execute('INSERT INTO cliente (nome, cpf) VALUES (%s, %s) RETURNING id', (nome, cpf))
        cliente_id = cur.fetchone()[0]
        criados.append(nome)
    cache[chave] = cliente_id
    return cliente_id


def data_valida(s, padrao):
    s = (s or '').strip()
    return s if '1900-01-01' <= s <= '2100-12-31' or (s and not padrao) else (s or padrao)


def reaplicar_recebimentos_do_sistema(cur):
    """Restaura o invariante: o que o sistema já recebeu volta a abater
    as parcelas legadas (FIFO), pois a reinsercao as deixou 100% abertas."""
    cur.execute("""
        SELECT c.id,
               (SELECT COALESCE(SUM(p.valor_aberto), 0) FROM pagamento_fiado p
                WHERE p.cliente_id = c.id AND p.tipo = 'DEBITO_INICIAL')
             + (SELECT COALESCE(SUM(pf.valor_aberto), 0) FROM parcela_fiado pf
                JOIN venda v ON v.id = pf.venda_id WHERE v.cliente_id = c.id)
             - ((SELECT COALESCE(SUM(v.total), 0) FROM venda v
                 WHERE v.cliente_id = c.id AND v.forma_pagamento = 'FIADO')
              - (SELECT COALESCE(SUM(p.valor), 0) FROM pagamento_fiado p
                 WHERE p.cliente_id = c.id)) AS excesso
        FROM cliente c
        """)
    ajustados = 0
    for cliente_id, excesso in [r for r in cur.fetchall() if r[1] and r[1] > 0]:
        cur.execute("""
            SELECT id, valor_aberto FROM pagamento_fiado
            WHERE cliente_id = %s AND tipo = 'DEBITO_INICIAL' AND valor_aberto > 0
            ORDER BY data, id""", (cliente_id,))
        restante = excesso
        for pid, aberto in cur.fetchall():
            if restante <= 0:
                break
            abate = min(aberto, restante)
            cur.execute('UPDATE pagamento_fiado SET valor_aberto = valor_aberto - %s WHERE id = %s',
                        (abate, pid))
            restante -= abate
        ajustados += 1
    return ajustados


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default=os.path.expanduser('~/Downloads/export-set'))
    pasta = ap.parse_args().dir

    legado = carregar_mapa_legado(pasta)
    with open(f'{pasta}/contasrec.csv', encoding='utf-8') as f:
        parcelas = list(csv.DictReader(f))

    con = psycopg2.connect(**DSN)
    cache, criados, datas_suspeitas = {}, [], 0
    abertas = pagas = 0
    total_aberto = 0.0
    try:
        with con:
            with con.cursor() as cur:
                cur.execute("""DELETE FROM pagamento_fiado
                               WHERE tipo = 'DEBITO_INICIAL' OR documento IS NOT NULL""")
                print(f'lancamentos legados anteriores removidos: {cur.rowcount}')

                for p in parcelas:
                    situacao = p['SITUACAO'].strip()
                    valor = float(p['VALOR'] or 0) - float(p['VALORPAG'] or 0) \
                        if situacao == 'a Receber' else float(p['VALOR'] or 0)
                    if valor <= 0:
                        continue
                    cpf, nome = legado.get(p['IDCLIENTE'].strip(), (None, None))
                    nome = nome or p['CLIENTE'].strip()
                    if not nome:
                        continue
                    cliente_id = resolver_cliente(cur, cache, cpf, nome, criados)
                    documento = (p['NDOC'] or '').strip() or None
                    venc = (p['DATAVENCTO'] or '').strip() or date.today().isoformat()
                    if not ('1990-01-01' <= venc <= '2100-01-01'):
                        datas_suspeitas += 1  # preservada mesmo assim (ex.: 0257-01-10)
                    # tipo da notinha vem da coluna GRUPO ("Venda Tênis" / "Venda Roupas/Geral").
                    # Tênis estão 100% separadas no SET; aberta sem grupo => Roupa.
                    grupo = (p.get('GRUPO') or '')
                    tipo_nota = ('Tênis' if ('Tênis' in grupo or 'Tenis' in grupo)
                                 else ('Roupa' if 'Roupa' in grupo else None))

                    if situacao == 'a Receber':
                        cur.execute("""
                            INSERT INTO pagamento_fiado
                                (cliente_id, valor, tipo, data, valor_aberto, documento, tipo_notinha)
                            VALUES (%s, %s, 'DEBITO_INICIAL', CAST(%s AS date), %s, %s, %s)
                            """, (cliente_id, -valor, venc, valor, documento, tipo_nota or 'Roupa'))
                        abertas += 1
                        total_aberto += valor
                    elif situacao == 'Recebida':
                        pago_em = (p['DATAPAG'] or '').strip() or venc
                        if not ('1990-01-01' <= pago_em <= '2100-01-01'):
                            pago_em = venc
                        # par com saldo liquido zero: o debito ja quitado + o pagamento historico
                        cur.execute("""
                            INSERT INTO pagamento_fiado
                                (cliente_id, valor, tipo, data, valor_aberto, documento)
                            VALUES (%s, %s, 'DEBITO_INICIAL', CAST(%s AS date), 0, %s)
                            """, (cliente_id, -valor, venc, documento))
                        cur.execute("""
                            INSERT INTO pagamento_fiado
                                (cliente_id, valor, tipo, data, documento, detalhe)
                            VALUES (%s, %s, 'DINHEIRO', CAST(%s AS date), %s, %s)
                            """, (cliente_id, valor, pago_em, documento,
                                  f'Carnê SET nº {documento or "?"}'))
                        pagas += 1

                ajustados = reaplicar_recebimentos_do_sistema(cur)

                cur.execute("""
                    SELECT COUNT(*) FILTER (WHERE valor_aberto > 0),
                           COALESCE(SUM(valor_aberto), 0)
                    FROM pagamento_fiado WHERE tipo = 'DEBITO_INICIAL'""")
                n_abertas, soma_aberta = cur.fetchone()

                # invariante global: rateio fecha com o saldo calculado
                cur.execute("""
                    SELECT (SELECT COALESCE(SUM(valor_aberto),0) FROM pagamento_fiado WHERE tipo='DEBITO_INICIAL')
                         + (SELECT COALESCE(SUM(valor_aberto),0) FROM parcela_fiado)
                         - ((SELECT COALESCE(SUM(total),0) FROM venda WHERE forma_pagamento='FIADO')
                          - (SELECT COALESCE(SUM(valor),0) FROM pagamento_fiado))""")
                divergencia = cur.fetchone()[0]
                if abs(float(divergencia)) > 0.01:
                    raise RuntimeError(f'invariante quebrado: divergencia de R$ {divergencia}')

        print(f'abertas importadas: {abertas} (R$ {total_aberto:,.2f}) | pagas importadas: {pagas}')
        print(f'no banco: {n_abertas} parcelas legadas em aberto, R$ {float(soma_aberta):,.2f}')
        print(f'clientes com rateio reaplicado (recebimentos do sistema): {ajustados}')
        print(f'clientes criados na hora: {len(criados)} | datas suspeitas preservadas: {datas_suspeitas}')
        print('invariante SUM(valor_aberto) == saldo: OK')
    finally:
        con.close()


if __name__ == '__main__':
    sys.exit(main())
