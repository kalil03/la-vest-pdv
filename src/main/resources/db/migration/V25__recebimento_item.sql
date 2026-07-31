-- Detalhamento de cada recebimento de carnê: quanto foi abatido de cada parcela.
-- Antes o recebimento gravava só o PagamentoFiado (valor total) e um texto — sem
-- isso não dava para REVERTER um recebimento lançado errado, pois não se sabia
-- quanto devolver a cada parcela. Agora cada alocação vira uma linha aqui, no
-- mesmo padrão do baixa_fiado_item, e o reverter restaura idêntico.
-- Só vale para recebimentos feitos a partir desta versão (os antigos não têm itens).
CREATE TABLE recebimento_item (
    id            BIGSERIAL PRIMARY KEY,
    pagamento_id  BIGINT NOT NULL REFERENCES pagamento_fiado (id) ON DELETE CASCADE,
    origem        TEXT NOT NULL,        -- 'L' = carnê SET (pagamento_fiado) | 'V' = parcela_fiado
    ref_id        BIGINT NOT NULL,      -- id da parcela abatida
    valor         NUMERIC(12, 2) NOT NULL
);

CREATE INDEX idx_recebimento_item_pagamento ON recebimento_item (pagamento_id);
