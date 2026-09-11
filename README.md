# SourceAFIS — aplicação biométrica em Java

Projeto de reconhecimento de impressões digitais com **SourceAFIS 3.18.1**, **Spring Boot 4.0.8**, **Java 21** e **PostgreSQL 17**. O ambiente local pode ser executado com Docker Compose ou com a aplicação no IntelliJ e o banco no Docker.

A biblioteca SourceAFIS extrai templates e compara impressões digitais na JVM. O PostgreSQL armazena cadastros, templates serializados e histórico. As imagens originais ficam em armazenamento privado.

## Estado atual

Estão implementados a infraestrutura Docker, os perfis `local` e `cloud`, a migração inicial do banco, os serviços básicos de extração/comparação e armazenamento de imagens e os endpoints de saúde do Spring Boot Actuator.

Os endpoints de cadastro e comparação, a autenticação, a autorização e o fluxo transacional de persistência ainda precisam ser desenvolvidos. O perfil `cloud` prepara a aplicação para um futuro deploy; este repositório ainda não provisiona recursos de produção.

A captura pelo leitor biométrico depende do SDK do fabricante no computador do usuário. Essa integração não está implementada.

## Estrutura

| Caminho | Finalidade |
| --- | --- |
| `pom.xml` e `src/` | Biblioteca SourceAFIS e seus testes |
| `api/pom.xml` | Projeto Maven da aplicação Spring Boot |
| `api/src/main/java/` | Inicialização e serviços básicos |
| `api/src/main/resources/application*.yaml` | Configuração comum e perfis |
| `api/src/main/resources/db/migration/` | Migrações SQL do Flyway |
| `api/src/test/` | Testes da aplicação |
| `compose.yaml` | API e PostgreSQL locais |
| `Dockerfile` | Compilação e imagem de execução da API |
| `.env.example` | Exemplo de variáveis do Compose |
| `.data/images/` | Imagens locais, ignoradas pelo Git |

Há dois projetos Maven: a biblioteca na raiz e a aplicação em `api/`. Instale a biblioteca no repositório Maven local antes de compilar a API. O Dockerfile já executa essa sequência.

## Pré-requisitos

Para executar tudo em containers, instale e inicie o Docker Desktop com **containers Linux** e Docker Compose disponíveis. Java e Maven no computador não são necessários nessa modalidade.

Para executar a API ou os testes fora do Docker, configure **JDK 21** e **Maven 3.9**. No IntelliJ, é possível usar o Maven integrado.

Os comandos abaixo usam **PowerShell na raiz do repositório**.

```powershell
docker version
docker compose version
```

Para o fluxo com Java/Maven local, verifique também:

```powershell
java -version
mvn -version
```

O Maven integrado do IntelliJ não adiciona automaticamente `mvn` ao `PATH` do terminal. Se o comando não estiver disponível, instale/configure Maven ou execute as etapas pela janela Maven do IntelliJ.

## Iniciar localmente com Docker

### 1. Configurar o ambiente

Crie `.env` apenas se ainda não existir:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

Revise os valores:

```dotenv
POSTGRES_DB=sourceafis
POSTGRES_USER=sourceafis
POSTGRES_PASSWORD=sourceafis_local_only
POSTGRES_PORT=5432
API_PORT=8080
PGADMIN_EMAIL=admin@example.com
PGADMIN_PASSWORD=pgadmin_local_only
PGADMIN_PORT=5050
```

As credenciais do exemplo são exclusivas para desenvolvimento. `.env` é ignorado pelo Git e excluído do contexto de construção da imagem.

### 2. Construir e iniciar

```powershell
docker compose config --quiet
docker compose up --build -d
docker compose ps
```

A primeira execução baixa imagens e dependências Maven. O Compose aguarda o PostgreSQL ficar saudável antes de iniciar a API. O Flyway aplica as migrações pendentes na inicialização da aplicação.

### 3. Verificar a aplicação

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

O campo `status` deve ser `UP`. O endpoint inclui a verificação da conexão com o banco, mas não testa um fluxo completo de cadastro ou comparação biométrica.

Para acompanhar logs, use outro terminal ou pressione `Ctrl+C` para encerrar o acompanhamento antes de executar outros comandos:

```powershell
docker compose logs -f api
```

### Endereços e persistência

| Recurso | Configuração local padrão |
| --- | --- |
| Saúde da API | `http://localhost:8080/actuator/health` |
| PostgreSQL no computador | `localhost:5432`, banco `sourceafis` |
| PostgreSQL acessado pela API no Docker | `postgres:5432` |
| Dados do banco | Volume `postgres-data`, com prefixo do projeto Compose |
| Imagens originais | `.data/images/`, montado como `/app/images` |

As portas são publicadas apenas em `127.0.0.1`. Se alterar `API_PORT`, ajuste a URL da API. Se alterar `POSTGRES_PORT`, ajuste os clientes no computador; a conexão entre containers continua usando `5432`.

### Administrar o banco pelo navegador

O Compose inclui o pgAdmin em **http://localhost:5050**. Para iniciar somente o
painel e o banco, execute `docker compose up -d pgadmin`. Em um `.env` existente,
adicione `PGADMIN_EMAIL`, `PGADMIN_PASSWORD` e `PGADMIN_PORT` conforme o exemplo acima.

Entre com `admin@example.com` e `pgadmin_local_only` (ou os valores do `.env`).
Cadastre uma conexão PostgreSQL com host **`postgres`**, porta `5432`, banco e
usuário `sourceafis` e a senha configurada em `POSTGRES_PASSWORD`.
Veja o [passo a passo do pgAdmin](api/README.md#acessar-o-postgresql-pelo-pgadmin)
para navegar pelas tabelas. As configurações do painel persistem no volume
`pgadmin-data`; `docker compose down -v` também remove esse volume.

### Parar e iniciar novamente

```powershell
docker compose down
docker compose up -d
```

`docker compose down` preserva o banco e as imagens. A opção `-v` remove o volume do banco e seus dados. Alterar `POSTGRES_PASSWORD` em `.env` **não altera a senha de um banco já inicializado**.

Após alterações no código ou nas configurações empacotadas, use `docker compose up --build -d`.

## Iniciar pelo terminal ou IntelliJ

### 1. Manter o banco no Docker

Prepare `.env` conforme a seção anterior. Se a API estiver executando no Docker, pare esse serviço para liberar a porta `8080`:

```powershell
docker compose stop api
docker compose up -d postgres
```

### 2. Instalar a biblioteca local

```powershell
mvn -B -ntp "-Dmaven.javadoc.skip=true" "-Dgpg.skip=true" install
```

Esse comando compila a biblioteca, executa seus testes e instala o artefato no repositório Maven local. As opções desabilitam a geração de Javadoc e a assinatura GPG, desnecessárias para desenvolvimento.

### 3. Executar pelo terminal

```powershell
$env:SPRING_PROFILES_ACTIVE = "local"
$env:DB_URL = "jdbc:postgresql://localhost:5432/sourceafis"
$env:DB_USERNAME = "sourceafis"
$env:DB_PASSWORD = "sourceafis_local_only"
$env:IMAGE_STORAGE_PATH = Join-Path (Get-Location).Path ".data/images"
mvn -f api/pom.xml spring-boot:run
```

Ajuste banco, porta, usuário e senha aos valores do seu `.env`. O Spring Boot **não lê `.env` automaticamente**; esse arquivo é usado pelo Docker Compose. As variáveis acima valem para a sessão atual do PowerShell.

O caminho absoluto de imagens permite compartilhar o mesmo diretório com o Docker, independentemente do diretório de trabalho usado pelo Maven.

### Alternativa: executar pelo IntelliJ

1. Configure o SDK do projeto e o JDK do executor Maven como Java 21.
2. Importe o `pom.xml` da raiz e adicione `api/pom.xml` como projeto Maven.
3. Execute `install` na biblioteca com as opções indicadas acima.
4. Crie uma configuração para `com.machinezoo.sourceafis.api.SourceAfisApplication`, usando o classpath de `sourceafis-api`.
5. Defina a raiz do repositório como diretório de trabalho e configure as variáveis do exemplo anterior. Informe o caminho de imagens resolvido, sem a expressão PowerShell.
6. Inicie a aplicação e consulte `/actuator/health`.

O perfil `local` é o padrão. Sem sobrescritas, usa `localhost:5432/sourceafis`, usuário `sourceafis`, senha `sourceafis_local_only` e imagens em `./.data/images`.

## Testes

### Biblioteca e API

Execute na raiz, nesta ordem. Prossiga para o segundo comando apenas se o primeiro terminar com `BUILD SUCCESS`:

```powershell
mvn -B -ntp "-Dmaven.javadoc.skip=true" "-Dgpg.skip=true" install
mvn -B -ntp -f api/pom.xml verify
```

Os testes atuais não exigem PostgreSQL ou Docker. Verificam o algoritmo, a extração/comparação na API, a persistência de imagens em diretório temporário e a rejeição de chaves inválidas. Os relatórios ficam em `target/surefire-reports/` e `api/target/surefire-reports/`.

Após instalar a biblioteca, execute somente os testes da API com:

```powershell
mvn -f api/pom.xml test
```

O Dockerfile usa `-DskipTests`: construir a imagem não substitui essa etapa de testes.

### Verificação com PostgreSQL real

Com os serviços iniciados:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose exec postgres psql -U sourceafis -d sourceafis -c "SELECT version, description, success FROM flyway_schema_history;"
docker compose exec postgres psql -U sourceafis -d sourceafis -c "\dt"
```

Ajuste usuário e banco se necessário. A migração `1`, `biometric schema`, deve aparecer com `success = t`. Essa verificação confirma a inicialização do esquema; ainda não existe uma suíte automatizada de integração com PostgreSQL.

## Modelo de armazenamento

| Tabela | Conteúdo |
| --- | --- |
| `organization` | Organizações |
| `person` | Pessoas e matrícula única por organização |
| `finger` | Dedos cadastrados |
| `fingerprint_sample` | DPI, data, dispositivo de captura e chave da imagem |
| `fingerprint_template` | Template em `bytea` e versão do SourceAFIS |
| `comparison_history` | Score, resultado, limite e versões utilizadas |

`PrivateImageStorage` grava imagens originais com chaves UUID, sem endpoint público para esses arquivos. O esquema está em [V1__biometric_schema.sql](api/src/main/resources/db/migration/V1__biometric_schema.sql). Os fluxos que conectam os registros aos serviços ainda precisam ser implementados.

## Preparação para produção

### Arquitetura prevista no Google Cloud

| Componente | Destino |
| --- | --- |
| API Spring Boot e SourceAFIS | Cloud Run |
| PostgreSQL | Cloud SQL for PostgreSQL |
| Imagens originais | Bucket privado do Cloud Storage montado na API |
| Senhas | Secret Manager |
| Imagem Docker | Artifact Registry |

O Compose atual é destinado ao desenvolvimento. O perfil `cloud` não cria banco, bucket, rede, permissões ou serviço Cloud Run.

### Variáveis da aplicação

| Variável | Configuração em produção |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `cloud`, explicitamente |
| `DB_URL` | URL JDBC, por exemplo `jdbc:postgresql://IP_PRIVADO:5432/sourceafis`; ajustar TLS ao ambiente |
| `DB_USERNAME` | Usuário dedicado da aplicação |
| `DB_PASSWORD` | Senha injetada pelo Secret Manager |
| `IMAGE_STORAGE_PATH` | Caminho do bucket montado, por exemplo `/app/images` |
| `DB_POOL_SIZE` | Máximo de conexões por instância; padrão `5` |
| `PORT` | Porta fornecida pelo Cloud Run; padrão da aplicação `8080` |

No perfil `cloud`, a URL, as credenciais do banco e o caminho de imagens são obrigatórios. `POSTGRES_*` e `API_PORT` pertencem ao Compose local e não configuram diretamente a aplicação em produção.

### Recursos a preparar

1. Crie o banco no Cloud SQL e configure a conexão por IP privado. O Cloud Run precisa alcançar a VPC do banco por Direct VPC egress ou um conector Serverless VPC Access. A aplicação usa JDBC via TCP e não inclui o Cloud SQL Java Connector. Consulte a [conexão do Cloud Run com PostgreSQL](https://docs.cloud.google.com/sql/docs/postgres/connect-run).
2. Crie e monte um bucket privado no caminho de `IMAGE_STORAGE_PATH`. A imagem executa com o usuário `app`; configure as permissões e, quando necessário, `uid` e `gid` do volume para permitir leitura e gravação. Valide a escrita com a imagem final. Veja a [configuração de volumes Cloud Storage](https://docs.cloud.google.com/run/docs/configuring/services/cloud-storage-volume-mounts).
3. Configure a identidade do serviço com acesso ao bucket e ao segredo utilizado. Injete `DB_PASSWORD` conforme a [configuração de segredos no Cloud Run](https://docs.cloud.google.com/run/docs/configuring/services/secrets).
4. Dimensione conexões considerando `máximo de instâncias × DB_POOL_SIZE`, incluindo revisões simultâneas durante atualizações e conexões administrativas e de migração.

Informar `/app/images` sem montar o bucket grava no sistema de arquivos do container e não garante preservação dos originais.

### Construção e entrega futura

Execute os testes antes de gerar a imagem de uma versão:

```powershell
docker build -t sourceafis-api:0.1.0 .
```

No futuro pipeline, publique a imagem com identificação imutável no Artifact Registry e use essa referência no Cloud Run. Configure variáveis, segredos, rede e volume antes de iniciar a revisão. Valide primeiro em homologação e só então direcione tráfego de produção.

O Flyway executa migrações na inicialização, portanto o usuário configurado precisa de permissões para alterar o esquema. Antes da produção, decida se as migrações continuarão nesse fluxo ou serão executadas por uma etapa dedicada com credenciais próprias. Crie novas migrações para alterações posteriores; preserve as já aplicadas.

### Pendências antes de receber dados reais

- Implementar autenticação, autorização por organização e endpoints de negócio; manter a implantação com acesso restrito até essa etapa estar validada.
- Garantir consistência entre banco e armazenamento de imagens, incluindo falhas parciais.
- Definir proteção, retenção e exclusão de imagens/templates, backups e testes de restauração do banco e dos originais.
- Validar limites de comparação com amostras representativas e registrar as versões do algoritmo e do limite.
- Testar migrações, concorrência, memória/CPU e compatibilidade de templates em homologação.
- Configurar monitoramento, logs sem conteúdo biométrico e recuperação. O endpoint de saúde atual não verifica escrita no armazenamento de imagens.

## Problemas comuns

| Sintoma | Verificação |
| --- | --- |
| Docker não conecta | Inicie o Docker Desktop e confirme containers Linux |
| Porta ocupada | Pare a API no Docker antes de iniciar pelo IntelliJ ou altere a porta |
| `mvn` não encontrado | Configure Maven no `PATH` ou use o Maven integrado do IntelliJ |
| SourceAFIS não encontrado | Execute `install` na raiz e use o mesmo repositório Maven local para os dois projetos |
| Falha de autenticação no banco | Confira as credenciais; editar `.env` não redefine a senha do volume existente |
| Imagens em outra pasta | Use `IMAGE_STORAGE_PATH` absoluto ou ajuste o diretório de trabalho |
| `404` na raiz da API | Consulte `/actuator/health`; não há página inicial nem endpoints de negócio implementados |

## Documentação e licença

Consulte as [notas da API](api/README.md), a [documentação oficial do SourceAFIS](https://sourceafis.machinezoo.com/java) e as [orientações de contribuição](CONTRIBUTING.md).

Distribuído sob a licença [Apache 2.0](LICENSE). Os créditos da biblioteca estão em [COPYRIGHT](COPYRIGHT).
