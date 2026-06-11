-- Importacao do crediario legado do SET: cada parcela em aberto vira um
-- PagamentoFiado tipo DEBITO_INICIAL com VALOR NEGATIVO. Como o saldo devedor
-- e sempre SUM(vendas FIADO) - SUM(pagamentos), um pagamento negativo SOMA
-- divida — a regra de ouro (divida calculada, nunca armazenada) fica intacta
-- e o historico de vendas nao e poluido com vendas sinteticas.

ALTER TABLE pagamento_fiado DROP CONSTRAINT pagamento_fiado_tipo_check;
ALTER TABLE pagamento_fiado DROP CONSTRAINT pagamento_fiado_valor_check;

ALTER TABLE pagamento_fiado ADD CONSTRAINT pagamento_fiado_tipo_check
    CHECK (tipo IN ('DINHEIRO', 'PIX', 'CARTAO', 'VALE_CREDITO', 'DEBITO_INICIAL'));

-- Negativo SO para debito inicial; pagamentos de verdade continuam positivos
ALTER TABLE pagamento_fiado ADD CONSTRAINT pagamento_fiado_valor_check
    CHECK ((tipo = 'DEBITO_INICIAL' AND valor < 0) OR (tipo <> 'DEBITO_INICIAL' AND valor > 0));
