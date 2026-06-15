import os

# CLIENTE SERVICE
with open('src/main/java/br/com/loja/pdv/service/ClienteService.java', 'r') as f:
    cs = f.read()

cs = cs.replace("private static String limpar", """
    @Transactional(readOnly = true)
    public Cliente buscarCompleto(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new RegraNegocioException("Cliente não encontrado (id " + id + ")"));
    }

    @Transactional
    public Cliente salvarCompleto(Cliente cliente) {
        if (cliente.getId() != null && cliente.getId() <= 0) cliente.setId(null);
        if (cliente.getDataCriacao() == null) cliente.setDataCriacao(java.time.Instant.now());
        return clienteRepository.save(cliente);
    }

    private static String limpar""")
with open('src/main/java/br/com/loja/pdv/service/ClienteService.java', 'w') as f:
    f.write(cs)


# CLIENTE CONTROLLER
with open('src/main/java/br/com/loja/pdv/web/ClienteController.java', 'r') as f:
    cc = f.read()

cc = cc.replace("public ScoreCliente score", """
    @GetMapping("/completo/{id}")
    public br.com.loja.pdv.domain.Cliente buscarCompleto(@PathVariable Long id) {
        return clienteService.buscarCompleto(id);
    }

    @PostMapping("/completo")
    @ResponseStatus(HttpStatus.CREATED)
    public br.com.loja.pdv.domain.Cliente salvarCompleto(@RequestBody br.com.loja.pdv.domain.Cliente cliente) {
        return clienteService.salvarCompleto(cliente);
    }

    @PutMapping("/completo/{id}")
    public br.com.loja.pdv.domain.Cliente atualizarCompleto(@PathVariable Long id, @RequestBody br.com.loja.pdv.domain.Cliente cliente) {
        cliente.setId(id);
        return clienteService.salvarCompleto(cliente);
    }

    @GetMapping("/{id}/score")
    public ScoreCliente score""")
with open('src/main/java/br/com/loja/pdv/web/ClienteController.java', 'w') as f:
    f.write(cc)


# PRODUTO SERVICE
with open('src/main/java/br/com/loja/pdv/service/ProdutoService.java', 'r') as f:
    ps = f.read()

ps = ps.replace("private static String limpar", """
    @Transactional(readOnly = true)
    public Produto buscarCompleto(Long id) {
        return produtoRepository.findById(id).orElseThrow(() -> new RegraNegocioException("Produto não encontrado (id " + id + ")"));
    }

    @Transactional
    public Produto salvarCompleto(Produto produto) {
        if (produto.getId() != null && produto.getId() <= 0) produto.setId(null);
        if (produto.getDataCriacao() == null) produto.setDataCriacao(java.time.Instant.now());
        if (produto.getCodigo() == null || produto.getCodigo().isBlank()) produto.setCodigo(String.valueOf(produtoRepository.proximoCodigoGerado()));
        return produtoRepository.save(produto);
    }

    private static String limpar""")
with open('src/main/java/br/com/loja/pdv/service/ProdutoService.java', 'w') as f:
    f.write(ps)


# PRODUTO CONTROLLER
with open('src/main/java/br/com/loja/pdv/web/ProdutoController.java', 'r') as f:
    pc = f.read()

pc = pc.replace("public ProdutoDTO atualizar", """
    @GetMapping("/completo/{id}")
    public br.com.loja.pdv.domain.Produto buscarCompleto(@PathVariable Long id) {
        return produtoService.buscarCompleto(id);
    }

    @PostMapping("/completo")
    @ResponseStatus(HttpStatus.CREATED)
    public br.com.loja.pdv.domain.Produto salvarCompleto(@RequestBody br.com.loja.pdv.domain.Produto produto) {
        return produtoService.salvarCompleto(produto);
    }

    @PutMapping("/completo/{id}")
    public br.com.loja.pdv.domain.Produto atualizarCompleto(@PathVariable Long id, @RequestBody br.com.loja.pdv.domain.Produto produto) {
        produto.setId(id);
        return produtoService.salvarCompleto(produto);
    }

    @PutMapping("/{id}")
    public ProdutoDTO atualizar""")
with open('src/main/java/br/com/loja/pdv/web/ProdutoController.java', 'w') as f:
    f.write(pc)
