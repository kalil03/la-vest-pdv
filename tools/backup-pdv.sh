#!/bin/bash
# Backup diario do banco do PDV.
#
# - Gera ~/backups/pdv-AAAA-MM-DD.dump (pg_dump formato custom, comprimido)
# - VERIFICA o backup restaurando num banco descartavel — backup que nao
#   restaura nao e backup
# - Mantem os ultimos 30; mantem tambem uma copia fixa "pdv-ultimo.dump"
#
# Restaurar de verdade (apaga o banco atual!):
#   docker exec -i pdv-postgres pg_restore -U pdv -d pdv --clean --if-exists < ~/backups/pdv-ultimo.dump
#
# Agendado no cron do usuario (crontab -l). Copie ~/backups para um pendrive
# ou nuvem com frequencia — backup no mesmo HD so protege contra besteira,
# nao contra o HD morrer.
set -euo pipefail

DESTINO="$HOME/backups"
DATA=$(date +%F)
ARQUIVO="$DESTINO/pdv-$DATA.dump"

mkdir -p "$DESTINO"

docker exec pdv-postgres pg_dump -U pdv -Fc pdv > "$ARQUIVO.tmp"

# prova real: restaura num banco descartavel
docker exec pdv-postgres psql -U pdv -q -c "DROP DATABASE IF EXISTS pdv_verifica" postgres
docker exec pdv-postgres psql -U pdv -q -c "CREATE DATABASE pdv_verifica" postgres
docker exec -i pdv-postgres pg_restore -U pdv -d pdv_verifica --no-owner < "$ARQUIVO.tmp" 2>/dev/null
VENDAS=$(docker exec pdv-postgres psql -U pdv -d pdv_verifica -t -c "SELECT COUNT(*) FROM venda" | tr -d ' ')
PARCELAS=$(docker exec pdv-postgres psql -U pdv -d pdv_verifica -t -c "SELECT COUNT(*) FROM pagamento_fiado" | tr -d ' ')
docker exec pdv-postgres psql -U pdv -q -c "DROP DATABASE pdv_verifica" postgres

if [ "$PARCELAS" -lt 1 ]; then
    echo "ERRO: backup restaurou vazio — NAO confiavel" >&2
    rm -f "$ARQUIVO.tmp"
    exit 1
fi

mv "$ARQUIVO.tmp" "$ARQUIVO"
cp "$ARQUIVO" "$DESTINO/pdv-ultimo.dump"

# mantem os 30 mais recentes
ls -1t "$DESTINO"/pdv-2*.dump 2>/dev/null | tail -n +31 | xargs -r rm -f

echo "backup OK: $ARQUIVO ($(du -h "$ARQUIVO" | cut -f1)) — verificado: $VENDAS vendas, $PARCELAS lancamentos"
