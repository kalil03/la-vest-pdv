with open('tools/import-set.py', 'r') as f:
    c = f.read()

c = c.replace(
    'INSERT INTO produto (codigo, nome, categoria, marca_id, preco, unidade, codigo_barras, data_criacao, ',
    'INSERT INTO produto (codigo, nome, categoria, marca_id, preco, unidade, codigo_barras, data_criacao, ncm, cest, '
)

c = c.replace(
    'VALUES (%s, %s, %s, %s, %s, %s, %s, %s, ',
    'VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, '
)

c = c.replace(
    'ON CONFLICT (codigo) DO UPDATE SET\n                nome = EXCLUDED.nome,',
    'ON CONFLICT (codigo) DO UPDATE SET\n                ncm = EXCLUDED.ncm, cest = EXCLUDED.cest, nome = EXCLUDED.nome,'
)

c = c.replace(
    "data_ou_agora(p.get('DATACAD')), numero(p.get('PRECOCUSTO'))",
    "data_ou_agora(p.get('DATACAD')), limpo(p.get('NCMSH')), limpo(p.get('CEST')), numero(p.get('PRECOCUSTO'))"
)

with open('tools/import-set.py', 'w') as f:
    f.write(c)
