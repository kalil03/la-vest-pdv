DO $$
DECLARE
    v_cliente RECORD;
    v_pagamento NUMERIC;
    v_parcela RECORD;
    v_abater NUMERIC;
BEGIN
    FOR v_cliente IN (SELECT id FROM cliente) LOOP
        -- Calcula o total pago pelo cliente (exceto debito inicial que entra como divida)
        SELECT COALESCE(SUM(valor), 0) INTO v_pagamento
        FROM pagamento_fiado
        WHERE cliente_id = v_cliente.id AND tipo != 'DEBITO_INICIAL';

        -- Se nao pagou nada, continua
        IF v_pagamento <= 0 THEN
            CONTINUE;
        END IF;

        -- Primeiro abate das parcelas de debito inicial (que sao registros em pagamento_fiado com tipo DEBITO_INICIAL)
        -- cujo valor_aberto > 0
        FOR v_parcela IN (SELECT id, valor_aberto FROM pagamento_fiado WHERE cliente_id = v_cliente.id AND tipo = 'DEBITO_INICIAL' AND valor_aberto > 0 ORDER BY data ASC) LOOP
            IF v_pagamento <= 0 THEN EXIT; END IF;
            v_abater := LEAST(v_pagamento, v_parcela.valor_aberto);
            v_pagamento := v_pagamento - v_abater;
            UPDATE pagamento_fiado SET valor_aberto = valor_aberto - v_abater WHERE id = v_parcela.id;
        END LOOP;

        -- Depois abate das parcelas de venda normais
        FOR v_parcela IN (
            SELECT p.id, p.valor_aberto 
            FROM parcela_fiado p 
            JOIN venda v ON p.venda_id = v.id 
            WHERE v.cliente_id = v_cliente.id AND p.valor_aberto > 0 
            ORDER BY p.vencimento ASC, p.id ASC
        ) LOOP
            IF v_pagamento <= 0 THEN EXIT; END IF;
            v_abater := LEAST(v_pagamento, v_parcela.valor_aberto);
            v_pagamento := v_pagamento - v_abater;
            UPDATE parcela_fiado SET valor_aberto = valor_aberto - v_abater WHERE id = v_parcela.id;
        END LOOP;
    END LOOP;
END $$;
