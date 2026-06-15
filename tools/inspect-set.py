import sys
from firebird.driver import connect, driver_config

driver_config.server_defaults.host.value = '172.17.0.3'
driver_config.server_defaults.port.value = '3050'
con = connect('172.17.0.3:/firebird/data/DADOS.FDB', user='SYSDBA', password='masterkey', charset='UTF8')
cur = con.cursor()

def colunas(tabela):
    cur.execute("""
        SELECT TRIM(rf.rdb$field_name)
        FROM rdb$relation_fields rf
        WHERE rf.rdb$relation_name = ?
        ORDER BY rf.rdb$field_position""", (tabela,))
    return [r[0] for r in cur.fetchall()]

print("Colunas de PRODUTOS:")
print(colunas('PRODUTOS'))
print("\nColunas de CLIENTES:")
print(colunas('CLIENTES'))

con.close()
