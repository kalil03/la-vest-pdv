-- Recebimento de carnê: registrar qual funcionário recebeu cada pagamento.
ALTER TABLE pagamento_fiado
    ADD COLUMN vendedor_id BIGINT REFERENCES vendedor (id);
