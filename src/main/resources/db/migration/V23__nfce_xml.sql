-- Guarda o XML de distribuicao autorizado (nfeProc = NFe assinada + protocolo).
-- E o arquivo que o contador precisa; passa a ser gravado no momento da
-- autorizacao. Notas autorizadas antes desta coluna ficam com xml NULL
-- (recuperaveis so por download na SEFAZ).
ALTER TABLE nfce ADD COLUMN xml TEXT;
