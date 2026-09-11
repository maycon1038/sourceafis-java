# API biométrica

Base em Java 21 e Spring Boot 4.0.8, usando o SourceAFIS deste repositório.
PostgreSQL 17 armazena cadastros, templates `bytea` e histórico. A extração e
comparação são executadas pelo serviço `BiometricEngine` na JVM.

## Endpoints: disponibilidade atual

URL base local: `http://localhost:8080`.

**Os endpoints de cadastro e comparação ainda não estão implementados.** O código
atual contém serviços Java, mas não possui controllers HTTP para essas operações.
As rotas de negócio abaixo são uma **proposta de contrato para implementação**;
seus exemplos ainda não podem ser usados como testes funcionais da aplicação.

| Método | Rota | Situação |
| --- | --- | --- |
| `GET` | `/actuator/health` | Disponível: saúde da aplicação e conexão com o banco |
| `GET` | `/actuator/health/liveness` | Disponível: estado de vida da aplicação |
| `GET` | `/actuator/health/readiness` | Disponível: prontidão da aplicação; não inclui o banco por padrão |
| `POST` | `/api/v1/organizations` | Proposta: cadastrar organização |
| `POST` | `/api/v1/persons` | Proposta: cadastrar pessoa |
| `POST` | `/api/v1/persons/{personId}/fingerprints` | Proposta: cadastrar amostra e extrair template |
| `POST` | `/api/v1/comparisons` | Proposta: comparar dois templates cadastrados, 1:1 |

## Contrato proposto: cadastro

### Organização e pessoa

Primeiro, cadastrar uma organização com `POST /api/v1/organizations`, enviando
`Content-Type: application/json`:

```json
{
  "name": "Organização de exemplo"
}
```

Resposta prevista: `201 Created`, com `id` (UUID) e `name`.
Usar o `id` retornado no cadastro da pessoa, `POST /api/v1/persons`:

```json
{
  "organizationId": "11111111-1111-4111-8111-111111111111",
  "registrationNumber": "MAT-0001",
  "name": "Pessoa de exemplo"
}
```

Todos os campos são obrigatórios. A organização deve existir, e a matrícula deve
ser única dentro dela. Resposta prevista: `201 Created`, com `id` (UUID) e os
campos cadastrados. Matrícula duplicada deve retornar `409 Conflict`.

Os UUIDs nesta documentação são ilustrativos; substituí-los pelos IDs retornados
pela futura API. A autorização deve validar o acesso à organização em cada operação.
O mecanismo de autenticação e o formato das credenciais ainda serão definidos.

### Cadastrar impressão digital

**Rota proposta:** `POST /api/v1/persons/{personId}/fingerprints`.

Requisição `multipart/form-data`, com os seguintes campos:

| Campo | Tipo | Regra proposta |
| --- | --- | --- |
| `personId` | UUID na URL | Pessoa existente e acessível ao solicitante |
| `image` | Arquivo binário | Imagem original não vazia; PNG como formato inicial do contrato |
| `fingerPosition` | Inteiro | De 1 a 10, conforme a tabela abaixo |
| `dpi` | Inteiro | Resolução real da captura, maior que zero |
| `capturedAt` | Texto ISO 8601 | Data/hora da captura com fuso, por exemplo `2026-09-10T15:00:00Z` |
| `captureDevice` | Texto | Identificação não vazia do dispositivo de captura |

Todos os campos são obrigatórios. A configuração atual do servidor limita o
arquivo a `10MB` e a requisição multipart inteira a `10MB`; os metadados e o
envelope multipart também ocupam espaço. A validação de formato ainda será implementada.

| Dedo | Mão direita | Mão esquerda |
| --- | --- | --- |
| Polegar | 1 | 6 |
| Indicador | 2 | 7 |
| Médio | 3 | 8 |
| Anelar | 4 | 9 |
| Mínimo | 5 | 10 |

Exemplo para a futura implementação, em PowerShell com `curl.exe`:

```powershell
curl.exe -X POST "http://localhost:8080/api/v1/persons/22222222-2222-4222-8222-222222222222/fingerprints" -F "image=@src/test/resources/com/machinezoo/sourceafis/probe.png;type=image/png" -F "fingerPosition=2" -F "dpi=500" -F "capturedAt=2026-09-10T15:00:00Z" -F "captureDevice=leitor-exemplo"
```

Executar a partir da raiz do repositório, após cadastrar a pessoa e substituir o
UUID. O valor `500` é ilustrativo: usar a resolução real informada pela captura.
Não definir manualmente o cabeçalho multipart; o cURL gera o delimitador.

Resposta proposta: `201 Created`.

```json
{
  "sampleId": "33333333-3333-4333-8333-333333333333",
  "templateId": "44444444-4444-4444-8444-444444444444",
  "personId": "22222222-2222-4222-8222-222222222222",
  "fingerPosition": 2,
  "dpi": 500,
  "sourceafisVersion": "3.18.1"
}
```

Comportamento esperado: localizar ou criar o dedo da pessoa, preservar a imagem
original no armazenamento privado, extrair o template e gravar amostra e template
versionado no PostgreSQL. Um novo cadastro do mesmo dedo cria outra amostra.
O servidor deve tratar falhas parciais entre arquivos e banco antes de confirmar
sucesso. A resposta não deve expor o binário do template nem uma URL pública da imagem.

## Contrato proposto: comparação 1:1

**Rota proposta:** `POST /api/v1/comparisons`, com `Content-Type: application/json`.

Esta proposta compara dois templates previamente cadastrados. O envio de uma nova
captura diretamente para verificação e a busca 1:N não estão definidos neste contrato.

```json
{
  "probeTemplateId": "44444444-4444-4444-8444-444444444444",
  "candidateTemplateId": "55555555-5555-4555-8555-555555555555"
}
```

Os dois IDs são obrigatórios e devem apontar para templates acessíveis dentro da
organização autorizada. O servidor deve validar a compatibilidade com a versão do
SourceAFIS em execução e solicitar reextração quando necessário.

O limite de decisão e sua versão devem vir de uma política definida no servidor,
não da requisição. Essa política ainda não está implementada nem calibrada.

Resposta proposta: `201 Created`, pois a operação cria um registro de histórico.
Os valores abaixo são apenas ilustrativos e **não definem um limite recomendado**:

```json
{
  "comparisonId": "66666666-6666-4666-8666-666666666666",
  "probeTemplateId": "44444444-4444-4444-8444-444444444444",
  "candidateTemplateId": "55555555-5555-4555-8555-555555555555",
  "score": 72.5,
  "threshold": 50.0,
  "thresholdVersion": "exemplo-v1",
  "matched": true,
  "sourceafisVersion": "3.18.1",
  "comparedAt": "2026-09-10T15:01:00Z"
}
```

`matched` deve corresponder a `score >= threshold`. O score é uma medida de
similaridade, não uma porcentagem. O servidor calcula o score na JVM e persiste
o resultado, os templates referenciados e as versões em `comparison_history`.

### Erros previstos para as rotas de negócio

Esta tabela também faz parte da proposta; os mapeamentos HTTP ainda não existem.

| Status | Situação prevista |
| --- | --- |
| `400 Bad Request` | Campo obrigatório ausente, UUID inválido, DPI ou posição inválidos |
| `401 Unauthorized` | Credenciais ausentes ou inválidas, após implementar autenticação |
| `403 Forbidden` | Operação não permitida ao solicitante |
| `404 Not Found` | Organização, pessoa ou template não encontrado no escopo autorizado |
| `409 Conflict` | Matrícula duplicada ou versão de template incompatível |
| `413 Content Too Large` | Upload excede o limite configurado |
| `415 Unsupported Media Type` | Tipo de conteúdo da requisição não suportado |
| `422 Unprocessable Content` | Imagem recebida não pode ser decodificada ou processada |
| `503 Service Unavailable` | Banco ou armazenamento temporariamente indisponível |

### Sequência de validação após implementar os endpoints

1. Cadastrar organização e pessoa e guardar os IDs retornados.
2. Cadastrar duas amostras e guardar seus `templateId`.
3. Comparar os templates e verificar `matched`, score, limite e versões.
4. Conferir a persistência do histórico e dos originais privados.
5. Testar matrícula duplicada, arquivos inválidos, acesso entre organizações e
   falhas parciais de persistência.

Até essa implementação, usar os testes de serviços e a verificação de saúde
descritos no [README principal](../README.md#testes).

## Executar com Docker

Na raiz do repositório, com Docker Desktop iniciado e containers Linux habilitados:

```powershell
Copy-Item .env.example .env
docker compose up --build -d
docker compose logs -f api
Invoke-RestMethod http://localhost:8080/actuator/health
```

O endpoint deve retornar `status: UP`. O Flyway cria o esquema na primeira
inicialização. O PostgreSQL fica no volume `postgres-data`; as imagens originais
ficam em `.data/images`, fora do Git e sem endpoint público. As portas são
publicadas apenas em localhost. As credenciais do exemplo são para uso local.

```powershell
docker compose down
```

Esse comando preserva o banco e as imagens. `docker compose down -v` apaga o volume
do banco. Não use essa opção para reiniciar um ambiente com dados que deseja manter.
Alterar a senha em `.env` não altera a senha de um banco já inicializado.

## Acessar o PostgreSQL pelo pgAdmin

O Compose inclui o pgAdmin 4, acessível em **http://localhost:5050**.
Para ambientes existentes, adicione ao `.env` as variáveis abaixo sem substituir
as credenciais já configuradas do PostgreSQL:

```dotenv
PGADMIN_EMAIL=admin@example.com
PGADMIN_PASSWORD=pgadmin_local_only
PGADMIN_PORT=5050
```

Inicie a interface e o banco:

```powershell
docker compose up -d pgadmin
```

Entre no navegador com `admin@example.com` e `pgadmin_local_only` (ou os valores
definidos no seu `.env`). Essas credenciais são do painel, separadas das do banco.

No painel, selecione **Servers → Register → Server**. Na aba **General**, use
o nome `SourceAFIS local`. Na aba **Connection**, preencha:

| Campo | Valor padrão |
| --- | --- |
| Host name/address | `postgres` |
| Port | `5432` |
| Maintenance database | `sourceafis` |
| Username | `sourceafis` |
| Password | `sourceafis_local_only` |

Use os valores `POSTGRES_DB`, `POSTGRES_USER` e `POSTGRES_PASSWORD` do `.env` caso
tenham sido alterados. O host é `postgres`, pois o pgAdmin acessa o banco pela rede
interna do Docker. `localhost` dentro do pgAdmin aponta para seu próprio container.

Após salvar, expanda **Servers → SourceAFIS local → Databases → sourceafis →
Schemas → public → Tables**. Selecione uma tabela para inspecionar **Columns**,
**Constraints** e **Indexes**. Use **View/Edit Data → All Rows** para consultar
registros ou **Query Tool** para executar SQL.

Os cadastros de conexão do painel ficam no volume `pgadmin-data`. Alterar as
credenciais iniciais no `.env` não redefine a conta de um volume já inicializado.
`docker compose down` preserva esse volume; `docker compose down -v` remove os
volumes do painel e do PostgreSQL. O painel é publicado apenas em localhost e
destina-se ao desenvolvimento local.

Referência: [pgAdmin em Docker](https://www.pgadmin.org/docs/pgadmin4/latest/container_deployment.html).

## Executar no IntelliJ

1. Configure um JDK 21 e o Maven integrado do IntelliJ.
2. Na raiz, execute `docker compose up -d postgres`.
3. Execute `mvn -DskipTests "-Dmaven.javadoc.skip=true" "-Dgpg.skip=true" install`
   no projeto Maven da raiz para instalar a biblioteca local.
4. Adicione `api/pom.xml` como projeto Maven no IntelliJ.
5. Execute `com.machinezoo.sourceafis.api.SourceAfisApplication` com perfil `local`.
   Use a raiz do repositório como diretório de trabalho para compartilhar `.data/images`.

O perfil `local` usa `localhost:5432/sourceafis`, usuário `sourceafis` e senha
`sourceafis_local_only`. Se mudar `.env`, configure também `DB_URL`, `DB_USERNAME`
e `DB_PASSWORD` na execução do IntelliJ: o Spring não lê `.env` automaticamente.
Para iniciar por terminal, use `mvn -f api/pom.xml spring-boot:run`.
Execute os testes da API com `mvn -f api/pom.xml test`.

## Perfil cloud

A imagem Docker pode ser usada no Cloud Run com:

| Variável | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `cloud` |
| `DB_URL` | URL JDBC do Cloud SQL PostgreSQL acessível pela rede privada |
| `DB_USERNAME` | Usuário do banco |
| `DB_PASSWORD` | Senha injetada pelo Secret Manager |
| `IMAGE_STORAGE_PATH` | Caminho do volume de um bucket privado Cloud Storage |
| `PORT` | Porta fornecida pelo Cloud Run |
| `DB_POOL_SIZE` | Conexões por instância; padrão 5 |

Configure a conectividade privada com Cloud SQL e monte o bucket no Cloud Run
antes de iniciar. O perfil exige as variáveis acima (exceto as que têm padrão);
não provisiona recursos Google Cloud. Não use o disco efêmero do Cloud Run para
preservar imagens. `PrivateImageStorage` usa chaves UUID e acessa o volume montado.

## Esquema inicial e limites desta configuração

`V1__biometric_schema.sql` cria organização, pessoa (matrícula única por organização),
dedo, amostra (DPI, captura, dispositivo e chave da imagem), template versionado e
histórico (score, resultado, limite e versões). A posição do dedo usa a convenção
1–5: polegar ao mínimo direitos; 6–10: polegar ao mínimo esquerdos.
Os IDs UUID serão gerados pela aplicação. As exclusões de registros referenciados
são bloqueadas pelo banco.

Esta etapa entrega infraestrutura, esquema e serviços básicos. Endpoints de
cadastro/comparação, autenticação e o fluxo transacional de persistência ainda
precisam ser implementados. O leitor continuará conectado ao computador do usuário,
com captura pelo SDK do fabricante. Antes de cadastrar imagens reais, defina
controle de acesso e retenção dos originais. Preserve a versão do SourceAFIS junto
ao template e reextraia os originais quando uma atualização exigir isso.

Referências: [Spring Boot](https://docs.spring.io/spring-boot/4.0/reference/using/build-systems.html)
e [migrações Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html).
