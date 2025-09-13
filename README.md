# Guia Completo: Deploy Contínuo de API Spring Boot no GCP

Este guia adota uma abordagem "developer-first" para o deploy contínuo. Primeiro, vamos preparar e testar nossa aplicação e sua imagem Docker localmente. Com a confiança de que a aplicação funciona, partiremos para a configuração da infraestrutura na nuvem e, por fim, automatizaremos o deploy com GitHub Actions.

## Parte 1: Preparando e Testando a Aplicação Localmente

Nesta fase, focamos apenas no nosso código e no ambiente local.

### 1.1 Configurando os Perfis (Profiles) da Aplicação

Spring Profiles nos permite ter configurações diferentes para cada ambiente.

1. Arquivo src/main/resources/application.properties (Configurações Comuns)Properties
    
    Aqui vão as propriedades que não mudam entre os ambientes.
    
    ```yaml
    # Configurações comuns a todos os ambientes
    server.port=8080
    spring.application.name=minha-api
    
    # Adiciona o caminho do arquivo de log
    logging.file.path=/var/log/minha-api/
    ```
    
2. Arquivo src/main/resources/application-dev.properties (Perfil de Desenvolvimento)Properties
    
    Usado para rodar a aplicação na sua máquina local.
    
    ```yaml
    # Conecta a um banco de dados local para desenvolvimento
    spring.datasource.url=jdbc:postgresql://localhost:5432/banco_dev
    spring.datasource.username=postgres
    spring.datasource.password=sua_senha_local
    ```
    
3. Arquivo src/main/resources/application-prod.properties (Perfil de Produção)Properties
    
    Usado quando a aplicação rodar no servidor GCP. Note que a senha não está aqui.
    
    ```yaml
    # Aponta para o banco de dados que será criado na VM
    spring.datasource.url=jdbc:postgresql://localhost:5432/banco_bastion
    spring.datasource.username=postgres
    ```
    
    - [x]  testar a cominicacao e comitar para o git

### 1.2 Criando a entidade → repository → Service → Controller

### 1.4 Criando o `Dockerfile`

Este arquivo define como nossa aplicação será empacotada em uma imagem Docker. Crie-o na raiz do seu projeto.

Dockerfile

```yaml
# 1-> build
FROM maven:3.8-openjdk-17 AS builder
WORKDIR /app

# copiar nosso pom para carregar as dependencias
COPY pom.xml .
RUN mvn dependency:go-offline

# copiar o codigo gerado e compilar
COPY src ./src

RUN mvn clean package -DskipTests

# 2 -> Runtime

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copiando o .jar que foi gerado anteriormente
COPY --from=builder /app/target/app.jar app.jar

# Expor a porta do spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 1.3 Testando a Imagem Docker Localmente

Antes de pensar em nuvem, vamos garantir que nossa imagem funciona perfeitamente.

1. **Construa a imagem Docker:**Bash
    
    ```yaml
    docker build -t minha-api:local .
    ```
    
    ### Quebra do comando
    
    - **`docker build`**
    - **`t minha-api:local`**
        
        O `-t` (tag) serve para dar **nome** e **versão/tag** à imagem criada.
        
        - `minha-api` → é o **nome da imagem** (pode ser qualquer identificador).
        - `local` → é a **tag**, geralmente usada para indicar a versão ou ambiente.
            - Exemplo: `:1.0.0`, `:latest`, `:prod`, `:dev`.
            - Aqui você usou `:local`, para indicar que é uma build local de desenvolvimento.
        
        👉 Se você não colocar `-t`, o Docker dá um nome genérico (`<none>`).
        
    - **`.`  → informar qu estamos utilizando o comando a onde os arquivos se encontram**
        
        O **contexto** é tudo o que o Docker pode ver e copiar quando roda o `Dockerfile`.
        
        - Exemplo: `COPY pom.xml .` só funciona porque o `pom.xml` está dentro desse contexto (`.`).
        - Se você estivesse em outro diretório, poderia passar o caminho do projeto.
2. **Execute o contêiner localmente, ativando o perfil `dev`:**Bash
    
    ```yaml
    docker run --name api-local -d -p 8080:8080 \
    	--network host \
      -e SPRING_PROFILES_ACTIVE=dev \
      minha-api:local
    ```
    
    > O -d roda em segundo plano. O -p 8080:8080 mapeia a porta do contêiner para a sua máquina. —network host informa para utilizar a sua rede
    > 
    
    ```yaml
    curl http://localhost:8080/mensagens | jq -> para testar as mensagens local
    ```
    
3. **Verifique se a API está respondendo:**Bash
    
    ```yaml
    curl http://localhost:8080/seu-endpoint-de-teste
    ```
    
4. **Pare e remova o contêiner de teste quando terminar:**Bash
    
    ```yaml
    docker stop api-local
    docker rm api-local
    ```
    
- [x]  **Commitar para o git → build: configura o build e perfis da aplicacao**

## Parte 2: Configurando a Infraestrutura de Produção (GCP)

Agora, vamos para o Google Cloud para criar e configurar o ambiente que hospedará nossa API.

### 2.1 Criar a Máquina Virtual (VM)

Bash

```yaml
gcloud compute instances create minha-api \
  --project=[SEU_PROJETO_ID] \
  --zone=us-central1-c \
  --machine-type=e2-micro \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --tags=api-server \
  --boot-disk-size=15GB \
  --boot-disk-type=pd-balanced
```

### 2.2 Configurar Regras de Firewall

Liberamos a porta `80` (HTTP) e garantimos o acesso SSH seguro via IAP.

Bash

```yaml
# Regra para HTTP
gcloud compute firewall-rules create allow-api-http --allow tcp:80 --target-tags=api-server

# Regra para SSH via IAP
gcloud compute firewall-rules create allow-iap-ssh --allow=tcp:22 --source-ranges=35.235.240.0/20
```

### 2.3 Acessar a VM com Segurança (IAP)

Bash

```yaml
gcloud compute ssh minha-api --zone=us-central1-c --tunnel-through-iap
```

> A partir daqui, todos os comandos são executados dentro da VM.
> 

### 2.4 Instalar Dependências na VM

Bash

```yaml
# Atualiza o sistema
sudo apt-get update && sudo apt-get upgrade -y

# Instala Nginx, Docker, PostgreSQL e o Agente de Logs
sudo apt-get install -y nginx docker.io postgresql postgresql-contrib
## agente de logs do google
curl -sSO https://dl.google.com/cloudagents/add-google-cloud-ops-agent-repo.sh
sudo bash add-google-cloud-ops-agent-repo.sh --also-install
```

### 2.5 Configurar o Banco de Dados PostgreSQL

Bash

```yaml
# Acessa o shell do postgres
sudo -u postgres psql
```

Dentro do `psql`, execute:

SQL

```yaml
CREATE DATABASE banco_bastion;
-- IMPORTANTE: Substitua 'sua-senha-forte-aqui' por uma senha real e segura!
CREATE USER postgres WITH PASSWORD 'sua-senha-forte-aqui';
GRANT ALL PRIVILEGES ON DATABASE banco_bastion TO postgres;
\q
```

> Ação Crítica: Guarde essa senha! Você vai precisar dela em breve.
> 

### 2.6 Instalar e Configurar o GitHub Self-Hosted Runner

1. **Conceda permissão do Docker ao seu usuário:**Bash
    
    ```yaml
    sudo usermod -aG docker $USER
    # Saia e entre novamente no SSH para a permissão ter efeito
    exit
    gcloud compute ssh minha-api --zone=us-central1-c --tunnel-through-iap
    ```
    
2. **Siga as instruções do GitHub:**
    - No seu repositório, vá para **Settings > Actions > Runners > New self-hosted runner**.
    - Execute os comandos de **Download** e **Configure** na sua VM.
3. **Instale o Runner como um serviço para rodar em segundo plano:**Bash
    
    ```yaml
    sudo ./svc.sh install
    sudo ./svc.sh start
    ```
    

## Parte 3: Construindo o Pipeline de Deploy (CI/CD)

Com a infraestrutura pronta, voltamos ao nosso repositório para definir o pipeline de automação.

### 3.1 Criar o Segredo no GCP Secret Manager

1. Vá para o console do Google Cloud > **Secret Manager**.
2. Crie um novo segredo chamado DB_PASSWORD.
3. No valor do segredo, cole a senha do PostgreSQL que você criou no passo 2.5.
4. Teste o comando para verificar se tem acesso a secret
    
    ```yaml
    gcloud secrets versions access latest --secret='secret'
    
    ```
    

### 3.2 Criar Segredos no Secret Manager

1. No seu repositório, vá para **Settings > Secrets and variables > Actions**.
2. Crie um novo segredo:

### 3.3 Criando o Workflow (`deploy.yml`)

Na raiz do seu projeto, crie a estrutura de pastas `.github/workflows/` e, dentro dela, o arquivo `deploy.yml`.

YAML

```yaml
name: CI/CD - Deploy API to GCP VM with Secret Manager

on:
  push:
    branches: [ main ]

jobs:
  build-and-deploy:
    name: Build, Deploy and Run Container
    runs-on: self-hosted

    permissions:
      contents: 'read'
      id-token: 'write'

    steps:
      - name: 1. Checkout do Repositório
        uses: actions/checkout@v4

      - name: 2. Obter Senha do Secret Manager
        id: get_db_password
        uses: google-github-actions/get-secretmanager-secrets@v3
        with:
          secrets: |-
            db_password:projects/mpv-concentrador-xml/secrets/DB_PASSWORD/versions/latest

      - name: 3. Construir a Imagem Docker
        id: build
        run: |
          docker build -t minha-api:${{ github.sha }} .

      - name: 4. Parar e Remover Contêiner Antigo
        run: |
          docker stop api-container || true
          docker rm api-container || true

      - name: 5. Rodar o Novo Contêiner com a Senha
        run: |
          echo "Iniciando o novo contêiner em modo de produção..."
          docker run -d --name api-container --network host \
            -v /var/log/minha-api:/var/log/minha-api \
            -e SPRING_PROFILES_ACTIVE="prod" \
            -e SPRING_DATASOURCE_PASSWORD="${{ steps.get_db_password.outputs.db_password }}" \
            minha-api:${{ github.sha }}

```

## Parte 4: Configuração Final na VM

Estes são os últimos ajustes na VM para que ela possa receber e expor nossa API.

### 4.1 Configurar o Nginx como Proxy Reverso

Bash

```yaml
# Crie o arquivo de configuração
sudo nano /etc/nginx/sites-available/api.conf
```

Cole o conteúdo abaixo, **substituindo `[IP_EXTERNO_DA_SUA_VM]`** pelo IP público da sua máquina.

Nginx

```yaml
server {
    listen 80;
    server_name [IP_EXTERNO_DA_SUA_VM];
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}
```

Ative a configuração:

Bash

```yaml
sudo ln -s /etc/nginx/sites-available/api.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl restart nginx
```

### 4.2 Configurar o Ops Agent para Logs

Bash

```yaml
# Edite o arquivo de configuração do agente
sudo nano /etc/google-cloud-ops-agent/config.yaml
```

Apague todo o conteúdo e cole:

YAML

```yaml
logging:
  receivers:
    minha-api-receiver:
      type: files
      include_paths:
        - /var/log/minha-api/spring.log
    nginx-receiver:
      type: files
      include_paths:
        - /var/log/nginx/access.log
        - /var/log/nginx/error.log
  service:
    pipelines:
      api_pipeline:
        receivers: [minha-api-receiver]
      nginx_pipeline:
        receivers: [nginx-receiver]
```

Reinicie o agente para aplicar:

Bash

```yaml
sudo systemctl restart google-cloud-ops-agent
```

## Parte 5: A Hora da Verdade - O Deploy!

### 5.1 Fazendo o Push para o GitHub

Agora, envie todas as suas alterações (código da API, `Dockerfile`, `deploy.yml`, etc.) para o repositório.

Bash

```yaml
git add .
git commit -m "Configuração completa para deploy no GCP"
git push origin main
```

### 5.2 Acompanhando o Deploy

Vá para a aba **Actions** no seu repositório do GitHub e observe a mágica acontecer. O workflow será acionado, e o runner na sua VM executará cada passo.

### 5.3 Verificação Final

1. **Teste a API:**Bash
    
    ```yaml
    curl http://[IP_EXTERNO_DA_SUA_VM]/seu-endpoint
    ```
    
2. **Verifique o Contêiner na VM:**Bash
    
    ```yaml
    docker ps
    ```
    
    Você deverá ver o `api-container` rodando.
    
3. Verifique os Logs no Cloud Logging:
    
    Vá para o console do GCP em Logging > Explorador de Logs e filtre pelos logs que configuramos (minha-api-receiver, nginx-receiver).
    

---

### Próximos Passos

- **Ativar HTTPS:** Use o `certbot` para adicionar um certificado SSL/TLS gratuito.
- **Usar um Domínio:** Configure um registro DNS para apontar um domínio para o IP da sua VM.
