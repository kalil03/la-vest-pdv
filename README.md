# La Vest PDV

Sistema de PDV e gestão para loja de roupas, calçados e perfumes. Registra a venda,
baixa o estoque, gerencia crediário (carnê), emite **NFC-e** direto na SEFAZ-PR e
imprime o recibo na térmica — tudo em uma ação.

## Stack

- **Backend:** Java 21 · Spring Boot 3 · Spring Data JPA · PostgreSQL · Flyway
- **Frontend:** HTML/CSS/JS puro servido pelo Spring Boot (sem framework)
- **Fiscal:** NFC-e modelo 65, layout 4.00, assinada/transmitida via lib `Java_NFe` (grátis, sem gateway)
- **Impressão:** HTML formatado para bobina 80mm via `window.print()` (Edge `--kiosk-printing`)

---

## Desenvolvimento

Precisa de **Java 21** e **Docker** (ou um PostgreSQL local).

```bash
# 1. Banco (Docker) — porta 5433 no dev
docker run -d --name pdv-postgres -e POSTGRES_USER=pdv -e POSTGRES_PASSWORD=pdv -p 5433:5432 postgres:latest
docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv;"
docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv_test;"

# 2. Apontar o Java 21 e subir a aplicação
export JAVA_HOME=/caminho/do/jdk-21
./mvnw spring-boot:run        # http://localhost:8080  — login: admin / admin

# 3. Testes (usa o banco pdv_test)
./mvnw test
```

O Flyway cria/atualiza o schema sozinho no boot. Login `admin/admin` é desativado
automaticamente assim que você cadastra um operador em **Ajustes**.

---

## Produção (loja — Windows nativo)

A loja roda em **Windows 10** com PostgreSQL 16 nativo (porta 5432, `pdv/pdv`),
o backend como **serviço do Windows** (`pdv-backend`, via WinSW) e o app aberto no
Edge instalado como PWA. Tudo vive em `C:\LaVest`.

### Atualizar o sistema após mudança no código

```bash
./mvnw -DskipTests package          # gera target/pdv-0.0.1-SNAPSHOT.jar
```

Na máquina da loja (PowerShell como admin):

```powershell
C:\LaVest\pdv-backend.exe stop
Copy-Item <novo>\pdv-0.0.1-SNAPSHOT.jar C:\LaVest\pdv.jar -Force
C:\LaVest\pdv-backend.exe start
```

> Sempre **parar o serviço antes de trocar o jar** — e conferir que a porta 8080
> ficou livre (`netstat -ano | findstr :8080`) antes de subir de novo.

### Configuração fiscal (NFC-e)

Fica em `C:\LaVest\application.properties` (fora do git — tem CNPJ, CSC e senha do
certificado). Campos: `fiscal.ambiente` (`homologacao`/`producao`), `fiscal.cnpj`,
`fiscal.csc`/`fiscal.csc-id`, `fiscal.certificado-caminho`/`-senha` (.pfx A1),
`fiscal.nfce.serie` (a loja usa **série 2**; série 1 é do sistema legado) e
`fiscal.resp-tecnico.*`. Schemas XSD em `C:\LaVest\schemas`.

---

## Backup

Automático, diário às 21h (Tarefa Agendada `LaVest-Backup-Diario` → `C:\LaVest\backup-pdv.ps1`).
Gera dump verificado em `C:\LaVest\backups\`, mantém 30 dias, log em `backups\backup.log`.

```powershell
# rodar na hora
powershell -ExecutionPolicy Bypass -File C:\LaVest\backup-pdv.ps1

# restaurar (APAGA o banco atual!)
$env:PGPASSWORD="pdv"; & "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" `
  -U pdv -h localhost -d pdv --clean --if-exists "C:\LaVest\backups\pdv-ultimo.dump"
```

**Copie `C:\LaVest\backups\` para fora do PC com frequência** (pendrive/nuvem) —
backup no mesmo HD não protege contra o HD morrer.

---

## Migração do sistema legado (Set Sistemas / Firebird)

Os scripts em `tools/` (`export-set.py`, `import-set.py`, `import-crediario.py`)
extraem produtos, clientes e crediário do banco Firebird antigo (`DADOS-SET.FDB`)
e importam no PostgreSQL. Rodados uma vez na virada; ver os comentários de cada script.
