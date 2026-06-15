import psycopg2

q1 = """INSERT INTO cliente (nome, telefone, email, logradouro, numero, bairro, cidade, uf, cep, data_criacao, cpf, tipo, razao, fantasia, rg, ie, data_nasc, idade, sexo, pfis_nome_pai, pfis_nome_mae, pfis_nome_conj, pfis_empresa_conj, pfis_renda_conj, pfis_fone_conj, pfis_local_trab, pfis_profissao, ref_comerciais, limite_cred, bloqueado, dia_vencimento, fone2, fone3, fone1_tipo, whats_fone1, complemento, ent_complemento, anotacoes, campo1, campo2, tab_preco1, tab_preco2, tab_preco3, data_cad, data_alt, usuario_cad, usuario_alt)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                ON CONFLICT (cpf) DO UPDATE SET
                    nome = EXCLUDED.nome"""

print("Count of %s in q1:", q1.count('%s'))
