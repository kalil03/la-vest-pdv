-- O tipo de notinha "Roupa" na verdade cobre tudo que nao e tenis (roupa,
-- perfume, acessorio...) — passa a se chamar "Geral". UPDATE nas duas tabelas
-- em vez de traduzir na exibicao: um valor, um significado, sem tela nova
-- precisando conhecer o apelido antigo. Nenhum CHECK/enum existe nas colunas.
UPDATE venda SET tipo_notinha = 'Geral' WHERE tipo_notinha = 'Roupa';
UPDATE pagamento_fiado SET tipo_notinha = 'Geral' WHERE tipo_notinha = 'Roupa';
