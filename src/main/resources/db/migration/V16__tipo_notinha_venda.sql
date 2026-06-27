-- Tipo da notinha nas vendas novas (Roupa / Tênis), obrigatório no fechamento.
-- Vendas antigas (antes deste campo) ficam NULL. Permite separar a carteira por
-- tipo também nas vendas próprias, igual ao carnê migrado do SET.
ALTER TABLE venda ADD COLUMN tipo_notinha TEXT;
