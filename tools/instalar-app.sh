#!/bin/bash
# Instala o PDV no menu/dock do sistema: cria o atalho (.desktop) e o icone.
# Depois de rodar, "Loja La Vest PDV" aparece no menu de aplicativos como
# qualquer programa instalado. Rodar de novo so atualiza.
set -euo pipefail

RAIZ="$HOME/sistema"
APPS="$HOME/.local/share/applications"
ICONE_PNG="$HOME/.local/share/icons/hicolor/256x256/apps"
ICONE_SVG="$HOME/.local/share/icons/hicolor/scalable/apps"
mkdir -p "$APPS" "$ICONE_PNG" "$ICONE_SVG"

chmod +x "$RAIZ/tools/pdv-app.sh"

# icone: prefere a logo da loja (tools/la-vest-logo.png); senao usa o placeholder SVG.
# a logo tambem vira o favicon = icone da janela do Chrome em modo aplicativo.
PRECISA_EMPACOTAR=0
rm -f "$ICONE_PNG/la-vest.png" "$ICONE_SVG/la-vest.svg"
if [ -f "$RAIZ/tools/la-vest-logo.png" ]; then
    cp "$RAIZ/tools/la-vest-logo.png" "$ICONE_PNG/la-vest.png"
    # favicon (janela do app): so re-empacota se a logo mudou
    if ! cmp -s "$RAIZ/tools/la-vest-logo.png" "$RAIZ/src/main/resources/static/favicon.png" 2>/dev/null; then
        cp "$RAIZ/tools/la-vest-logo.png" "$RAIZ/src/main/resources/static/favicon.png"
        PRECISA_EMPACOTAR=1
    fi
    echo "usando a logo La Vest (tools/la-vest-logo.png)"
else
    cp "$RAIZ/tools/pdv-icon.svg" "$ICONE_SVG/la-vest.svg"
    echo "logo nao encontrada — usando icone provisorio. Salve a sua em tools/la-vest-logo.png e rode de novo."
fi

# empacota o jar se faltar ou se a logo mudou
if [ ! -f "$RAIZ/target/pdv-0.0.1-SNAPSHOT.jar" ] || [ "$PRECISA_EMPACOTAR" = 1 ]; then
    echo "empacotando o sistema..."
    ( cd "$RAIZ" && JAVA_HOME="$HOME/tools/jdk-21.0.11+10" ./mvnw -q -DskipTests package )
fi

# remove o atalho antigo, se existir
rm -f "$APPS/loja-la-vest-pdv.desktop"

cat > "$APPS/la-vest.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=La Vest
Comment=Sistema de PDV e gestao da loja
Exec=$RAIZ/tools/pdv-app.sh
Icon=la-vest
Terminal=false
Categories=Office;Finance;
StartupNotify=true
EOF

update-desktop-database "$APPS" 2>/dev/null || true
gtk-update-icon-cache -f -t "$HOME/.local/share/icons/hicolor" 2>/dev/null || true
echo "instalado. Procure por 'La Vest' no menu de aplicativos."
