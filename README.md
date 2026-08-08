# La Vest PDV

Sistema de PDV e gestão para loja de roupas, calçados e perfumes. Numa única ação
registra a venda, baixa o estoque, lança no crediário (carnê) e imprime o recibo/
promissória na impressora térmica. Roda em **1 PC servidor** (backend + banco) com
outros PCs abrindo só o navegador na rede local.

## Stack

- **Backend:** Java 21 (código nível 17) · Spring Boot 3.5 · Spring Data JPA · PostgreSQL 16 · Flyway
- **Frontend:** HTML/CSS/JS puro servido pelo Spring Boot (sem framework, sem build)
- **Impressão:** HTML formatado para bobina 80mm via `window.print()` (Edge `--kiosk-printing`)
- **Nota fiscal:** emitida pelo sistema legado (Set Sistemas). O código de NFC-e
  (modelo 65, layout 4.00) existe no projeto mas está **desativado** — os endpoints
  respondem `410 Gone`. Ver [Fiscal](#fiscal-nfc-e-desativada).

---

## Funcionalidades

- **Venda** à vista (dinheiro, PIX, cartão, misto) e **fiado**, com entrada + cronograma de parcelas.
- **Crediário / carnê:** parcelas, recebimentos (uma ou várias formas), juros, reimpressão de promissória.
- **Condicional:** peças que saem para o cliente experimentar; ao fechar, viram venda na data do fechamento.
- **Caixa do dia:** vendas por forma, entradas/recebimentos, sangrias (retiradas), conferência (esperado × contado) e memória de saldo anterior.
- **Baixas / recebimentos:** reverter recebimento lançado errado, alterar a data (mover para o caixa de outro dia), reimprimir só notas em aberto.
- **Contas a receber, cobrança, relatórios** (vendas por tipo/vendedor, à vista/à prazo, aging do crediário).
- **Clientes, produtos/variações, etiquetas, vendedores, ajustes** (usuários, dados da loja, impressão).

---

## Regras de ouro (invariantes do projeto)

Estas decisões de projeto são o que mantém o financeiro confiável — mexer nelas exige cuidado:

- **Venda é atômica.** Venda + itens + parcelas + entrada gravam numa única transação; qualquer falha desfaz tudo.
- **Baixa de estoque é atômica** (`UPDATE ... SET estoque = estoque - :qtd` num só statement) — sem lost update. Estoque negativo é permitido por decisão de negócio.
- **Dívida nunca é armazenada, é sempre calculada:** `saldoDevedor = Σ(venda FIADO não cancelada) − Σ(pagamento_fiado)`. Invariante conferido em teste: `Σ(valor_aberto das parcelas) == saldoDevedor`.
- **Estorno é marcação, nunca DELETE.** A venda cancelada mantém numeração e data; sai das somas pelo filtro `cancelada_em IS NULL`. Toda anulação vira registro imutável em `estorno`.
- **Idempotência de venda e sangria:** o cliente manda um token (UUID por tentativa); reenvio da mesma tentativa não duplica venda/estoque nem sangria (constraint `UNIQUE` + tratamento da colisão no servidor).
- **`DEBITO_INICIAL`** = carnê migrado do Set (valor negativo empurra o saldo pra cima; `valor_aberto` controla o quanto falta pagar).

---

## Estrutura

```
src/main/java/br/com/loja/pdv/
  domain/       entidades JPA (Venda, Cliente, ParcelaFiado, PagamentoFiado, Condicional, ...)
  repository/   Spring Data repositories
  service/      regra de negócio (VendaService, CarneService, BaixaService, RelatorioService, ...)
  web/          controllers REST (/api/...) + dto/
src/main/resources/
  db/migration/ migrations Flyway (V1..V28) — donas do schema
  static/       frontend (html + js/)
src/test/java/  testes de integração contra PostgreSQL real (pdv_test)
tools/          scripts de migração do sistema legado (Firebird/Set)
deploy/windows/ pacote de produção (WinSW, application.properties, backup-pdv.ps1, instalador)
```

Segurança: é controle de **balcão**, não de banco — o login fica no `localStorage` e
não há sessão no servidor. A proteção real é a API ficar amarrada em `127.0.0.1`
(`server.address`), acessível só pela própria máquina. Expor na rede exige repensar auth.

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

# 3. Testes (usam o banco pdv_test)
./mvnw test
```

O Flyway cria/atualiza o schema sozinho no boot. Login `admin/admin` é desativado
automaticamente assim que você cadastra um operador em **Ajustes**.

---

## Produção (loja — Windows nativo)

A loja roda em **Windows 10** com PostgreSQL 16 nativo (porta 5432, `pdv/pdv`),
o backend como **serviço do Windows** (`pdv-backend`, via WinSW, sobe com o PC) e o
app aberto no Edge instalado como PWA. Tudo vive em `C:\LaVest`. Detalhes do pacote e
instalação em [`deploy/windows/WINDOWS.md`](deploy/windows/WINDOWS.md).

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
> ficou livre (`netstat -ano | findstr :8080`) antes de subir de novo. Migrations
> Flyway aplicam sozinhas no próximo start.

### Fiscal (NFC-e desativada)

A nota fiscal da loja é emitida pelo **Set Sistemas** (legado), não por este sistema.
O código de NFC-e (modelo 65, layout 4.00, assinatura/transmissão SEFAZ-PR) continua
no projeto e o histórico em `nfce` permanece intacto, mas a emissão está **desligada**
— os endpoints `/api/vendas/{id}/nfce` respondem `410 Gone`. A config fiscal ainda
vive em `C:\LaVest\application.properties` (fora do git — CNPJ, CSC, senha do
certificado .pfx); série 2 é da loja, série 1 é do legado.

---

## Backup

Automático, **todo dia** (roda de manhã, quando o PC da loja liga) via
`C:\LaVest\backup-pdv.ps1` — fonte no repo em
[`deploy/windows/backup-pdv.ps1`](deploy/windows/backup-pdv.ps1). Cada backup:

- gera dump `pg_dump -Fc` em `C:\LaVest\backups\pdv-AAAA-MM-DD.dump` (+ `pdv-ultimo.dump`);
- é **verificado restaurando num banco descartável** e conferindo contagem de linhas — dump que não restaura é descartado;
- mantém os **últimos 30 dias**; log em `backups\backup.log`;
- copia off-site para o **OneDrive** (`%OneDrive%\LaVest-Backups`) — protege contra HD morrer / PC roubado / ransomware.

```powershell
# rodar na hora
powershell -ExecutionPolicy Bypass -File C:\LaVest\backup-pdv.ps1

# restaurar (APAGA o banco atual!)
$env:PGPASSWORD="pdv"; & "C:\Program Files\PostgreSQL\16\bin\pg_restore.exe" `
  -U pdv -h localhost -d pdv --clean --if-exists "C:\LaVest\backups\pdv-ultimo.dump"
```

---

## Migração do sistema legado (Set Sistemas / Firebird)

Os scripts em `tools/` (`export-set.py`, `import-set.py`, `import-crediario.py`)
extraem produtos, clientes e crediário do banco Firebird antigo (`DADOS-SET.FDB`)
e importam no PostgreSQL. Rodados uma vez na virada; ver os comentários de cada script.
