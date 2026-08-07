-- Idempotência de venda e sangria: uma chave (UUID) gerada pelo cliente por
-- TENTATIVA. Se a resposta se perde e o cliente reenvia a MESMA tentativa, a
-- constraint UNIQUE barra o duplicado e o serviço devolve o registro já gravado
-- em vez de criar outro (venda duplicada + estoque baixado 2x, sangria em dobro).
--
-- Coluna NULLABLE de propósito: chamada antiga (JS em cache, sem token) continua
-- funcionando — no Postgres a UNIQUE ignora NULLs, então vários NULLs não colidem.
ALTER TABLE venda ADD COLUMN idempotency_key TEXT;
ALTER TABLE venda ADD CONSTRAINT uq_venda_idempotency UNIQUE (idempotency_key);

ALTER TABLE retirada_caixa ADD COLUMN idempotency_key TEXT;
ALTER TABLE retirada_caixa ADD CONSTRAINT uq_retirada_caixa_idempotency UNIQUE (idempotency_key);
