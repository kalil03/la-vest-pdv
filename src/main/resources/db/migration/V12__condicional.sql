-- Condicional: a cliente leva peças para provar em casa. Por decisão do dono,
-- a saída NÃO baixa estoque — só quando fecha (as peças que ela ficar viram
-- uma Venda normal, que aí sim baixa). O que volta não mexe em nada.
-- Status: ABERTA (peças fora) -> FECHADA (virou venda) ou CANCELADA (voltou tudo).
CREATE TABLE condicional (
    id              BIGSERIAL PRIMARY KEY,
    cliente_id      BIGINT NOT NULL REFERENCES cliente (id),
    vendedor_id     BIGINT REFERENCES vendedor (id),
    data_saida      TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          TEXT NOT NULL DEFAULT 'ABERTA',
    observacao      TEXT,
    venda_id        BIGINT REFERENCES venda (id),   -- venda gerada no fechamento
    data_fechamento TIMESTAMPTZ
);

CREATE TABLE item_condicional (
    id             BIGSERIAL PRIMARY KEY,
    condicional_id BIGINT NOT NULL REFERENCES condicional (id) ON DELETE CASCADE,
    variacao_id    BIGINT NOT NULL REFERENCES variacao (id),
    quantidade     INT NOT NULL,
    preco_unit     NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_condicional_status ON condicional (status);
CREATE INDEX idx_item_condicional_cond ON item_condicional (condicional_id);
