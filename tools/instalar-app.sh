#!/bin/bash
# Instala o PDV no menu/dock do sistema: cria o atalho (.desktop) e o icone.
# Depois de rodar, "Loja La Vest PDV" aparece no menu de aplicativos como
# qualquer programa instalado. Rodar de novo so atualiza.
set -euo pipefail

RAIZ="$HOME/sistema"
APPS="$HOME/.local/share/applications"
ICONES="$HOME/.local/share/icons/hicolor/scalable/apps"
mkdir -p "$APPS" "$ICONES"

# empacota o jar do app se ainda nao existir (o lancador usa o jar, nao o Maven)
if [ ! -f "$RAIZ/target/pdv-0.0.1-SNAPSHOT.jar" ]; then
    echo "empacotando o sistema (primeira vez)..."
    ( cd "$RAIZ" && JAVA_HOME="$HOME/tools/jdk-21.0.11+10" ./mvnw -q -DskipTests package )
fi

cp "$RAIZ/tools/pdv-icon.svg" "$ICONES/loja-la-vest-pdv.svg"
chmod +x "$RAIZ/tools/pdv-app.sh"

cat > "$APPS/loja-la-vest-pdv.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Loja La Vest PDV
Comment=Sistema de PDV e gestao da loja
Exec=$RAIZ/tools/pdv-app.sh
Icon=loja-la-vest-pdv
Terminal=false
Categories=Office;Finance;
StartupNotify=true
EOF

update-desktop-database "$APPS" 2>/dev/null || true
echo "instalado. Procure por 'Loja La Vest PDV' no menu de aplicativos."
