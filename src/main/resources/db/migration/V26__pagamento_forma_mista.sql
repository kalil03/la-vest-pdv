-- Recebimento em MAIS DE UMA FORMA (ex.: metade dinheiro, metade PIX).
-- Em vez de mudar o caixa (que soma pagamento_fiado por tipo), um recebimento
-- misto vira VÁRIOS PagamentoFiado, um por forma — cada um com seu tipo e valor,
-- então o caixa conta certo sozinho (R$60 no dinheiro, R$40 no PIX).
-- Os pagamentos-irmãos apontam para o PRIMEIRO (o "pai"), que é quem guarda o
-- detalhamento por parcela (recebimento_item) e a partir de quem se reverte tudo.
ALTER TABLE pagamento_fiado
    ADD COLUMN pagamento_pai_id BIGINT REFERENCES pagamento_fiado (id) ON DELETE CASCADE;

CREATE INDEX idx_pagamento_fiado_pai ON pagamento_fiado (pagamento_pai_id);
