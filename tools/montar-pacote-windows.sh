#!/bin/bash
# Monta o pacote de instalacao do La Vest PDV para Windows 10.
#
# Junta numa pasta (e num .zip) tudo que a loja precisa levar no pendrive:
#   pdv.jar (backend) + JRE Windows embutido + WinSW (servico) + dados +
#   scripts de instalacao + icone. A loja so instala o PostgreSQL e roda
#   o Instalar-LaVest.bat.
#
# Roda nesta maquina Linux (dev). Gera dist/LaVest-Windows/ e LaVest-Windows.zip
set -euo pipefail

RAIZ="$HOME/sistema"
DIST="$RAIZ/dist/LaVest-Windows"
JRE_URL="https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jre/hotspot/normal/eclipse"
WINSW_URL="https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe"

echo "== 1/5 empacotando o backend (com a logo) =="
# para o servico se estiver rodando: sobrescrever o jar em uso corrompe a JVM
RODAVA=0
if systemctl --user is-active --quiet pdv-backend 2>/dev/null; then
    RODAVA=1; systemctl --user stop pdv-backend
fi
( cd "$RAIZ" && JAVA_HOME="$HOME/tools/jdk-21.0.11+10" ./mvnw -q -DskipTests package )
[ "$RODAVA" = 1 ] && systemd-run --user --unit=pdv-backend \
    --setenv=JAVA_HOME="$HOME/tools/jdk-21.0.11+10" \
    "$HOME/tools/jdk-21.0.11+10/bin/java" -jar "$RAIZ/target/pdv-0.0.1-SNAPSHOT.jar" >/dev/null 2>&1 || true

echo "== 2/5 preparando a pasta =="
rm -rf "$DIST"; mkdir -p "$DIST"
cp "$RAIZ/target/pdv-0.0.1-SNAPSHOT.jar" "$DIST/pdv.jar"
cp "$RAIZ/deploy/windows/application.properties" "$DIST/"
cp "$RAIZ/deploy/windows/pdv-backend.xml"        "$DIST/"
cp "$RAIZ/deploy/windows/Instalar-LaVest.bat"    "$DIST/"
cp "$RAIZ/deploy/windows/la-vest.ico"            "$DIST/"
cp "$RAIZ/deploy/windows/LEIA-ME.txt"            "$DIST/" 2>/dev/null || true
# dump fresco dos dados atuais
docker exec pdv-postgres pg_dump -U pdv -Fc pdv > "$DIST/dados-iniciais.dump"

echo "== 3/5 baixando o WinSW (servico do Windows) =="
curl -fsSL -o "$DIST/pdv-backend.exe" "$WINSW_URL"

echo "== 4/5 baixando o JRE 21 para Windows (embutido) =="
TMP=$(mktemp -d)
curl -fsSL -o "$TMP/jre.zip" "$JRE_URL"
( cd "$TMP" && unzip -q jre.zip )
JREDIR=$(find "$TMP" -maxdepth 1 -type d -name 'jdk-*-jre' | head -1)
[ -z "$JREDIR" ] && JREDIR=$(find "$TMP" -maxdepth 1 -type d -name 'jdk-*' | head -1)
mv "$JREDIR" "$DIST/jre"
rm -rf "$TMP"

echo "== 5/5 zipando =="
( cd "$RAIZ/dist" && zip -qr LaVest-Windows.zip LaVest-Windows )
echo
echo "PRONTO: $RAIZ/dist/LaVest-Windows.zip ($(du -h "$RAIZ/dist/LaVest-Windows.zip" | cut -f1))"
echo "Leve no pendrive, descompacte na maquina Windows e rode Instalar-LaVest.bat como administrador."
