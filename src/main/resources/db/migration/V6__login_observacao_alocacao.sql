-- Login simples por funcionario (docs: sem autenticacao complexa).
-- senha_hash = sha256(sal || senha) em hex.
CREATE TABLE usuario (
    id         BIGSERIAL PRIMARY KEY,
    login      TEXT NOT NULL UNIQUE,
    nome       TEXT NOT NULL,
    sal        TEXT NOT NULL,
    senha_hash TEXT NOT NULL,
    ativo      BOOLEAN NOT NULL DEFAULT TRUE
);
-- usuario inicial: admin / admin (sal fixo 'nexopdv')
INSERT INTO usuario (login, nome, sal, senha_hash)
VALUES ('admin', 'Administrador', 'nexopdv',
        '7c641c51f0274e48e5d053066f80986d13d05dfed7a403a529666da09dcc275a');

-- Observacao da venda: "fulana comprou no nome da avo" — aparece na
-- promissoria e nas parcelas do carne.
ALTER TABLE venda ADD COLUMN observacao TEXT;

-- Alocacao de recebimento POR ORDEM DE SELECAO (pedido da loja): cada parcela
-- guarda quanto ainda resta dela. O SALDO DEVEDOR continua 100% calculado
-- (vendas FIADO - pagamentos); valor_aberto e so o rateio de qual parcela o
-- dinheiro abateu — invariante: SUM(valor_aberto) == saldo devedor.
ALTER TABLE parcela_fiado ADD COLUMN valor_aberto NUMERIC(10,2);
UPDATE parcela_fiado SET valor_aberto = valor;
ALTER TABLE parcela_fiado ALTER COLUMN valor_aberto SET NOT NULL;
ALTER TABLE parcela_fiado
    ADD CONSTRAINT parcela_valor_aberto_check CHECK (valor_aberto >= 0 AND valor_aberto <= valor);

-- Parcelas migradas do SET (DEBITO_INICIAL): valor_aberto = divida restante
ALTER TABLE pagamento_fiado ADD COLUMN valor_aberto NUMERIC(10,2);
UPDATE pagamento_fiado SET valor_aberto = -valor WHERE tipo = 'DEBITO_INICIAL';

-- Resumo legivel do que um recebimento quitou (ex.: "Venda nº 12 (2/3); Carne SET 03/07/2022")
ALTER TABLE pagamento_fiado ADD COLUMN detalhe TEXT;
