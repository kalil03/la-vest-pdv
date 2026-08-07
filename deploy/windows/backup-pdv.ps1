# Backup diario do banco do La Vest PDV (equivalente Windows do tools/backup-pdv.sh).
#
# - Gera C:\LaVest\backups\pdv-AAAA-MM-DD.dump (pg_dump formato custom, comprimido)
# - VERIFICA o backup restaurando num banco descartavel - backup que nao
#   restaura nao e backup
# - Mantem os ultimos 30; mantem tambem uma copia fixa "pdv-ultimo.dump"
# - So DEPOIS de verificado, copia o dump para o OneDrive (off-site): HD que
#   morre / PC roubado / ransomware nao leva banco e backup juntos.
#
# Restaurar de verdade (apaga o banco atual!):
#   & "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" -U pdv -h localhost -d pdv --clean --if-exists "C:\LaVest\backups\pdv-ultimo.dump"
#
# Agendado via Tarefa Agendada do Windows (ver Instalar-Backup-Agendado.ps1).

$ErrorActionPreference = "Stop"
$PGBIN = "C:\Program Files\PostgreSQL\16\bin"
$DESTINO = "C:\LaVest\backups"
$DATA = Get-Date -Format "yyyy-MM-dd"
$ARQUIVO = "$DESTINO\pdv-$DATA.dump"

New-Item -ItemType Directory -Force -Path $DESTINO | Out-Null

$env:PGPASSWORD = "pdv"
try {
    & "$PGBIN\pg_dump.exe" -U pdv -h localhost -Fc -f "$ARQUIVO.tmp" pdv
    if ($LASTEXITCODE -ne 0) { throw "pg_dump falhou (codigo $LASTEXITCODE)" }

    # prova real: restaura num banco descartavel e confere se tem dado de verdade
    & "$PGBIN\psql.exe" -U pdv -h localhost -q -c "DROP DATABASE IF EXISTS pdv_verifica" postgres | Out-Null
    & "$PGBIN\psql.exe" -U pdv -h localhost -q -c "CREATE DATABASE pdv_verifica" postgres | Out-Null
    & "$PGBIN\pg_restore.exe" -U pdv -h localhost -d pdv_verifica --no-owner "$ARQUIVO.tmp" 2>$null | Out-Null
    $parcelas = (((& "$PGBIN\psql.exe" -U pdv -h localhost -d pdv_verifica -t -c "SELECT COUNT(*) FROM pagamento_fiado") -join '')).Trim()
    $vendas   = (((& "$PGBIN\psql.exe" -U pdv -h localhost -d pdv_verifica -t -c "SELECT COUNT(*) FROM venda") -join '')).Trim()
    & "$PGBIN\psql.exe" -U pdv -h localhost -q -c "DROP DATABASE pdv_verifica" postgres | Out-Null

    if ([int]$parcelas -lt 1) {
        Remove-Item "$ARQUIVO.tmp" -Force -ErrorAction SilentlyContinue
        throw "backup restaurou vazio (0 lancamentos de fiado) - NAO confiavel, descartado"
    }

    Move-Item "$ARQUIVO.tmp" $ARQUIVO -Force
    Copy-Item $ARQUIVO "$DESTINO\pdv-ultimo.dump" -Force

    # mantem os 30 mais recentes
    Get-ChildItem "$DESTINO\pdv-????-??-??.dump" | Sort-Object LastWriteTime -Descending | Select-Object -Skip 30 | Remove-Item -Force -ErrorAction SilentlyContinue

    # copia OFF-SITE para o OneDrive - so aqui, com o dump JA verificado (nunca
    # empurra dump nao conferido pra nuvem). O OneDrive sincroniza sozinho.
    $offsite = Join-Path $env:OneDrive "LaVest-Backups"
    New-Item -ItemType Directory -Force -Path $offsite | Out-Null
    Copy-Item "$DESTINO\pdv-ultimo.dump" (Join-Path $offsite "pdv-ultimo.dump") -Force
    Copy-Item $ARQUIVO (Join-Path $offsite ("pdv-" + $DATA + ".dump")) -Force
    # mantem os 30 mais recentes tambem no off-site
    Get-ChildItem "$offsite\pdv-????-??-??.dump" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -Skip 30 | Remove-Item -Force -ErrorAction SilentlyContinue

    $tamanho = "{0:N1} MB" -f ((Get-Item $ARQUIVO).Length / 1MB)
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') backup OK: $ARQUIVO ($tamanho) - verificado: $vendas vendas, $parcelas lancamentos - off-site: $offsite" | Out-File "$DESTINO\backup.log" -Append -Encoding utf8
}
catch {
    "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') ERRO: $_" | Out-File "$DESTINO\backup.log" -Append -Encoding utf8
    throw
}
finally {
    $env:PGPASSWORD = ""
}
