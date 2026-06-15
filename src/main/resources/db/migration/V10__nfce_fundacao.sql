-- Fundacao da NFC-e (Fase 2). Tributacao por produto: NULL = usa o padrao da
-- loja (fiscal.csosn-padrao / fiscal.cfop-padrao), que cobre o varejo no
-- Simples Nacional. So precisa preencher aqui o produto que foge do padrao.
ALTER TABLE produto
    ADD COLUMN csosn TEXT,
    ADD COLUMN cfop  TEXT;

-- Rastreio das notas emitidas. Uma venda tem no maximo uma NFC-e.
-- A divida/estoque NAO dependem disto: a nota fiscal e um efeito posterior,
-- a venda existe e fecha mesmo sem nota (como hoje, no sistema antigo).
CREATE TABLE nfce (
    id            BIGSERIAL PRIMARY KEY,
    venda_id      BIGINT NOT NULL UNIQUE REFERENCES venda (id) ON DELETE CASCADE,
    ref           TEXT NOT NULL UNIQUE,        -- identificador idempotente enviado a Focus
    status        TEXT NOT NULL,               -- PROCESSANDO | AUTORIZADO | ERRO | CANCELADO
    chave_acesso  TEXT,
    numero        INTEGER,
    serie         INTEGER,
    protocolo     TEXT,
    danfe_url     TEXT,
    xml_url       TEXT,
    mensagem      TEXT,                         -- motivo da rejeicao, quando ERRO
    criada_em     TIMESTAMPTZ NOT NULL DEFAULT now(),
    autorizada_em TIMESTAMPTZ
);
CREATE INDEX idx_nfce_status ON nfce (status);
