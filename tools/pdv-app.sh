#!/bin/bash
# Lancador do PDV como "aplicativo desktop".
#
# Sobe (se preciso) o banco e o backend, espera ficar pronto e abre o Chrome
# em MODO APLICATIVO: janela propria, sem barra de endereco nem abas, com
# impressao direto na termica (--kiosk-printing). Cara de programa de loja,
# nao de site.
#
# Idempotente: rodar de novo nao sobe um segundo backend; so reabre a janela.
set -euo pipefail

RAIZ="$HOME/sistema"
JAR="$RAIZ/target/pdv-0.0.1-SNAPSHOT.jar"
JAVA="$HOME/tools/jdk-21.0.11+10/bin/java"
URL="http://localhost:8080"
LOGDIR="$HOME/.local/share/pdv"
PERFIL="$HOME/.var/app/com.google.Chrome/pdv-perfil"  # perfil isolado (dentro do sandbox do Chrome flatpak)
mkdir -p "$LOGDIR"

esta_no_ar() { curl -s -o /dev/null --max-time 2 "$URL/api/config"; }

# 1) banco
if ! docker ps --format '{{.Names}}' | grep -q '^pdv-postgres$'; then
    echo "subindo o banco..."
    docker start pdv-postgres >/dev/null 2>&1 || {
        echo "ERRO: container pdv-postgres nao existe. Crie o banco antes." >&2; exit 1; }
fi

# 2) backend (so se ainda nao estiver no ar)
if ! esta_no_ar; then
    echo "iniciando o sistema..."
    # setsid: nova sessao, o backend sobrevive ao fechar a janela/terminal
    setsid "$JAVA" -jar "$JAR" > "$LOGDIR/app.log" 2>&1 < /dev/null &
    for _ in $(seq 1 60); do esta_no_ar && break; sleep 1; done
    esta_no_ar || { echo "ERRO: o sistema nao subiu — veja $LOGDIR/app.log" >&2; exit 1; }
fi

# 3) janela do aplicativo
echo "abrindo o PDV..."
exec flatpak run com.google.Chrome \
    --user-data-dir="$PERFIL" \
    --app="$URL" \
    --kiosk-printing \
    --no-first-run \
    --no-default-browser-check \
    >> "$LOGDIR/app.log" 2>&1
