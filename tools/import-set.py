"""Importa os CSVs do SET (export-set/) para o PostgreSQL do PDV.

Mapeamento (ver docs, seção "Banco legado do SET"):
  produtos.csv: NPROD->codigo (zero-padded preservado), DESCRICAO->nome,
                GRUPO->categoria, MARCA->marca (deduplicada, "SEM MARCA" vira null),
                PRECOVENDA->preco, UNVENDA->unidade, CODBARRAS->codigo_barras,
                DATACAD->data_criacao, QTDEDISP->estoque da variacao "padrao"
  clientes.csv: RAZAO->nome, CNPJCPF->cpf (so digitos), FONE1->telefone,
                EMAIL1->email, ENDERECO/NUMERO/BAIRRO/CIDADE/UF/CEP, DATACAD

Nao existe campo de saldo de fiado nos CSVs (o crediario do SET vive em
CREDIARIO/CONTASREC, nao exportadas) — nada de divida inicial e inventado.

Idempotente: produto por codigo (upsert), cliente por CPF (upsert) ou,
sem CPF, por nome exato. Variacao "padrao" so e criada se nao existir
(re-rodar nao sobrescreve estoque ja movimentado por vendas).
Transacional: qualquer erro desfaz a importacao inteira.

Uso: python3 tools/import-set.py [--dir ~/Downloads/export-set]
"""
import argparse
import csv
import os
import sys
from datetime import datetime

import psycopg2

DSN = dict(host='localhost', port=5433, dbname='pdv', user='pdv', password='pdv')

MARCAS_LIXO = {'', 'SEM MARCA', 'SEM-MARCA', 'S/MARCA', 'S/ MARCA'}


def limpo(s):
    s = (s or '').strip()
    return s if s else None


def so_digitos(s):
    d = ''.join(c for c in (s or '') if c.isdigit())
    return d if d else None


def numero(s, padrao=0):
    try:
        return float(s)
    except (TypeError, ValueError):
        return padrao


def data_ou_agora(s):
    try:
        return datetime.fromisoformat(s.strip())
    except (AttributeError, ValueError):
        return datetime.now()


def importar_marcas(cur, produtos):
    """Dedup case-insensitive; devolve mapa NOME_NORMALIZADO -> id."""
    nomes = {}
    for p in produtos:
        m = ' '.join((p.get('MARCA') or '').split())  # colapsa espacos duplicados
        if m.upper() not in MARCAS_LIXO:
            nomes.setdefault(m.upper(), m)  # primeira grafia vista vence

    mapa, criadas = {}, 0
    for chave, grafia in nomes.items():
        cur.execute('SELECT id FROM marca WHERE UPPER(nome) = %s', (chave,))
        linha = cur.fetchone()
        if linha:
            mapa[chave] = linha[0]
        else:
            cur.execute('INSERT INTO marca (nome) VALUES (%s) RETURNING id', (grafia,))
            mapa[chave] = cur.fetchone()[0]
            criadas += 1
    print(f'marcas: {criadas} criadas, {len(mapa)} no total')
    return mapa


def importar_produtos(cur, produtos, marcas):
    inseridos = atualizados = variacoes = 0
    for p in produtos:
        codigo = limpo(p['NPROD'])
        if not codigo:
            continue
        nome = limpo(p['DESCRICAO']) or f'SEM DESCRICAO ({codigo})'
        marca_chave = ' '.join((p.get('MARCA') or '').split()).upper()
        cur.execute(
            """
            INSERT INTO produto (codigo, nome, categoria, marca_id, preco, unidade,
                                 codigo_barras, data_criacao)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (codigo) DO UPDATE SET
                nome = EXCLUDED.nome,
                categoria = EXCLUDED.categoria,
                marca_id = EXCLUDED.marca_id,
                preco = EXCLUDED.preco,
                unidade = EXCLUDED.unidade,
                codigo_barras = EXCLUDED.codigo_barras
            RETURNING id, (xmax = 0) AS inserido
            """,
            (codigo, nome, limpo(p['GRUPO']), marcas.get(marca_chave),
             max(numero(p['PRECOVENDA']), 0), limpo(p['UNVENDA']) or 'UN',
             limpo(p['CODBARRAS']), data_ou_agora(p.get('DATACAD'))))
        produto_id, inserido = cur.fetchone()
        if inserido:
            inseridos += 1
        else:
            atualizados += 1

        # Variacao "padrao" (tamanho/cor nulos): so na primeira importacao —
        # re-rodar nao pode sobrescrever estoque ja baixado por vendas novas
        cur.execute(
            """
            INSERT INTO variacao (produto_id, tamanho, cor, estoque)
            VALUES (%s, NULL, NULL, %s)
            ON CONFLICT (produto_id, tamanho, cor) DO NOTHING
            """,
            (produto_id, round(numero(p['QTDEDISP']))))
        variacoes += cur.rowcount
    print(f'produtos: {inseridos} inseridos, {atualizados} atualizados, '
          f'{variacoes} variacoes padrao criadas')


def importar_clientes(cur, clientes):
    inseridos = atualizados = 0
    for c in clientes:
        nome = limpo(c['RAZAO'])
        if not nome:
            continue
        cpf = so_digitos(c['CNPJCPF'])
        dados = (nome, limpo(c['FONE1']), limpo(c['EMAIL1']), limpo(c['ENDERECO']),
                 limpo(c['NUMERO']), limpo(c['BAIRRO']), limpo(c['CIDADE']),
                 limpo(c['UF']), so_digitos(c['CEP']), data_ou_agora(c.get('DATACAD')))

        if cpf:
            cur.execute(
                """
                INSERT INTO cliente (nome, telefone, email, logradouro, numero,
                                     bairro, cidade, uf, cep, data_criacao, cpf)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (cpf) DO UPDATE SET
                    nome = EXCLUDED.nome, telefone = EXCLUDED.telefone,
                    email = EXCLUDED.email, logradouro = EXCLUDED.logradouro,
                    numero = EXCLUDED.numero, bairro = EXCLUDED.bairro,
                    cidade = EXCLUDED.cidade, uf = EXCLUDED.uf, cep = EXCLUDED.cep
                RETURNING (xmax = 0)
                """, dados + (cpf,))
            if cur.fetchone()[0]:
                inseridos += 1
            else:
                atualizados += 1
        else:
            # sem CPF: idempotencia por nome exato
            cur.execute('SELECT id FROM cliente WHERE cpf IS NULL AND nome = %s', (nome,))
            existente = cur.fetchone()
            if existente:
                cur.execute(
                    """
                    UPDATE cliente SET telefone=%s, email=%s, logradouro=%s, numero=%s,
                                       bairro=%s, cidade=%s, uf=%s, cep=%s
                    WHERE id=%s
                    """, dados[1:9] + (existente[0],))
                atualizados += 1
            else:
                cur.execute(
                    """
                    INSERT INTO cliente (nome, telefone, email, logradouro, numero,
                                         bairro, cidade, uf, cep, data_criacao)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """, dados)
                inseridos += 1
    print(f'clientes: {inseridos} inseridos, {atualizados} atualizados')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default=os.path.expanduser('~/Downloads/export-set'))
    pasta = ap.parse_args().dir

    with open(f'{pasta}/produtos.csv', encoding='utf-8') as f:
        produtos = list(csv.DictReader(f))
    with open(f'{pasta}/clientes.csv', encoding='utf-8') as f:
        clientes = list(csv.DictReader(f))
    print(f'csv: {len(produtos)} produtos, {len(clientes)} clientes')

    con = psycopg2.connect(**DSN)
    try:
        with con:  # uma transacao: commit no sucesso, rollback em qualquer erro
            with con.cursor() as cur:
                marcas = importar_marcas(cur, produtos)
                importar_produtos(cur, produtos, marcas)
                importar_clientes(cur, clientes)
                cur.execute('SELECT COUNT(*) FROM produto')
                total_p = cur.fetchone()[0]
                cur.execute('SELECT COUNT(*) FROM cliente')
                total_c = cur.fetchone()[0]
        print(f'TOTAIS no banco: {total_p} produtos, {total_c} clientes')
    finally:
        con.close()


if __name__ == '__main__':
    sys.exit(main())
