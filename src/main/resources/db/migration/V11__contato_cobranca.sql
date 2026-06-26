-- Cobranca ativa: registro de cada contato feito com um devedor (ligacao,
-- WhatsApp, pessoalmente), o resultado e — quando promete pagar — a data
-- prometida. Vira a fila de follow-up: quem prometeu e nao cumpriu.
-- So historico/CRM; NAO mexe na divida (regra de ouro nº 1: divida e calculada).
CREATE TABLE contato_cobranca (
    id            BIGSERIAL PRIMARY KEY,
    cliente_id    BIGINT NOT NULL REFERENCES cliente (id),
    data          TIMESTAMPTZ NOT NULL DEFAULT now(),
    operador      TEXT,                 -- usuario logado que registrou
    canal         TEXT NOT NULL,        -- WHATSAPP | LIGACAO | PESSOAL | OUTRO
    resultado     TEXT NOT NULL,        -- PROMETEU | NAO_ATENDEU | CONTESTOU | SEM_CONDICAO | PAGOU | OUTRO
    promessa_data DATE,                 -- quando prometeu pagar (so se resultado = PROMETEU)
    observacao    TEXT
);

-- busca do ultimo contato por cliente (painel) e da fila de promessas
CREATE INDEX idx_contato_cobranca_cliente_data ON contato_cobranca (cliente_id, data DESC);
