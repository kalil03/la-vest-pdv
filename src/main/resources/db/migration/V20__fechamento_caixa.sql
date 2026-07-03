-- Fechamento diario do caixa: a conferencia da gaveta vira registro.
-- saldo_anterior e contagem sao digitados pela operadora; esperado e diferenca
-- sao recalculados pelo servidor na hora de gravar (fonte da verdade).
-- A contagem de hoje vira a sugestao de saldo_anterior de amanha.
CREATE TABLE fechamento_caixa (
    id             BIGSERIAL PRIMARY KEY,
    data           DATE NOT NULL UNIQUE,
    saldo_anterior NUMERIC(12,2) NOT NULL DEFAULT 0,
    contagem       NUMERIC(12,2) NOT NULL,
    esperado       NUMERIC(12,2) NOT NULL,
    diferenca      NUMERIC(12,2) NOT NULL,
    operador       TEXT,
    fechado_em     TIMESTAMPTZ NOT NULL DEFAULT now()
);
