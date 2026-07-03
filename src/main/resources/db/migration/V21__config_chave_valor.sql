-- Ajustes editaveis pela tela (dados da loja, padroes do carne, impressao).
-- Chave-valor: ajuste novo no futuro = INSERT, sem migration de coluna.
-- Sem seed: enquanto a chave nao existe, o backend usa o padrao do
-- application.properties — o banco so guarda o que a loja mudou.
CREATE TABLE config (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);
