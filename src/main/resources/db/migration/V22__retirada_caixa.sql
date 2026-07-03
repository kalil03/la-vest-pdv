-- Retirada de dinheiro do caixa (sangria). Duas funcoes:
-- 1) saida em dinheiro na conferencia da gaveta (antes, sangria virava "falta"
--    na diferenca esperado-vs-contado do fechamento);
-- 2) fonte da linha "Retiradas do mes" no fechamento mensal.
-- O dia da retirada e derivado do timestamp na zona da loja, como o resto.
CREATE TABLE retirada_caixa (
    id       BIGSERIAL PRIMARY KEY,
    data     TIMESTAMPTZ NOT NULL DEFAULT now(),
    valor    NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    motivo   TEXT,
    operador TEXT
);
CREATE INDEX idx_retirada_caixa_data ON retirada_caixa (data);
