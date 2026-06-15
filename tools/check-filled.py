import sys
from firebird.driver import connect, driver_config

driver_config.server_defaults.host.value = '172.17.0.3'
driver_config.server_defaults.port.value = '3050'
con = connect('172.17.0.3:/firebird/data/DADOS.FDB', user='SYSDBA', password='masterkey', charset='UTF8')
cur = con.cursor()

def colunas_preenchidas(tabela):
    # Pega os nomes das colunas
    cur.execute("""
        SELECT TRIM(rf.rdb$field_name)
        FROM rdb$relation_fields rf
        WHERE rf.rdb$relation_name = ?
        ORDER BY rf.rdb$field_position""", (tabela,))
    colunas = [r[0] for r in cur.fetchall()]
    
    # Query para pegar todos os dados (12k produtos não é muito pra RAM)
    # Como pode haver erro de decoding (WIN1251 vs WIN1252), usamos um select simples
    # Mas como só queremos saber se é nulo, podemos fazer via SQL:
    
    preenchidas = []
    for col in colunas:
        # Pega 1 registro onde a coluna não é nula nem string vazia
        # CAST para varchar para poder comparar com ''
        try:
            cur.execute(f'SELECT FIRST 1 1 FROM {tabela} WHERE "{col}" IS NOT NULL AND CAST("{col}" AS VARCHAR(1000) CHARACTER SET OCTETS) != \'\'')
            if cur.fetchone():
                preenchidas.append(col)
        except Exception as e:
            # Em caso de erro de cast (ex: blob binario), verifica só IS NOT NULL
            try:
                con.rollback()
                cur.execute(f'SELECT FIRST 1 1 FROM {tabela} WHERE "{col}" IS NOT NULL')
                if cur.fetchone():
                    preenchidas.append(col)
            except Exception as e2:
                con.rollback()
                pass
    return preenchidas

print("=== PRODUTOS ===")
print(colunas_preenchidas('PRODUTOS'))
print("\n=== CLIENTES ===")
print(colunas_preenchidas('CLIENTES'))

con.close()
