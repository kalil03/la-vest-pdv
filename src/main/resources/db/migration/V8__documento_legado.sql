-- Nº da notinha do sistema antigo (CONTASREC.NDOC, ex. "66/01" = notinha 66,
-- parcela 01) — e o codigo que os funcionarios e clientes conhecem.
-- Preenchido só em lancamentos vindos da importacao do SET (debito e o
-- pagamento historico correspondente); lancamentos do proprio sistema ficam NULL.
ALTER TABLE pagamento_fiado ADD COLUMN documento TEXT;

CREATE INDEX idx_pagamento_fiado_documento
    ON pagamento_fiado (documento) WHERE documento IS NOT NULL;
