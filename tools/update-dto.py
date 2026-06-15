import re

# Produto
with open('src/main/java/br/com/loja/pdv/web/dto/NovoProdutoRequest.java', 'r') as f:
    pr = f.read()

if "BigDecimal pCusto" not in pr:
    pr = pr.replace("Integer origem,", "Integer origem,\n        BigDecimal pCusto,\n        BigDecimal pLucro,\n        BigDecimal pAtacado,\n        BigDecimal pLucroAtacado,\n        BigDecimal estoque,\n        BigDecimal estMinimo,")
    with open('src/main/java/br/com/loja/pdv/web/dto/NovoProdutoRequest.java', 'w') as f: f.write(pr)

with open('src/main/java/br/com/loja/pdv/web/dto/ProdutoDTO.java', 'r') as f:
    pd = f.read()

if "BigDecimal pCusto" not in pd:
    pd = pd.replace("Integer origem,", "Integer origem,\n        BigDecimal pCusto,\n        BigDecimal pLucro,\n        BigDecimal pAtacado,\n        BigDecimal pLucroAtacado,\n        BigDecimal estoque,\n        BigDecimal estMinimo,")
    pd = pd.replace("p.getOrigem(),", "p.getOrigem(),\n                p.getPCusto(), p.getPLucro(), p.getPAtacado(), p.getPLucroAtacado(), p.getEstoque(), p.getEstMinimo(),")
    with open('src/main/java/br/com/loja/pdv/web/dto/ProdutoDTO.java', 'w') as f: f.write(pd)

with open('src/main/java/br/com/loja/pdv/service/ProdutoService.java', 'r') as f:
    ps = f.read()

if "produto.setPCusto(req.pCusto());" not in ps:
    ps = ps.replace("produto.setOrigem(req.origem() != null ? req.origem() : 0);", "produto.setOrigem(req.origem() != null ? req.origem() : 0);\n        produto.setPCusto(req.pCusto());\n        produto.setPLucro(req.pLucro());\n        produto.setPAtacado(req.pAtacado());\n        produto.setPLucroAtacado(req.pLucroAtacado());\n        produto.setEstoque(req.estoque());\n        produto.setEstMinimo(req.estMinimo());")
    with open('src/main/java/br/com/loja/pdv/service/ProdutoService.java', 'w') as f: f.write(ps)

# Cliente
with open('src/main/java/br/com/loja/pdv/web/dto/NovoClienteRequest.java', 'r') as f:
    cr = f.read()

if "String tipo" not in cr:
    cr = cr.replace("String cep", "String cep,\n        String tipo,\n        String rg,\n        java.time.LocalDate dataNasc,\n        BigDecimal limiteCred,\n        String bloqueado,\n        String pfisProfissao,\n        BigDecimal pfisRendaConj,\n        String anotacoes")
    with open('src/main/java/br/com/loja/pdv/web/dto/NovoClienteRequest.java', 'w') as f: f.write(cr)

with open('src/main/java/br/com/loja/pdv/web/dto/ClienteDTO.java', 'r') as f:
    cd = f.read()

if "String tipo" not in cd:
    cd = cd.replace("BigDecimal saldoDevedor", "BigDecimal saldoDevedor,\n        String tipo,\n        String rg,\n        java.time.LocalDate dataNasc,\n        BigDecimal limiteCred,\n        String bloqueado,\n        String pfisProfissao,\n        BigDecimal pfisRendaConj,\n        String anotacoes")
    cd = cd.replace("saldoDevedor)", "saldoDevedor,\n                c.getTipo(), c.getRg(), c.getDataNasc(), c.getLimiteCred(), c.getBloqueado(), c.getPfisProfissao(), c.getPfisRendaConj(), c.getAnotacoes())")
    with open('src/main/java/br/com/loja/pdv/web/dto/ClienteDTO.java', 'w') as f: f.write(cd)

with open('src/main/java/br/com/loja/pdv/service/ClienteService.java', 'r') as f:
    cs = f.read()

if "cliente.setTipo(limpar(req.tipo()));" not in cs:
    cs = cs.replace("cliente.setCep(limpar(req.cep()));", "cliente.setCep(limpar(req.cep()));\n        cliente.setTipo(limpar(req.tipo()));\n        cliente.setRg(limpar(req.rg()));\n        cliente.setDataNasc(req.dataNasc());\n        cliente.setLimiteCred(req.limiteCred());\n        cliente.setBloqueado(limpar(req.bloqueado()));\n        cliente.setPfisProfissao(limpar(req.pfisProfissao()));\n        cliente.setPfisRendaConj(req.pfisRendaConj());\n        cliente.setAnotacoes(limpar(req.anotacoes()));")
    with open('src/main/java/br/com/loja/pdv/service/ClienteService.java', 'w') as f: f.write(cs)
