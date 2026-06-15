import re

def process_file(filename, new_fields_code):
    with open(filename, 'r') as f:
        content = f.read()
    
    # Insert before the first getter
    first_getter_idx = content.find('public Long getId()')
    if first_getter_idx == -1:
        first_getter_idx = content.find('public void ')
    
    # generate getters and setters
    lines = new_fields_code.strip().split('\n')
    getters_setters = []
    for line in lines:
        line = line.strip()
        if not line or line.startswith('//') or line.startswith('@'): continue
        # private BigDecimal precoCusto;
        parts = line.replace(';', '').split()
        if len(parts) >= 3 and parts[0] == 'private':
            tipo = parts[1]
            nome = parts[2]
            Nome = nome[0].upper() + nome[1:]
            getters_setters.append(f'    public {tipo} get{Nome}() {{ return {nome}; }}')
            getters_setters.append(f'    public void set{Nome}({tipo} {nome}) {{ this.{nome} = {nome}; }}')
            
    insert_fields = new_fields_code + '\n\n'
    insert_methods = '\n'.join(getters_setters) + '\n'
    
    new_content = content[:first_getter_idx] + insert_fields + insert_methods + content[first_getter_idx:]
    
    # Add imports if necessary
    if 'import java.time.LocalDate;' not in new_content and 'LocalDate' in new_content:
        new_content = new_content.replace('import java.time.Instant;', 'import java.time.Instant;\nimport java.time.LocalDate;')
        
    with open(filename, 'w') as f:
        f.write(new_content)

produto_fields = """
    // -- Legado: Custos e margens --
    private BigDecimal precoCusto;
    private BigDecimal precoCompra;
    private BigDecimal pLucro;
    private BigDecimal pLucro2;
    private BigDecimal pLucro3;
    private BigDecimal vLucro;
    private BigDecimal vLucro2;
    private BigDecimal vLucro3;

    // -- Legado: Tabelas de Preço Múltiplas --
    private BigDecimal precoVenda2;
    private BigDecimal precoVenda3;
    private BigDecimal precoEspecial;

    // -- Legado: Estoque Avançado --
    private BigDecimal qtdeMin;
    private BigDecimal qtdeMax;
    private BigDecimal qtdeReservado;
    private BigDecimal qtdeProducao;

    // -- Legado: Hierarquia --
    private String grupo;
    private String subgrupo;

    // -- Legado: Códigos Auxiliares --
    private String refOrigi;
    private String codFabricante;

    // -- Legado: Flags e Configurações --
    private String controlaEstoque;
    private String situacao;
    private String prodProp;
    private String classe;
    private String prodEspecifico;

    // -- Legado: Fiscal --
    private String cstIcms;
    private String cbenef;

    // -- Legado: Auditoria --
    private Instant dataCad;
    private Instant dataAlt;
    @Column(name = "data_alt_ncm")
    private Instant dataAltNcm;
    private String usuarioCad;
    private String usuarioAlt;

    // -- Legado: Gerais --
    @Column(length = 2000)
    private String anota;
    private String unCompra;
"""

cliente_fields = """
    // -- Legado: Documentos e PJ --
    private String tipo;
    private String razao;
    private String fantasia;
    private String rg;
    private String ie;

    // -- Legado: Dados Pessoais/Demográficos --
    private LocalDate dataNasc;
    private String idade;
    private String sexo;

    // -- Legado: Familiares e Filiação --
    private String pfisNomePai;
    private String pfisNomeMae;
    private String pfisNomeConj;
    private String pfisEmpresaConj;
    private BigDecimal pfisRendaConj;
    private String pfisFoneConj;

    // -- Legado: Profissionais / Referência --
    private String pfisLocalTrab;
    private String pfisProfissao;
    private String refComerciais;

    // -- Legado: Crediário / Risco --
    private BigDecimal limiteCred;
    private String bloqueado;
    private Integer diaVencimento;

    // -- Legado: Contatos Extras --
    private String fone2;
    private String fone3;
    private String fone1Tipo;
    private String whatsFone1;

    // -- Legado: Endereço Adicional --
    private String complemento;
    private String entComplemento;

    // -- Legado: Observações --
    @Column(length = 5000)
    private String anotacoes;
    private String campo1;
    private String campo2;

    // -- Legado: Vínculos de Venda --
    private String tabPreco1;
    private String tabPreco2;
    private String tabPreco3;

    // -- Legado: Auditoria --
    private Instant dataCad;
    private Instant dataAlt;
    private String usuarioCad;
    private String usuarioAlt;
"""

process_file('src/main/java/br/com/loja/pdv/domain/Produto.java', produto_fields)
process_file('src/main/java/br/com/loja/pdv/domain/Cliente.java', cliente_fields)
