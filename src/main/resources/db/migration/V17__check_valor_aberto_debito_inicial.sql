-- O valor_aberto do carne migrado do SET (DEBITO_INICIAL) existia desde a V6
-- sem nenhum CHECK — um bug de abate quebraria o invariante
-- SUM(valor_aberto) == saldo em silencio. O banco passa a ser a ultima linha
-- de defesa, como ja e para parcela_fiado (parcela_valor_aberto_check).
-- Lembrete: DEBITO_INICIAL tem valor NEGATIVO, entao -valor e o valor original.
ALTER TABLE pagamento_fiado ADD CONSTRAINT debito_valor_aberto_check
    CHECK (tipo <> 'DEBITO_INICIAL' OR valor_aberto IS NULL
           OR (valor_aberto >= 0 AND valor_aberto <= -valor));
