-- V2: campos fiscais minimos para NFC-e futura, marcas, vendedores,
-- desconto e parcelamento de fiado com entrada.
-- O estoque continua existindo no schema (baixa silenciosa), mas nenhuma
-- tela exibe ou exige estoque por decisao de negocio.

CREATE TABLE marca (
    id   BIGSERIAL PRIMARY KEY,
    nome TEXT NOT NULL UNIQUE
);

CREATE TABLE vendedor (
    id    BIGSERIAL PRIMARY KEY,
    nome  TEXT NOT NULL,
    cpf   TEXT UNIQUE,
    ativo BOOLEAN NOT NULL DEFAULT TRUE
);

ALTER TABLE produto
    ADD COLUMN marca_id      BIGINT REFERENCES marca (id),
    ADD COLUMN data_criacao  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- dados fiscais minimos para NFC-e (Fase 2)
    ADD COLUMN ncm           TEXT,                       -- 8 digitos
    ADD COLUMN cest          TEXT,
    ADD COLUMN unidade       TEXT NOT NULL DEFAULT 'UN',
    ADD COLUMN codigo_barras TEXT,                       -- GTIN/EAN
    ADD COLUMN origem        INTEGER NOT NULL DEFAULT 0; -- 0=nacional

ALTER TABLE cliente
    ADD COLUMN email        TEXT,
    ADD COLUMN logradouro   TEXT,
    ADD COLUMN numero       TEXT,
    ADD COLUMN bairro       TEXT,
    ADD COLUMN cidade       TEXT,
    ADD COLUMN uf           TEXT,
    ADD COLUMN cep          TEXT,
    ADD COLUMN data_criacao TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE venda
    ADD COLUMN vendedor_id     BIGINT REFERENCES vendedor (id),
    ADD COLUMN desconto        NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (desconto >= 0),
    ADD COLUMN parcelas_cartao INTEGER; -- informativo (maquininha)

-- Entrada de fiado e um pagamento normal vinculado a venda de origem:
-- o saldo devedor continua sendo SUM(vendas FIADO) - SUM(pagamentos)
ALTER TABLE pagamento_fiado
    ADD COLUMN venda_id BIGINT REFERENCES venda (id);

-- Cronograma do carne: parcelas do fiado (a soma deve fechar com total - entrada)
CREATE TABLE parcela_fiado (
    id         BIGSERIAL PRIMARY KEY,
    venda_id   BIGINT NOT NULL REFERENCES venda (id) ON DELETE CASCADE,
    numero     INTEGER NOT NULL CHECK (numero > 0),
    valor      NUMERIC(10,2) NOT NULL CHECK (valor > 0),
    vencimento DATE NOT NULL,
    UNIQUE (venda_id, numero)
);
CREATE INDEX idx_parcela_fiado_venda ON parcela_fiado (venda_id);
