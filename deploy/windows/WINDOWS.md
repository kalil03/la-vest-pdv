# Implantação no Windows 10 — La Vest PDV

A loja roda num PC Windows 10. A "janela de aplicativo" usa o **Microsoft Edge**
(já vem no Windows) em modo `--app --kiosk-printing` — sem barra de endereço,
impressão direto na térmica. O backend é o mesmo `.jar` (Java é portável),
rodando como **serviço do Windows** que sobe junto com o PC.

## Gerar o pacote (nesta máquina Linux)

```bash
bash tools/montar-pacote-windows.sh
```

Produz `dist/LaVest-Windows.zip` (~103 MB) com tudo dentro:

| Arquivo | O que é |
|---|---|
| `pdv.jar` | backend (Spring Boot fat jar, com a logo embutida) |
| `jre/` | Java 21 para Windows **embutido** — a loja não instala Java |
| `pdv-backend.exe` + `.xml` | [WinSW](https://github.com/winsw/winsw) — registra o backend como serviço |
| `application.properties` | config de produção (PostgreSQL `localhost:5432`, dados da loja) |
| `dados-iniciais.dump` | `pg_dump` dos dados atuais (12k produtos, 1,7k clientes, 75k lançamentos) |
| `la-vest.ico` | ícone do atalho |
| `Instalar-LaVest.bat` | instalador (roda como administrador) |
| `LEIA-ME.txt` | guia para quem instala na loja |

## Instalar (na máquina Windows)

1. Instalar o **PostgreSQL 16** ([instalador oficial](https://www.postgresql.org/download/windows/)) — anotar a senha do `postgres`.
2. Descompactar o ZIP e rodar `Instalar-LaVest.bat` **como administrador**. Ele:
   - cria o usuário `pdv` e o banco `pdv`, restaura `dados-iniciais.dump`;
   - registra o serviço `pdv-backend` (sobe com o Windows);
   - cria o atalho **"La Vest"** na área de trabalho (Edge em modo app).
3. Abrir "La Vest" → login `admin` / `admin`.

Detalhes em `LEIA-ME.txt`. A térmica deve ser a **impressora padrão** do Windows.

## Operação no Windows

- **Status/reiniciar**: `pdv-backend.exe restart` (na pasta de instalação), ou Serviços → "La Vest PDV".
- **Backup**: o equivalente do `backup-pdv.sh` no Windows é uma Tarefa Agendada rodando `pg_dump`. Configurar na instalação (pendência — hoje o backup automático só está no ambiente Linux).
- **Atualizar o sistema**: regerar o pacote aqui, parar o serviço na loja (`pdv-backend.exe stop`), trocar só o `pdv.jar`, iniciar de novo (`pdv-backend.exe start`). Não precisa reinstalar nem tocar nos dados.

## Não testado em Windows real

Os scripts foram escritos e o pacote validado **no Linux** (jar, JRE Windows,
WinSW e dump conferidos). A execução no Windows ainda **não foi testada** —
os caminhos do PostgreSQL/Edge no `.bat` podem precisar de ajuste no primeiro
uso. Testar numa máquina Windows antes de levar para a loja.
