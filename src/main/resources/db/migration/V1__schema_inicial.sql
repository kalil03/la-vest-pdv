-- Busca de produto sem sensibilidade a acento ("Tenis" encontra "Tênis")
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Codigos gerados para produtos novos sem codigo proprio.
-- Comeca em 100000 para nao colidir com os codigos legados do SET
-- (ajustar o START na sessao de importacao do Firebird, se preciso).
CREATE SEQUENCE produto_codigo_seq START WITH 100000;

CREATE TABLE produto (
    id        BIGSERIAL PRIMARY KEY,
    codigo    TEXT NOT NULL UNIQUE,
    nome      TEXT NOT NULL,
    categoria TEXT,
    preco     NUMERIC(10,2) NOT NULL CHECK (preco >= 0)
);

-- O estoque vive SEMPRE aqui, nunca no produto.
-- Produto sem grade (perfume) tem uma unica variacao com tamanho/cor nulos.
CREATE TABLE variacao (
    id         BIGSERIAL PRIMARY KEY,
    produto_id BIGINT NOT NULL REFERENCES produto (id),
    tamanho    TEXT,
    cor        TEXT,
    estoque    INTEGER NOT NULL DEFAULT 0,
    -- NULLS NOT DISTINCT: impede duas variacoes "padrao" (tamanho/cor nulos) no mesmo produto
    UNIQUE NULLS NOT DISTINCT (produto_id, tamanho, cor)
);

CREATE TABLE cliente (
    id       BIGSERIAL PRIMARY KEY,
    nome     TEXT NOT NULL,
    cpf      TEXT UNIQUE,
    telefone TEXT
);

CREATE TABLE venda (
    id              BIGSERIAL PRIMARY KEY,
    cliente_id      BIGINT REFERENCES cliente (id),
    data            TIMESTAMPTZ NOT NULL DEFAULT now(),
    forma_pagamento TEXT NOT NULL CHECK (forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO', 'FIADO')),
    total           NUMERIC(10,2) NOT NULL CHECK (total >= 0),
    -- Fiado sem cliente nao existe: nao ha como cobrar quem nao se sabe quem e.
    -- A regra tambem vive no servico, mas o banco e a ultima linha de defesa.
    CONSTRAINT fiado_exige_cliente CHECK (forma_pagamento <> 'FIADO' OR cliente_id IS NOT NULL)
);

CREATE TABLE item_venda (
    id          BIGSERIAL PRIMARY KEY,
    venda_id    BIGINT NOT NULL REFERENCES venda (id) ON DELETE CASCADE,
    variacao_id BIGINT NOT NULL REFERENCES variacao (id),
    quantidade  INTEGER NOT NULL CHECK (quantidade > 0),
    preco_unit  NUMERIC(10,2) NOT NULL CHECK (preco_unit >= 0)
);

-- A divida do cliente NUNCA e armazenada: e sempre
--   SUM(venda.total onde FIADO) - SUM(pagamento_fiado.valor)
-- VALE_CREDITO e o tipo usado em devolucoes (Sprint 2).
CREATE TABLE pagamento_fiado (
    id         BIGSERIAL PRIMARY KEY,
    cliente_id BIGINT NOT NULL REFERENCES cliente (id),
    valor      NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    tipo       TEXT NOT NULL CHECK (tipo IN ('DINHEIRO', 'PIX', 'CARTAO', 'VALE_CREDITO')),
    data       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_variacao_produto ON variacao (produto_id);
CREATE INDEX idx_venda_cliente ON venda (cliente_id);
CREATE INDEX idx_item_venda_venda ON item_venda (venda_id);
CREATE INDEX idx_pagamento_fiado_cliente ON pagamento_fiado (cliente_id);
