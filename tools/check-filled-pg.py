import psycopg2
import sys

DSN = dict(host='localhost', port=5433, dbname='pdv', user='pdv', password='pdv')

def main():
    con = psycopg2.connect(**DSN)
    cur = con.cursor()
    
    # Pegar todas as tabelas publicas
    cur.execute("""
        SELECT table_name 
        FROM information_schema.tables 
        WHERE table_schema = 'public' 
          AND table_type = 'BASE TABLE'
          AND table_name != 'flyway_schema_history'
        ORDER BY table_name;
    """)
    tabelas = [r[0] for r in cur.fetchall()]
    
    for tabela in tabelas:
        cur.execute(f"""
            SELECT column_name, data_type 
            FROM information_schema.columns 
            WHERE table_name = '{tabela}'
            ORDER BY ordinal_position;
        """)
        colunas = cur.fetchall()
        
        preenchidas = []
        for col_name, data_type in colunas:
            # Query para checar se tem algum valor preenchido
            if 'char' in data_type or 'text' in data_type:
                query = f'SELECT 1 FROM "{tabela}" WHERE "{col_name}" IS NOT NULL AND "{col_name}" != \'\' LIMIT 1'
            else:
                query = f'SELECT 1 FROM "{tabela}" WHERE "{col_name}" IS NOT NULL LIMIT 1'
            
            try:
                cur.execute(query)
                if cur.fetchone():
                    preenchidas.append(col_name)
            except Exception as e:
                con.rollback()
                
        if preenchidas:
            print(f"\n=== Tabela: {tabela.upper()} ===")
            print(", ".join(preenchidas))
            
    con.close()

if __name__ == '__main__':
    main()
