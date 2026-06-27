-- Baixa de fiado por incobrabilidade — REVERSÍVEL e auditável.
-- A baixa zera o valor_aberto das parcelas escolhidas e cria um PagamentoFiado
-- tipo 'BAIXA' (positivo) que reduz o saldo SEM ser dinheiro (excluído do
-- caixa/recebido). O item guarda exatamente quanto saiu de cada parcela, para
-- o RESTAURAR devolver tudo idêntico. Mantém o invariante SUM(valor_aberto)==saldo.
CREATE TABLE baixa_fiado (
    id                BIGSERIAL PRIMARY KEY,
    cliente_id        BIGINT NOT NULL REFERENCES cliente (id),
    data              TIMESTAMPTZ NOT NULL DEFAULT now(),
    operador          TEXT,
    motivo            TEXT,
    valor             NUMERIC(12, 2) NOT NULL,
    status            TEXT NOT NULL DEFAULT 'ATIVA',   -- ATIVA | REVERTIDA
    pagamento_id      BIGINT REFERENCES pagamento_fiado (id),
    data_reversao     TIMESTAMPTZ,
    operador_reversao TEXT
);

CREATE TABLE baixa_fiado_item (
    id        BIGSERIAL PRIMARY KEY,
    baixa_id  BIGINT NOT NULL REFERENCES baixa_fiado (id) ON DELETE CASCADE,
    origem    TEXT NOT NULL,        -- 'L' = carnê SET (pagamento_fiado) | 'V' = parcela_fiado
    ref_id    BIGINT NOT NULL,      -- id da parcela de origem
    valor     NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_baixa_fiado_cliente ON baixa_fiado (cliente_id);
CREATE INDEX idx_baixa_fiado_status ON baixa_fiado (status);
CREATE INDEX idx_baixa_fiado_item_baixa ON baixa_fiado_item (baixa_id);
