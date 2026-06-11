# PDV - Loja da Família

Sistema de Ponto de Venda (PDV) e Gestão para loja de roupas, calçados e perfumes.
O sistema tem foco em agilidade, utilizando operação por teclado para registrar a venda, baixar o estoque, gerenciar crediário (fiado) e imprimir o recibo em uma única ação.

## Pré-requisitos

Para rodar este projeto na sua máquina de desenvolvimento, você precisará de:

1. **Java 21** instalado (`JDK 21`).
2. **Docker** para subir o banco de dados local.

## Como rodar o projeto localmente

### 1. Subir o Banco de Dados (PostgreSQL)

O projeto usa o PostgreSQL rodando em um container Docker, mapeado na porta **5433** (para não conflitar com um PG local na 5432). O script de inicialização do Spring Boot via Flyway criará o schema automaticamente se os bancos existirem.

Rode o seguinte comando no terminal para subir o container do PostgreSQL já configurado:

```bash
docker run -d --name pdv-postgres \
  -e POSTGRES_USER=pdv \
  -e POSTGRES_PASSWORD=pdv \
  -p 5433:5432 \
  postgres:latest
```

Após o container subir, crie os bancos de dados de `dev` e `test`:

```bash
docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv;"
docker exec -it pdv-postgres psql -U pdv -c "CREATE DATABASE pdv_test;"
```

### 2. Configurar o Java (se necessário)

Certifique-se de que o seu `JAVA_HOME` aponta para o JDK 21. Se não estiver configurado globalmente, você pode exportar a variável no terminal antes de rodar o projeto:

```bash
# Exemplo de export no Linux:
export JAVA_HOME=~/tools/jdk-21.0.11+10
```

### 3. Rodar a aplicação

O projeto utiliza o Maven Wrapper, então não é necessário ter o Maven instalado globalmente. Na pasta raiz do projeto, execute:

```bash
./mvnw spring-boot:run
```

Se tudo estiver correto, o backend subirá na porta **8080**.

### 4. Acessar o Sistema

Abra o seu navegador e acesse:
 **[http://localhost:8080](http://localhost:8080)**

---

## Como rodar os Testes

Os testes de integração utilizam o banco de dados `pdv_test` (que configuramos no passo 1) para validar toda a regra do sistema, de ponta a ponta.

Para rodar os testes, execute o comando:

```bash
./mvnw test
```

## Arquitetura Básica
- **Backend:** Java 21, Spring Boot 3, Spring Data JPA, Hibernate, PostgreSQL, Flyway.
- **Frontend:** HTML, CSS e JavaScript Vanilla (servidos no `/src/main/resources/static`).

*Para entender mais sobre o banco de dados, regras do modelo ou os motivos de arquitetura, consulte a documentação técnica (artifacts/documentacao_arquitetura.md) gerada neste repositório.*
