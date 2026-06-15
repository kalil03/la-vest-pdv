with open('tools/import-set.py', 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "limpo(c.get('DATANASC'))" in line:
        lines[i] = line.replace("limpo(c.get('DATANASC'))", "data_apenas(c.get('DATANASC'))")
    
    # c_query_cpf values
    if "INSERT INTO cliente (nome, telefone, email, logradouro, numero, bairro, cidade, uf, cep, data_criacao, cpf, tipo, razao" in lines[i]:
        # The next line is the VALUES clause
        expected = "                VALUES (" + ", ".join(["%s"] * 47) + ")\n"
        lines[i+1] = expected
        
    # c_query_nocpf values
    if "INSERT INTO cliente (nome, telefone, email, logradouro, numero, bairro, cidade, uf, cep, data_criacao, tipo, razao" in lines[i]:
        expected = "                    VALUES (" + ", ".join(["%s"] * 46) + ")\"\"\", dados + extra_dados)\n"
        lines[i+1] = expected

with open('tools/import-set.py', 'w') as f:
    f.writelines(lines)
