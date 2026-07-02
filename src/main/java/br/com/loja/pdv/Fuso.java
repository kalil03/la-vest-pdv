package br.com.loja.pdv;

import java.time.ZoneId;

/**
 * O fuso da loja, num lugar só. "Hoje" depende de onde se pergunta: a JVM sem
 * zona explícita e o CURRENT_DATE do Postgres (container, possivelmente em UTC)
 * podem discordar da loja depois das ~21h — e uma parcela que vence hoje
 * apareceria atrasada. Regra: no Java, sempre LocalDate.now(Fuso.LOJA); no
 * SQL, nunca CURRENT_DATE — usar CAST(now() AT TIME ZONE 'America/Sao_Paulo' AS date).
 */
public final class Fuso {

    public static final ZoneId LOJA = ZoneId.of("America/Sao_Paulo");

    private Fuso() {}
}
