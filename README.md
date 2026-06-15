# La Vest PDV

Sistema de PDV e gestão para loja de roupas, calçados e perfumes. Registra a venda, baixa o estoque, gerencia crediário e imprime o recibo em uma única ação.

## Pré-requisitos

- **Java 21** — ex: `~/tools/jdk-21.0.11+10` (ajuste para o caminho real na sua máquina)
- **Docker**

## Rodar em desenvolvimento

### 1. Subir o banco

```bash
docker run -d --name pdv-postgres \
  -e POSTGRES_USER=pdv \
  -e POSTGRES_PASSWORD=pdv \
  -p 5433:5432 \
  postgres:latest

# o banco "pdv" já é criado automaticamente pelo postgres (POSTGRES_USER=pdv)
# só precisa criar o banco de testes:
docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv_test;"
```

### 2. Exportar o JAVA_HOME

```bash
export JAVA_HOME=~/tools/jdk-21.0.11+10   # ajuste para o caminho real
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

### Empacotar e reiniciar após mudanças no código

Toda alteração em arquivos estáticos (HTML, JS, CSS) ou Java exige reempacotar e reiniciar o backend para ter efeito:

```bash
# 1. Reempacotar (ajuste o JAVA_HOME para o caminho real do JDK 21 na sua máquina)
JAVA_HOME=~/.vscode/extensions/redhat.java-1.54.0-linux-x64/jre/21.0.10-linux-x86_64 \
PATH=$JAVA_HOME/bin:$PATH \
./mvnw package -DskipTests

# 2. Reiniciar o backend (matar o processo Java atual e subir de novo via atalho do app)
pkill -f pdv-0.0.1-SNAPSHOT.jar
```

Depois abra o atalho "Loja La Vest PDV" normalmente — ele sobe o backend do JAR novo.

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
