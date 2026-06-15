import re

def add_columns(filename, fields):
    with open(filename, 'r') as f:
        content = f.read()
    
    for field, colname in fields.items():
        # Find the line with the field declaration
        pattern = r'(    private \w+ ' + field + r';)'
        replacement = f'    @Column(name = "{colname}")\n\\1'
        content = re.sub(pattern, replacement, content)
        
    with open(filename, 'w') as f:
        f.write(content)

p_fields = {
    'pLucro2': 'p_lucro2', 'pLucro3': 'p_lucro3',
    'vLucro2': 'v_lucro2', 'vLucro3': 'v_lucro3',
    'precoVenda2': 'preco_venda2', 'precoVenda3': 'preco_venda3'
}

c_fields = {
    'fone1Tipo': 'fone1_tipo',
    'whatsFone1': 'whats_fone1',
    'tabPreco1': 'tab_preco1',
    'tabPreco2': 'tab_preco2',
    'tabPreco3': 'tab_preco3',
    'fone2': 'fone2',
    'fone3': 'fone3',
    'campo1': 'campo1',
    'campo2': 'campo2'
}

add_columns('src/main/java/br/com/loja/pdv/domain/Produto.java', p_fields)
add_columns('src/main/java/br/com/loja/pdv/domain/Cliente.java', c_fields)
