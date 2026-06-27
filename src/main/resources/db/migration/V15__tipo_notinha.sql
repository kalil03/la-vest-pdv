-- Tipo da notinha do crediário do SET: cada parcela aberta (DEBITO_INICIAL)
-- vinha de uma venda de "Tênis" ou de "Roupa" (coluna GRUPO do contasrec).
-- Guardamos isso para separar a carteira por tipo nas telas.
ALTER TABLE pagamento_fiado ADD COLUMN tipo_notinha TEXT;
