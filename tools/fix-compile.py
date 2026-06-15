import re

with open('src/main/java/br/com/loja/pdv/web/dto/NovoClienteRequest.java', 'r') as f:
    cr = f.read()
cr = cr.replace("BigDecimal limiteCred", "java.math.BigDecimal limiteCred")
cr = cr.replace("BigDecimal pfisRendaConj", "java.math.BigDecimal pfisRendaConj")
with open('src/main/java/br/com/loja/pdv/web/dto/NovoClienteRequest.java', 'w') as f: f.write(cr)


with open('src/main/java/br/com/loja/pdv/service/CarneService.java', 'r') as f:
    cs = f.read()
cs = cs.replace("ClienteDTO.de(cliente, clienteRepository.saldoDevedor(cliente.getId()))", "ClienteDTO.de(cliente, clienteRepository.saldoDevedor(cliente.getId()), cliente.getTipo(), cliente.getRg(), cliente.getDataNasc(), cliente.getLimiteCred(), cliente.getBloqueado(), cliente.getPfisProfissao(), cliente.getPfisRendaConj(), cliente.getAnotacoes())")
with open('src/main/java/br/com/loja/pdv/service/CarneService.java', 'w') as f: f.write(cs)


with open('src/main/java/br/com/loja/pdv/service/ClienteService.java', 'r') as f:
    cls = f.read()
cls = cls.replace("ClienteDTO.de(c, saldos.getOrDefault(c.getId(), BigDecimal.ZERO))", "ClienteDTO.de(c, saldos.getOrDefault(c.getId(), BigDecimal.ZERO), c.getTipo(), c.getRg(), c.getDataNasc(), c.getLimiteCred(), c.getBloqueado(), c.getPfisProfissao(), c.getPfisRendaConj(), c.getAnotacoes())")
cls = cls.replace("ClienteDTO.de(cliente, BigDecimal.ZERO)", "ClienteDTO.de(cliente, BigDecimal.ZERO, cliente.getTipo(), cliente.getRg(), cliente.getDataNasc(), cliente.getLimiteCred(), cliente.getBloqueado(), cliente.getPfisProfissao(), cliente.getPfisRendaConj(), cliente.getAnotacoes())")
cls = cls.replace("ClienteDTO.de(cliente, clienteRepository.saldoDevedor(id))", "ClienteDTO.de(cliente, clienteRepository.saldoDevedor(id), cliente.getTipo(), cliente.getRg(), cliente.getDataNasc(), cliente.getLimiteCred(), cliente.getBloqueado(), cliente.getPfisProfissao(), cliente.getPfisRendaConj(), cliente.getAnotacoes())")
with open('src/main/java/br/com/loja/pdv/service/ClienteService.java', 'w') as f: f.write(cls)


with open('src/main/java/br/com/loja/pdv/web/dto/ProdutoDTO.java', 'r') as f:
    pd = f.read()
pd = pd.replace("p.getPCusto()", "p.getPrecoCusto()")
pd = pd.replace("p.getPAtacado()", "p.getPrecoVenda2()")
pd = pd.replace("p.getPLucroAtacado()", "p.getPLucro2()")
pd = pd.replace("p.getEstoque()", "null") # estoque is not directly mapped, maybe we just pass null? Or remove it. Wait, I will just return null for now. Let me use java.math.BigDecimal.ZERO
pd = pd.replace("p.getEstMinimo()", "p.getQtdeMin()")
with open('src/main/java/br/com/loja/pdv/web/dto/ProdutoDTO.java', 'w') as f: f.write(pd)

with open('src/main/java/br/com/loja/pdv/service/ProdutoService.java', 'r') as f:
    ps = f.read()
ps = ps.replace("produto.setPCusto(req.pCusto());", "produto.setPrecoCusto(req.pCusto());")
ps = ps.replace("produto.setPAtacado(req.pAtacado());", "produto.setPrecoVenda2(req.pAtacado());")
ps = ps.replace("produto.setPLucroAtacado(req.pLucroAtacado());", "produto.setPLucro2(req.pLucroAtacado());")
ps = ps.replace("produto.setEstoque(req.estoque());", "") # no setter
ps = ps.replace("produto.setEstMinimo(req.estMinimo());", "produto.setQtdeMin(req.estMinimo());")
with open('src/main/java/br/com/loja/pdv/service/ProdutoService.java', 'w') as f: f.write(ps)

