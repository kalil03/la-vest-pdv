-- Trilha de auditoria de estornos: a venda estornada some do banco (estoque
-- volta, fiado desfeito), mas fica registrado QUEM desfez O QUE e QUANDO.
-- E o jeito classico de sumir dinheiro de loja — este log nao tem DELETE.
CREATE TABLE estorno (
    id              BIGSERIAL PRIMARY KEY,
    venda_id        BIGINT NOT NULL,          -- nº da venda original (ja apagada)
    data            TIMESTAMPTZ NOT NULL DEFAULT now(),
    operador        TEXT,                     -- usuario logado que estornou
    motivo          TEXT,                     -- 'estorno' | 'edicao' | livre
    cliente_nome    TEXT,
    forma_pagamento TEXT NOT NULL,
    total           NUMERIC(10,2) NOT NULL,
    resumo          TEXT                      -- itens da venda ("2x Tenis 38; 1x Perfume")
);
