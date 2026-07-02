-- Estorno deixa de DELETAR a venda: ela e MARCADA (cancelada_em) e sai de
-- todas as somas financeiras via filtro cancelada_em IS NULL. Beneficios:
-- o numero impresso na notinha nunca mais some do banco, o caixa de dias
-- passados fica reconstruivel, e o ON DELETE CASCADE da nfce deixa de ser
-- um risco ativo (nao ha mais DELETE de venda em lugar nenhum).
-- Sem status-string: timestamp nulo = venda valida, preenchido = cancelada.
ALTER TABLE venda
    ADD COLUMN cancelada_em        TIMESTAMPTZ,
    ADD COLUMN cancelada_por       TEXT,
    ADD COLUMN cancelamento_motivo TEXT;
