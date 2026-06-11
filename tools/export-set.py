"""Exporta tabelas do SET (Firebird 4) para CSV UTF-8.

O banco declara WIN1251 (cirílico) por engano, mas os dados foram gravados
como WIN1252 — deixar o servidor transliterar corrompe Ç e acentos.
Solução: CAST de cada coluna de texto para CHARACTER SET OCTETS (passthrough
de bytes, sem transliteração) e decodificação cp1252 aqui no cliente.
"""
import csv
import decimal
import datetime

from firebird.driver import connect, driver_config

driver_config.server_defaults.host.value = '172.17.0.3'
driver_config.server_defaults.port.value = '3050'

TABELAS = ['PRODUTOS', 'CLIENTES', 'PRODUTOSGRADE', 'GRADES', 'GRUPOS', 'PRODUTOS_CODBARRAS']
SAIDA = '/out'

TIPO_TEXTO = (14, 37)   # CHAR, VARCHAR
TIPO_BLOB = 261

con = connect('172.17.0.3:/firebird/data/DADOS.FDB', user='SYSDBA', password='masterkey', charset='UTF8')
cur = con.cursor()

def colunas_da_tabela(tabela):
    cur.execute("""
        SELECT TRIM(rf.rdb$field_name), f.rdb$field_type, f.rdb$field_length,
               COALESCE(f.rdb$field_sub_type, 0)
        FROM rdb$relation_fields rf
        JOIN rdb$fields f ON f.rdb$field_name = rf.rdb$field_source
        WHERE rf.rdb$relation_name = ?
        ORDER BY rf.rdb$field_position""", (tabela,))
    return cur.fetchall()

def expressao(nome, tipo, tamanho, subtipo):
    if tipo in TIPO_TEXTO:
        return f'CAST("{nome}" AS VARCHAR({tamanho}) CHARACTER SET OCTETS) AS "{nome}"'
    if tipo == TIPO_BLOB and subtipo == 1:  # blob de texto (observações etc.)
        return f'CAST(SUBSTRING("{nome}" FROM 1 FOR 8000) AS VARCHAR(8000) CHARACTER SET OCTETS) AS "{nome}"'
    if tipo == TIPO_BLOB:                   # blob binário (fotos): inútil em CSV
        return None
    return f'"{nome}"'

def celula(v):
    if v is None:
        return ''
    if isinstance(v, bytes):
        return v.decode('cp1252', errors='replace').rstrip()
    if isinstance(v, (datetime.date, datetime.datetime)):
        return v.isoformat()
    if isinstance(v, decimal.Decimal):
        return str(v)
    return str(v)

for tabela in TABELAS:
    cols = colunas_da_tabela(tabela)
    nomes, exprs = [], []
    for nome, tipo, tamanho, subtipo in cols:
        e = expressao(nome, tipo, tamanho, subtipo)
        if e:
            nomes.append(nome)
            exprs.append(e)
    cur.execute(f'SELECT {", ".join(exprs)} FROM {tabela}')
    caminho = f'{SAIDA}/{tabela.lower()}.csv'
    n = 0
    with open(caminho, 'w', newline='', encoding='utf-8') as f:
        w = csv.writer(f)
        w.writerow(nomes)
        for linha in cur:
            w.writerow([celula(v) for v in linha])
            n += 1
    print(f'{tabela}: {n} linhas, {len(nomes)} colunas -> {caminho}')

con.close()
