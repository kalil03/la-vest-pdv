"""Importa as parcelas EM ABERTO do crediario do SET (contasrec.csv) como
PagamentoFiado tipo DEBITO_INICIAL com valor NEGATIVO (soma divida no saldo
calculado). Parcelas "Recebida" (pagas) sao ignoradas.

Vinculo com o cliente: CONTASREC.IDCLIENTE -> clientes.csv (IDCLIENTE) ->
nosso cliente por CPF (so digitos) ou, sem CPF, por nome exato. Se o cliente
da divida nao existir no PostgreSQL, e criado pelo nome (divida nao se perde).

Idempotente por substituicao: apaga TODOS os DEBITO_INICIAL e reinsere,
na mesma transacao — re-rodar nunca duplica. Vencimento original preservado
na coluna data.

Uso: python3 tools/import-crediario.py [--dir ~/Downloads/export-set]
"""
import argparse
import csv
import os
import sys
from datetime import date

import psycopg2

DSN = dict(host='localhost', port=5433, dbname='pdv', user='pdv', password='pdv')

ABERTA = 'a Receber'


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


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default=os.path.expanduser('~/Downloads/export-set'))
    pasta = ap.parse_args().dir

    legado = carregar_mapa_legado(pasta)
    with open(f'{pasta}/contasrec.csv', encoding='utf-8') as f:
        parcelas = [x for x in csv.DictReader(f) if x['SITUACAO'].strip() == ABERTA]
    print(f'parcelas em aberto no csv: {len(parcelas)}')

    con = psycopg2.connect(**DSN)
    cache, criados, sem_cliente, datas_suspeitas = {}, [], [], 0
    try:
        with con:
            with con.cursor() as cur:
                cur.execute("DELETE FROM pagamento_fiado WHERE tipo = 'DEBITO_INICIAL'")
                print(f'debitos iniciais anteriores removidos: {cur.rowcount}')

                inseridos, total = 0, 0.0
                for p in parcelas:
                    valor = float(p['VALOR'] or 0) - float(p['VALORPAG'] or 0)
                    if valor <= 0:
                        continue
                    cpf, nome = legado.get(p['IDCLIENTE'].strip(), (None, None))
                    nome = nome or p['CLIENTE'].strip()
                    if not nome:
                        sem_cliente.append(p['IDCONTAREC'])
                        continue
                    cliente_id = resolver_cliente(cur, cache, cpf, nome, criados)

                    venc = (p['DATAVENCTO'] or '').strip() or date.today().isoformat()
                    if not ('2000-01-01' <= venc <= '2100-01-01'):
                        datas_suspeitas += 1  # preservada mesmo assim (ex.: 0257-01-10)
                    cur.execute(
                        """
                        INSERT INTO pagamento_fiado (cliente_id, valor, tipo, data, valor_aberto)
                        VALUES (%s, %s, 'DEBITO_INICIAL', CAST(%s AS date), %s)
                        """, (cliente_id, -valor, venc, valor))
                    inseridos += 1
                    total += valor

                cur.execute("""
                    SELECT COUNT(*), COALESCE(-SUM(valor), 0) FROM pagamento_fiado
                    WHERE tipo = 'DEBITO_INICIAL'""")
                n_banco, total_banco = cur.fetchone()

        print(f'parcelas importadas: {inseridos} | divida total: R$ {total:,.2f}')
        print(f'no banco apos commit: {n_banco} debitos iniciais, R$ {float(total_banco):,.2f}')
        print(f'clientes criados na hora (nao existiam): {len(criados)}'
              + (f' -> {criados[:5]}...' if criados else ''))
        print(f'parcelas sem cliente identificavel (puladas): {len(sem_cliente)}')
        print(f'datas de vencimento suspeitas preservadas: {datas_suspeitas}')
    finally:
        con.close()


if __name__ == '__main__':
    sys.exit(main())
