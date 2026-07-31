-- Venda à vista paga em MAIS DE UMA FORMA (ex.: R$60 no cartão + R$40 dinheiro).
-- A venda fica com forma_pagamento = 'MISTO' e o detalhamento por forma vai na
-- tabela pagamento_venda. O Caixa/Fechamento expande as vendas MISTO por essas
-- linhas (as de forma única continuam pela coluna forma_pagamento), então a
-- conferência do dinheiro físico continua exata.
ALTER TABLE venda DROP CONSTRAINT IF EXISTS venda_forma_pagamento_check;
ALTER TABLE venda ADD CONSTRAINT venda_forma_pagamento_check
    CHECK (forma_pagamento IN ('DINHEIRO', 'PIX', 'CARTAO', 'FIADO', 'MISTO'));

CREATE TABLE pagamento_venda (
    id       BIGSERIAL PRIMARY KEY,
    venda_id BIGINT NOT NULL REFERENCES venda (id) ON DELETE CASCADE,
    forma    TEXT NOT NULL CHECK (forma IN ('DINHEIRO', 'PIX', 'CARTAO')),
    valor    NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_pagamento_venda_venda ON pagamento_venda (venda_id);
