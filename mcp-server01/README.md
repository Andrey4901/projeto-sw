<!--
Markdown Cheatsheet
https://github.com/adam-p/markdown-here/wiki/markdown-cheatsheet
-->

## 🚀 MCP Server: Exemplo 🚀

Este projeto demonstra o uso do Protocolo de Contexto do Modelo ([MCP](https://modelcontextprotocol.io/docs/2026-07-28/getting-started/intro) - Model Context Protocol) usando o [MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk).

Com este SDK deve-ser programar todos os detalhes, tanto do servidor quanto do cliente.

A camada de transporte administra a comunicação entre cliente e servidor, entre outras tarefas, podendo ser STDIO ou *Streamable* HTTP.

Neste caso, é demonstrado o uso de STDIO como modelo de comunicação entre o cliente e o servidor.

O MCP define três primitivas básicas: *tools* (ferramentas), *resources* (recursos) e *prompts*.

Nosso projeto exemplifica o uso de *tools*.

### 📄 Ferramentas para metadados de PDF

O servidor oferece duas ferramentas MCP para consultar e alterar metadados padrão de arquivos PDF. As ferramentas usam Apache PDFBox e operam somente em arquivos localizados na pasta de trabalho autorizada.

| Ferramenta | Parâmetros | Resultado |
| --- | --- | --- |
| `ler_metadados_pdf` | `filePath` (texto obrigatório): caminho relativo à pasta de trabalho ou caminho absoluto dentro dela | Retorna título, autor, assunto, palavras-chave, criador, produtor, data de criação e data de modificação. Campos ausentes são exibidos como “não informado”. |
| `atualizar_metadados_pdf` | `filePath` (texto obrigatório) e um ou mais campos opcionais: `title`, `author`, `subject`, `keywords`, `creator`, `creationDate`, `modificationDate` | Grava os campos informados no PDF e retorna os metadados atualizados. Campos omitidos permanecem como estavam. |

O campo `filePath` deve apontar para um arquivo existente com extensão `.pdf`. Caminhos que tentem sair da pasta de trabalho são rejeitados. Um PDF inexistente, inválido, protegido por senha ou sem permissão de gravação resulta em erro da ferramenta, sinalizado ao cliente MCP. Uma atualização sem nenhum campo de metadado também resulta em erro.

Por padrão, a pasta autorizada é `pdf-workspace` dentro do diretório de execução do servidor. Ela pode ser configurada pela propriedade Java `-Dmcp.pdf.workspace=C:\\caminho\\para\\pdfs` ou pela variável de ambiente `MCP_PDF_WORKSPACE`. Coloque os arquivos PDF nessa pasta antes de chamar uma ferramenta. Para caminhos absolutos, o arquivo ainda precisa estar dentro da pasta configurada.

Os campos `title`, `author`, `subject`, `keywords`, `creator`, `creationDate` e `modificationDate` são editáveis. Para as datas, informe ISO 8601 com fuso horário, por exemplo `2026-11-08T12:30:00Z` ou `2026-11-08T09:30:00-03:00`; a resposta sempre apresenta as datas normalizadas em UTC. Se o PDF não contiver uma dessas datas, a leitura exibirá “não informado”. O campo `producer` é apresentado na leitura, mas não é alterado pela ferramenta, pois identifica o software que produziu o arquivo PDF.

A ferramenta atualiza o próprio arquivo PDF. Faça uma cópia de segurança antes de alterar documentos importantes; uma alteração pode invalidar assinaturas digitais existentes.

### 📌 Executar a aplicação 📌
Este é um projeto único para duas aplicações: servidor e cliente. O servidor é executado a partir da classe `br.edu.ifpr.mcp_server.main.McpServer01SyncStdio`. Já o cliente é executado a partir da classe `br.edu.ifpr.mcp_server.main.McpClient01SyncStdio`.

Inicialmente, é preciso gerar a aplicação servidor e, em seguida, executar o cliente, tanto no Eclispe IDE quanto em linha de comando (CLI).

Além disto, é possível executar o servidor MCP Java em assistentes de IA locais, tais como Claude Desktop, Gemini CLI e Cursor CLI, entre outros.

Também é possível verificar e testar o servidor MCP Java com o MCP Inspector. A Anthropic criou esta ferramenta para testar e depurar servidores MCP locais e remotos. Ela possui interface web, CLI e TUI. Mais adiante, este documento mostra os passos necessários para testar o servidor MCP Java com o MCP Inspector rodando como um contêiner Docker no Docker Desktop, juntamente com os arquivos `Dockerfile` e `compose.yaml`, disponíveis na raiz do projeto.

#### ⚙️ Eclipse IDE ⚙️
1. Clicar com o botão direito do mouse no nome do projeto;
2. Selecionar **Run As** > **Maven build...**
3. Na janela **Edit configuration and launch**:
    * Na aba **Main** digitar **clean package -Pserver -DskipTests** no campo **Goals**
    * Na aba **JRE** digitar **--enable-native-access=ALL-UNNAMED** no campo **VM arguments**
4. Clicar no botão **Run**

Os passos anteriores devem ser executados somente na primeira vez. A partir da segunda vez clicar na "pontinha de flecha" ao lado do botão *play*, na barra de botões do Eclipse IDE.

Se o processo de construção da aplicação servidor ocorreu sem erros, ele produzirá o diretório (pasta) **server** na raiz do projeto. Este diretório contém a aplicação executável do servidor e o sub-diretório **libs** contendo as bibliotecas de código necessárias ao servidor.

**A aplicação servidor é executada pela aplicação cliente e não precisa de intervenção do programador.**

Para gerar a aplicação cliente, deve-se executar os quatro passos mostrados anteriormente, com a seguinte diferença, na aba **Main** digitar **clean package -Pclient -DskipTests**, e este procedimento, assim como o anterior, deve ser realizado apenas na primeira vez.

Se o processo de construção da aplicação cliente ocorreu sem erros, ele produzirá o diretório **dist-client** como sub-diretório de **target**. O diretório **dist_client** contém a aplicação cliente e o sub-diretório **libs** com as bibliotecas necessária à sua execução.

#### ⚙️ Linha de Comand (CLI) ⚙️
Abrir uma janela do terminal, Prompt de Comando no Windows, por exemplo, mudar para o diretório raiz do projeto e gerar as aplicações:

```
# gerar a aplicação servidor
mvn clean package -Pserver -DskipTests
```
```
# gerar a aplicação cliente
mvn clean package -Pclient -DskipTests

# executar o cliente MCP Java, que executará o servidor MCP Java
java -jar mcp_server_01_client.jar
```

Independentemente do modo de execução do processo de construção (*build*), se realizado no Eclipse IDE ou em CLI, uma vez que ambas as aplicações tenham sido criadas, o programador deve abrir uma janela do terminal e mudar para o diretório **dist-client**. Estando neste diretório digitar **java -jar mcp_server_01_client.jar**, conforme informado acima, pressionar a tecla ENTER <img width="20" height="20" src="https://img.icons8.com/color/48/enter-key.png" alt="enter-key"/> e aguardar a execução do cliente que, por sua vez, executará o servidor e apresentará ao usuário um menu para interagir com a(s) ferramenta(s) disponibilizadas pelo servidor.

---

#### ⚙️ MCP Inspector no Docker Desktop ⚙️
Certificar-se de que o Docker Desktop esteja em execução antes de proceder com os passos à seguir.

Antes de iniciar o contêiner, coloque os PDFs que deseja testar no diretório `pdf-workspace` na raiz do projeto. O `compose.yaml` monta esse diretório no contêiner e define `MCP_PDF_WORKSPACE`, permitindo que o Inspector e o servidor Java acessem os mesmos arquivos.

1. Abrir uma janela do terminal, Prompt de Comando no Windows, por exemplo
2. Mudar para o diretório raiz do projeto
3. Executar o comando `docker compose up -d`
4. Aguardar até que a imagem tenha sido criada e executada no Docker Desktop
5. Na barra de endereços do navegador, digitar `http://127.0.0.1:6274` e pressionar ENTER <img width="20" height="20" src="https://img.icons8.com/color/48/enter-key.png" alt="enter-key"/>

O passo **3** é uma composição, ou seja, o comando `compose` processa o arquivo `compose.yaml`, que, por sua vez, processa o arquivo `Dockerfile` para criar, se necessário, a imagem Docker. Após a criação da imagem Docker, o `compose` continua sua execução e implanta um conjunto de serviços, neste caso, apenas um, no Docker Desktop. Ambos os arquivos encontram-se no diretório raiz do projeto.

Se os passos anteriores ocorreram com sucesso, o MCP Inspector exibirá o servidor MCP Java e a(s) ferramenta(s) disponibilizada(s) por ele poderá(ão) ser executada(s) (testada(s)).

---

### 📌 Instalar o servidor MCP Java Local no [Claude Desktop](https://claude.com/) 📌 ###
- Localizar o diretório `C:\Users\<USUÁRIO_LOGADO>\AppData\Local\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Roaming\Claude`
- Editar o arquivo `claude_desktop_config.json`
- Se não existir, criar a entrada `mcpServers` com o conteúdo

```
  "mcpServers": {
    "NOME_DO_SERVIDOR": {
      "command": "java",
      "args": ["-jar", "/caminho/absoluto/para/servidor.jar"],
      "env": {
        "MCP_PDF_WORKSPACE": "C:\\caminho\\absoluto\\para\\pdf-workspace"
      }
    }
  }
```
- Salvar o arquivo e iniciar ou reiniciar o Claude Desktop (se o Claude Desktop estiver em execução deve ser completamente encerrado e não apenas sua janela fechada).


O diretório `Users` também pode ser escrito como `Usuários`.
 
O diretório `Claude_pzs8sxrjxfjjc` pode possuir um nome diferente do apresentado, mas o prefixo `Claude_` será o mesmo.

O Java, JDK ou JRE, **deve** estar instalado e configurado.

---

🗣️ **OBSERVAÇÃO IMPORTANTE** 🗣️
> Sempre que o artefato SERVIDOR é gerado, o artefato CLIENTE é apagado do diretório "target", devido ao objetivo (*goal*) `clean`, e deve ser gerado novamente (mesmo que não tenha havido mudança de código nele).

---

### 📌 Executar Testes de Integração (IT - *Integration Test*) (OPCIONAL) 📌
O diretório `src/test/java` é o local onde se localizam as classes para testes, tanto de integração, classes com o sufixo **IT**, quanto unitários, classes com o sufixo **Test**.

Os testes unitários de `PdfMetadataService` exercitam a leitura de campos presentes e ausentes, atualização parcial, preservação de campos e rejeição de caminhos ou solicitações inválidas. Eles podem ser executados com `mvn test`.

Para incluir um PDF externo nos testes, informe `mcp.pdf.sample`. O teste unitário e o teste de integração usam cópias temporárias, então o arquivo original não é alterado:

```powershell
mvn clean verify -Pserver "-Dmcp.pdf.sample=C:\caminho\para\arquivo.pdf"
```

Seguir os passos acima para criar um *build*, se for a primeira execução. No campo `Goals` digitar `clean verify -Pserver`.

Para executar os testes de integração em linha de comando, digitar `mvn clean verify -Pserver`.

---

#### 📘 REFERÊNCIAS 📘 ####
[What is the Model Context Protocol (MCP)? - Documentação Oficial](https://modelcontextprotocol.io/docs/2026-07-28/getting-started/intro)

[MCP Java SDK](https://github.com/modelcontextprotocol/java-sdk)

[Building a JDBC-based MCP Server: A Developer Guide](https://www.cdata.com/kb/articles/jdbc-mcp-server-dev-guide.rst)

[Learn MCP Java Tutorial - Microsoft](https://github.com/microsoft/lets-learn-mcp-java/tree/main)

[Tutorial 1 no GitHub](https://github.com/eugenp/tutorials/tree/master/java-mcp)

[Tutorial 2 no GitHub](https://github.com/CDataSoftware/cdata-jdbc-mcp-server)
