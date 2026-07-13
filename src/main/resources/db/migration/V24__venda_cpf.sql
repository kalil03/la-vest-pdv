-- CPF do consumidor informado no rodapé da venda, para sair como destinatário
-- na NFC-e (o "CPF na nota?" do balcão). Opcional e independente do cadastro do
-- cliente: uma venda à vista sem cliente registrado ainda pode ter CPF na nota.
ALTER TABLE venda ADD COLUMN cpf TEXT;
