-- Permite o novo tipo de PagamentoFiado 'BAIXA' (baixa por incobrabilidade).
-- É positivo (reduz o saldo) e satisfaz o pagamento_fiado_valor_check existente
-- (tipo <> 'DEBITO_INICIAL' AND valor > 0).
ALTER TABLE pagamento_fiado DROP CONSTRAINT pagamento_fiado_tipo_check;
ALTER TABLE pagamento_fiado ADD CONSTRAINT pagamento_fiado_tipo_check
    CHECK (tipo IN ('DINHEIRO', 'PIX', 'CARTAO', 'VALE_CREDITO', 'DEBITO_INICIAL', 'BAIXA'));
