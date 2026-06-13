@echo off
REM ============================================================
REM  Instalador do La Vest PDV no Windows 10
REM  Rode com BOTAO DIREITO -> "Executar como administrador"
REM ============================================================
setlocal enabledelayedexpansion
set BASE=%~dp0
set SENHA=pdv

echo.
echo === La Vest PDV - instalacao ===
echo.

REM --- 1) localizar o PostgreSQL instalado ---
set PGBIN=
for /d %%v in ("C:\Program Files\PostgreSQL\*") do set PGBIN=%%v\bin
if "%PGBIN%"=="" (
  echo ERRO: PostgreSQL nao encontrado em C:\Program Files\PostgreSQL
  echo Instale o PostgreSQL antes ^(veja o LEIA-ME^).
  pause & exit /b 1
)
echo PostgreSQL: %PGBIN%

REM --- 2) banco: usuario, base e dados iniciais ---
echo.
echo Criando usuario e banco... ^(vai pedir a senha do 'postgres' definida na instalacao do PostgreSQL^)
"%PGBIN%\psql" -U postgres -c "CREATE ROLE pdv LOGIN PASSWORD '%SENHA%';"
"%PGBIN%\psql" -U postgres -c "CREATE DATABASE pdv OWNER pdv;"

echo Restaurando os dados da loja...
set PGPASSWORD=%SENHA%
"%PGBIN%\pg_restore" -U pdv -h localhost -d pdv --no-owner "%BASE%dados-iniciais.dump"
set PGPASSWORD=

REM --- 3) servico do backend (sobe com o Windows) ---
echo.
echo Instalando o servico do sistema...
"%BASE%pdv-backend.exe" stop  >nul 2>&1
"%BASE%pdv-backend.exe" uninstall >nul 2>&1
"%BASE%pdv-backend.exe" install
"%BASE%pdv-backend.exe" start

REM --- 4) atalho na area de trabalho (abre o app via Edge) ---
echo.
echo Criando atalho 'La Vest' na area de trabalho...
set EDGE=C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe
if not exist "%EDGE%" set EDGE=C:\Program Files\Microsoft\Edge\Application\msedge.exe
powershell -NoProfile -Command ^
  "$s=(New-Object -COM WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\La Vest.lnk');" ^
  "$s.TargetPath='%EDGE%';" ^
  "$s.Arguments='--app=http://localhost:8080 --kiosk-printing';" ^
  "$s.IconLocation='%BASE%la-vest.ico';" ^
  "$s.Save()"

echo.
echo ============================================================
echo  Instalado! Aguarde uns 10 segundos e abra 'La Vest' na
echo  area de trabalho. Usuario: admin  Senha: admin
echo ============================================================
pause
