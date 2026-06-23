# La Vest PDV

Sistema de PDV e gestão para loja de roupas, calçados e perfumes. Registra a venda, baixa o estoque, gerencia crediário e imprime o recibo em uma única ação.

## Pré-requisitos

- **Java 21** — `~/tools/jdk-21.0.11+10` (o sistema só tem Java 17)
- **Docker**

## Rodar em desenvolvimento

### 1. Subir o banco

```bash
docker run -d --name pdv-postgres \
  -e POSTGRES_USER=pdv \
  -e POSTGRES_PASSWORD=pdv \
  -p 5433:5432 \
  postgres:latest

docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv;"
docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv_test;"
```

### 2. Exportar o JAVA_HOME

```bash
export JAVA_HOME=~/tools/jdk-21.0.11+10
```

### 3. Subir a aplicação

```bash
./mvnw spring-boot:run
```

Acesse em **http://localhost:8080**. Login inicial: `admin` / `admin`.

### 4. Rodar os testes

```bash
./mvnw test
```

---

## Produção (loja)

### Empacotar após mudanças no código

```bash
./mvnw package
```

### Instalar o atalho no desktop (Linux)

```bash
bash tools/instalar-app.sh

```

Cria o atalho "Loja La Vest PDV" no menu do sistema. Ao abrir, sobe o banco + backend e abre o Chrome em modo aplicativo (`--kiosk-printing` para imprimir direto na térmica).

### Pacote Windows 10

Gerado por `tools/montar-pacote-windows.sh`. Inclui Edge `--app --kiosk-printing`, PostgreSQL nativo, backend como serviço (WinSW) e JRE embutido. Os arquivos ficam em `deploy/windows/`.

---

## Backup e restauração

Backup automático diário às 21h via cron (`~/backups/pdv-AAAA-MM-DD.dump`, mantém 30 dias):

```bash
bash tools/backup-pdv.sh
```

Restaurar:

```bash
docker exec -i pdv-postgres pg_restore -U pdv -d pdv --clean --if-exists < ~/backups/pdv-ultimo.dump
```

**Copie `~/backups/` para fora do PC com frequência.**

---

## Stack

- **Backend:** Java 21 · Spring Boot 3 · Spring Data JPA · Hibernate · PostgreSQL · Flyway
- **Frontend:** HTML/CSS/JS puro servido pelo Spring Boot (sem frameworks)
- **Impressão:** página HTML formatada para bobina 80mm via `window.print()`
