import argparse
import csv
import os
import sys
from datetime import datetime

import psycopg2

DSN = dict(host='localhost', port=5433, dbname='pdv', user='pdv', password='pdv')
MARCAS_LIXO = {'', 'SEM MARCA', 'SEM-MARCA', 'S/MARCA', 'S/ MARCA'}

def limpo(s): return (s or '').strip() or None
def so_digitos(s): d = ''.join(c for c in (s or '') if c.isdigit()); return d if d else None
def numero(s, padrao=0):
    try: return float(s)
    except: return padrao
def data_ou_agora(s):
    try: return datetime.fromisoformat(s.strip())
    except: return None # datetime.now() was used for DATACAD, but let's let DB defaults or return None if parsing fails. Actually return datetime.now() for created_at
def data_apenas(s):
    try: return s.strip()[:10] if s else None
    except: return None

def importar_marcas(cur, produtos):
    nomes = {}
    for p in produtos:
        m = ' '.join((p.get('MARCA') or '').split())
        if m.upper() not in MARCAS_LIXO: nomes.setdefault(m.upper(), m)
    mapa, criadas = {}, 0
    for chave, grafia in nomes.items():
        cur.execute('SELECT id FROM marca WHERE UPPER(nome) = %s', (chave,))
        linha = cur.fetchone()
        if linha: mapa[chave] = linha[0]
        else:
            cur.execute('INSERT INTO marca (nome) VALUES (%s) RETURNING id', (grafia,))
            mapa[chave] = cur.fetchone()[0]
            criadas += 1
    return mapa

def importar_produtos(cur, produtos, marcas):
    inseridos = atualizados = variacoes = 0
    for p in produtos:
        codigo = limpo(p['NPROD'])
        if not codigo: continue
        nome = limpo(p['DESCRICAO']) or f'SEM DESCRICAO ({codigo})'
        marca_chave = ' '.join((p.get('MARCA') or '').split()).upper()
        
        # Helper pra datas
        d_cad = data_ou_agora(p.get('DATACAD')) or datetime.now()
        
        cur.execute("""INSERT INTO produto (codigo, nome, categoria, marca_id, preco, unidade, codigo_barras, data_criacao, ncm, cest, preco_custo, preco_compra, p_lucro, p_lucro2, p_lucro3, v_lucro, v_lucro2, v_lucro3, preco_venda2, preco_venda3, preco_especial, qtde_min, qtde_max, qtde_reservado, qtde_producao, grupo, subgrupo, ref_origi, cod_fabricante, controla_estoque, situacao, prod_prop, classe, prod_especifico, cst_icms, cbenef, data_cad, data_alt, data_alt_ncm, usuario_cad, usuario_alt, anota, un_compra)
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
            ON CONFLICT (codigo) DO UPDATE SET
                ncm = EXCLUDED.ncm, cest = EXCLUDED.cest, nome = EXCLUDED.nome,
                categoria = EXCLUDED.categoria,
                marca_id = EXCLUDED.marca_id,
                preco = EXCLUDED.preco,
                unidade = EXCLUDED.unidade,
                codigo_barras = EXCLUDED.codigo_barras,
                preco_custo = EXCLUDED.preco_custo, preco_compra = EXCLUDED.preco_compra, p_lucro = EXCLUDED.p_lucro, p_lucro2 = EXCLUDED.p_lucro2, p_lucro3 = EXCLUDED.p_lucro3, v_lucro = EXCLUDED.v_lucro, v_lucro2 = EXCLUDED.v_lucro2, v_lucro3 = EXCLUDED.v_lucro3, preco_venda2 = EXCLUDED.preco_venda2, preco_venda3 = EXCLUDED.preco_venda3, preco_especial = EXCLUDED.preco_especial, qtde_min = EXCLUDED.qtde_min, qtde_max = EXCLUDED.qtde_max, qtde_reservado = EXCLUDED.qtde_reservado, qtde_producao = EXCLUDED.qtde_producao, grupo = EXCLUDED.grupo, subgrupo = EXCLUDED.subgrupo, ref_origi = EXCLUDED.ref_origi, cod_fabricante = EXCLUDED.cod_fabricante, controla_estoque = EXCLUDED.controla_estoque, situacao = EXCLUDED.situacao, prod_prop = EXCLUDED.prod_prop, classe = EXCLUDED.classe, prod_especifico = EXCLUDED.prod_especifico, cst_icms = EXCLUDED.cst_icms, cbenef = EXCLUDED.cbenef, data_cad = EXCLUDED.data_cad, data_alt = EXCLUDED.data_alt, data_alt_ncm = EXCLUDED.data_alt_ncm, usuario_cad = EXCLUDED.usuario_cad, usuario_alt = EXCLUDED.usuario_alt, anota = EXCLUDED.anota, un_compra = EXCLUDED.un_compra
            RETURNING id, (xmax = 0) AS inserido""", (codigo, nome, limpo(p['GRUPO']), marcas.get(marca_chave), max(numero(p['PRECOVENDA']), 0), limpo(p['UNVENDA']) or 'UN', limpo(p['CODBARRAS']), data_ou_agora(p.get('DATACAD')), limpo(p.get('NCMSH')), limpo(p.get('CEST')), numero(p.get('PRECOCUSTO')), numero(p.get('PRECOCOMPRA')), numero(p.get('PLUCRO')), numero(p.get('PLUCRO2')), numero(p.get('PLUCRO3')), numero(p.get('VLUCRO')), numero(p.get('VLUCRO2')), numero(p.get('VLUCRO3')), numero(p.get('PRECOVENDA2')), numero(p.get('PRECOVENDA3')), numero(p.get('PRECO_ESPECIAL')), numero(p.get('QTDEMIN')), numero(p.get('QTDEMAX')), numero(p.get('QTDE_RESERVADO')), numero(p.get('QTDEPRODUCAO')), limpo(p.get('GRUPO')), limpo(p.get('SUBGRUPO')), limpo(p.get('REFORIGI')), limpo(p.get('COD_FABRICANTE')), limpo(p.get('CONTROLAESTOQUE')), limpo(p.get('SITUACAO')), limpo(p.get('PRODPROP')), limpo(p.get('CLASSE')), limpo(p.get('PRODESPECIFICO')), limpo(p.get('CSTICMS')), limpo(p.get('CBENEF')), data_ou_agora(p.get('DATACAD')), data_ou_agora(p.get('DATAALT')), data_ou_agora(p.get('DATAALT_NCM')), limpo(p.get('USUARIOCAD')), limpo(p.get('USUARIOALT')), limpo(p.get('ANOTA')), limpo(p.get('UNCOMPRA'))))
        produto_id, inserido = cur.fetchone()
        if inserido: inseridos += 1
        else: atualizados += 1

        cur.execute("INSERT INTO variacao (produto_id, tamanho, cor, estoque) VALUES (%s, NULL, NULL, %s) ON CONFLICT (produto_id, tamanho, cor) DO NOTHING", (produto_id, round(numero(p.get('QTDEDISP')))))
        variacoes += cur.rowcount
    print(f'produtos: {inseridos} inseridos, {atualizados} atualizados')

def importar_clientes(cur, clientes):
    inseridos = atualizados = 0
    for c in clientes:
        nome = limpo(c['RAZAO'])
        if not nome: continue
        cpf = so_digitos(c['CNPJCPF'])
        
        d_cad = data_ou_agora(c.get('DATACAD')) or datetime.now()
        tel = limpo(c.get('FONE1')) or limpo(c.get('FONE3')) or limpo(c.get('FONE2')) or limpo(c.get('WHATSFONE1'))
        dados = (nome, tel, limpo(c.get('EMAIL1')), limpo(c.get('ENDERECO')), limpo(c.get('NUMERO')), limpo(c.get('BAIRRO')), limpo(c.get('CIDADE')), limpo(c.get('UF')), so_digitos(c.get('CEP')), d_cad)
        
        extra_dados = (limpo(c.get('TIPO')), limpo(c.get('RAZAO')), limpo(c.get('FANTASIA')), limpo(c.get('RG')), limpo(c.get('IE')), data_apenas(c.get('DATANASC')), limpo(c.get('IDADE')), limpo(c.get('SEXO')), limpo(c.get('PFIS_NOMEPAI')), limpo(c.get('PFIS_NOMEMAE')), limpo(c.get('PFIS_NOMECONJ')), limpo(c.get('PFIS_EMPRESA_CONJ')), numero(c.get('PFIS_RENDA_CONJ')), limpo(c.get('PFIS_FONE_CONJ')), limpo(c.get('PFIS_LOCALTRAB')), limpo(c.get('PFIS_PROFISSAO')), limpo(c.get('REFCOMERCIAIS')), numero(c.get('LIMITECRED')), limpo(c.get('BLOQUEADO')), numero(c.get('DIA_VENCIMENTO')), limpo(c.get('FONE2')), limpo(c.get('FONE3')), limpo(c.get('FONE1TIPO')), limpo(c.get('WHATSFONE1')), limpo(c.get('COMPLEMENTO')), limpo(c.get('ENT_COMPLEMENTO')), limpo(c.get('ANOTACOES')), limpo(c.get('CAMPO1')), limpo(c.get('CAMPO2')), limpo(c.get('TABPRECO1')), limpo(c.get('TABPRECO2')), limpo(c.get('TABPRECO3')), data_ou_agora(c.get('DATACAD')), data_ou_agora(c.get('DATAALT')), limpo(c.get('USUARIOCAD')), limpo(c.get('USUARIOALT')),)
        
        if cpf:
            cur.execute("""INSERT INTO cliente (nome, telefone, email, logradouro, numero, bairro, cidade, uf, cep, data_criacao, cpf, tipo, razao, fantasia, rg, ie, data_nasc, idade, sexo, pfis_nome_pai, pfis_nome_mae, pfis_nome_conj, pfis_empresa_conj, pfis_renda_conj, pfis_fone_conj, pfis_local_trab, pfis_profissao, ref_comerciais, limite_cred, bloqueado, dia_vencimento, fone2, fone3, fone1_tipo, whats_fone1, complemento, ent_complemento, anotacoes, campo1, campo2, tab_preco1, tab_preco2, tab_preco3, data_cad, data_alt, usuario_cad, usuario_alt)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (cpf) DO UPDATE SET
                    nome = EXCLUDED.nome, telefone = EXCLUDED.telefone,
                    email = EXCLUDED.email, logradouro = EXCLUDED.logradouro,
                    numero = EXCLUDED.numero, bairro = EXCLUDED.bairro,
                    cidade = EXCLUDED.cidade, uf = EXCLUDED.uf, cep = EXCLUDED.cep,
                    tipo = EXCLUDED.tipo, razao = EXCLUDED.razao, fantasia = EXCLUDED.fantasia, rg = EXCLUDED.rg, ie = EXCLUDED.ie, data_nasc = EXCLUDED.data_nasc, idade = EXCLUDED.idade, sexo = EXCLUDED.sexo, pfis_nome_pai = EXCLUDED.pfis_nome_pai, pfis_nome_mae = EXCLUDED.pfis_nome_mae, pfis_nome_conj = EXCLUDED.pfis_nome_conj, pfis_empresa_conj = EXCLUDED.pfis_empresa_conj, pfis_renda_conj = EXCLUDED.pfis_renda_conj, pfis_fone_conj = EXCLUDED.pfis_fone_conj, pfis_local_trab = EXCLUDED.pfis_local_trab, pfis_profissao = EXCLUDED.pfis_profissao, ref_comerciais = EXCLUDED.ref_comerciais, limite_cred = EXCLUDED.limite_cred, bloqueado = EXCLUDED.bloqueado, dia_vencimento = EXCLUDED.dia_vencimento, fone2 = EXCLUDED.fone2, fone3 = EXCLUDED.fone3, fone1_tipo = EXCLUDED.fone1_tipo, whats_fone1 = EXCLUDED.whats_fone1, complemento = EXCLUDED.complemento, ent_complemento = EXCLUDED.ent_complemento, anotacoes = EXCLUDED.anotacoes, campo1 = EXCLUDED.campo1, campo2 = EXCLUDED.campo2, tab_preco1 = EXCLUDED.tab_preco1, tab_preco2 = EXCLUDED.tab_preco2, tab_preco3 = EXCLUDED.tab_preco3, data_cad = EXCLUDED.data_cad, data_alt = EXCLUDED.data_alt, usuario_cad = EXCLUDED.usuario_cad, usuario_alt = EXCLUDED.usuario_alt
                RETURNING (xmax = 0)""", dados + (cpf,) + extra_dados)
            if cur.fetchone()[0]: inseridos += 1
            else: atualizados += 1
        else:
            cur.execute('SELECT id FROM cliente WHERE cpf IS NULL AND nome = %s', (nome,))
            existente = cur.fetchone()
            if existente:
                cur.execute("""UPDATE cliente SET telefone=%s, email=%s, logradouro=%s, numero=%s, bairro=%s, cidade=%s, uf=%s, cep=%s, tipo=%s, razao=%s, fantasia=%s, rg=%s, ie=%s, data_nasc=%s, idade=%s, sexo=%s, pfis_nome_pai=%s, pfis_nome_mae=%s, pfis_nome_conj=%s, pfis_empresa_conj=%s, pfis_renda_conj=%s, pfis_fone_conj=%s, pfis_local_trab=%s, pfis_profissao=%s, ref_comerciais=%s, limite_cred=%s, bloqueado=%s, dia_vencimento=%s, fone2=%s, fone3=%s, fone1_tipo=%s, whats_fone1=%s, complemento=%s, ent_complemento=%s, anotacoes=%s, campo1=%s, campo2=%s, tab_preco1=%s, tab_preco2=%s, tab_preco3=%s, data_cad=%s, data_alt=%s, usuario_cad=%s, usuario_alt=%s WHERE id=%s""", dados[1:9] + extra_dados + (existente[0],))
                atualizados += 1
            else:
                cur.execute("""INSERT INTO cliente (nome, telefone, email, logradouro, numero, bairro, cidade, uf, cep, data_criacao, tipo, razao, fantasia, rg, ie, data_nasc, idade, sexo, pfis_nome_pai, pfis_nome_mae, pfis_nome_conj, pfis_empresa_conj, pfis_renda_conj, pfis_fone_conj, pfis_local_trab, pfis_profissao, ref_comerciais, limite_cred, bloqueado, dia_vencimento, fone2, fone3, fone1_tipo, whats_fone1, complemento, ent_complemento, anotacoes, campo1, campo2, tab_preco1, tab_preco2, tab_preco3, data_cad, data_alt, usuario_cad, usuario_alt)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""", dados + extra_dados)
                inseridos += 1
    print(f'clientes: {inseridos} inseridos, {atualizados} atualizados')

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--dir', default=os.path.expanduser('~/Downloads/export-set'))
    pasta = ap.parse_args().dir
    with open(f'{pasta}/produtos.csv', encoding='utf-8') as f: produtos = list(csv.DictReader(f))
    with open(f'{pasta}/clientes.csv', encoding='utf-8') as f: clientes = list(csv.DictReader(f))
    con = psycopg2.connect(**DSN)
    try:
        with con:
            with con.cursor() as cur:
                marcas = importar_marcas(cur, produtos)
                importar_produtos(cur, produtos, marcas)
                importar_clientes(cur, clientes)
    finally:
        con.close()

if __name__ == '__main__':
    sys.exit(main())
