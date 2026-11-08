package br.edu.ifpr.mcp_server.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.pdfbox.pdmodel.PDDocument;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.json.JsonMapper;

class McpServer01IT {
   private static final Logger LOG =
                                   LoggerFactory.getLogger(McpServer01IT.class);
   // nome e caminho para o servidor MCP
   private static final String SERVER_PATH = "server/mcp_server01_server.jar";
   /** Arquivo de teste criado na pasta padrão do serviço de metadados PDF. */
   private static final Path PDF_WORKSPACE = Path.of("pdf-workspace");
   private static final String TEST_PDF_NAME = "mcp-integration-test.pdf";
   private static final Path TEST_PDF = PDF_WORKSPACE.resolve(TEST_PDF_NAME);

   private static McpSyncClient client;

   @BeforeAll
   static void setUpBeforeClass() throws Exception {
      // antes que a classe de testes seja carregada, este método providencia
      // alguma inicialização ou alocação de recursos, neste caso, o servidor
      // MCP é executado e o cliente se conecta a ele
      // Prepara um PDF real para que as chamadas MCP exercitem o serviço PDFBox.
      Files.createDirectories(PDF_WORKSPACE);
      try (PDDocument document = new PDDocument()) {
         document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
         document.getDocumentInformation().setTitle("Título de integração");
         document.save(TEST_PDF.toFile());
      }

      File serverJar = new File(SERVER_PATH);
      assertTrue(serverJar.isFile(),"JAR do servidor não encontrado: " +
                                    serverJar.getAbsolutePath());

      String           jarPath = serverJar.getAbsolutePath();
      ServerParameters params  = ServerParameters.builder("java")
    			                                 .args("-jar",jarPath)
    			                                 .build();
      JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(
    			                                              new JsonMapper());
      StdioClientTransport transport  = new StdioClientTransport(params,
    			                                                 jsonMapper);

      transport.setStdErrorHandler(line -> LOG.info("[SERVER STDERR] " + line));

      client = McpClient.sync(transport).build();
      client.initialize();
   }

   @AfterAll
   static void tearDownAfterClass() throws Exception {
      // ao terminar todos os testes, todos os métodos nesta classe foram
      // executados, o cliente se desconecta do servidor
      if (client != null)
         client.closeGracefully();
      Files.deleteIfExists(TEST_PDF);
   }

   // ==========================================================================
   // Os métodos de teste à seguir simulam a aplicação cliente:
   // - há ferramentas (tools) disponíveis???
   // - as ferramentas de metadados PDF estão disponíveis???
   // - as ferramentas de leitura e atualização executaram com sucesso???
   // ==========================================================================

   @Test
   void deveDisponibilizarFerramentas() {
      ListToolsResult result = client.listTools();

      assertNotNull(result,"O servidor não retornou o resultado.");
      assertNotNull(result.tools(),"A lista de ferramentas não pode ser null.");
      assertFalse(result.tools().isEmpty(),"O servidor deveria disponibilizar" +
                                           " pelo menos uma ferramenta." );
   }

   @Test
   void deveDisponibilizarFerramentasDeMetadadosPdf() {
      ListToolsResult result = client.listTools();
                                      // retorna a lista de ferramentas
      Tool            tool   = result.tools()
                                     // cria um fluxo para percorrer a lista
    			                     .stream()
    			                     // filtra a lista para um elemento
    			                     .filter(item ->
                          "ler_metadados_pdf".equals(item.name()))
    			                     // verifica se há alguma elemento...
    			                     .findFirst()
    			                     // ...caso contrário, retorna "null"
    			                     .orElse(null);

      assertNotNull(tool,"A ferramenta de leitura de metadados PDF não foi encontrada.");
      assertNotNull(tool.description(),"A ferramenta deveria possuir " +
                                       "descrição.");
      assertNotNull(tool.inputSchema(),"A ferramenta deveria possuir " +
                                       "'inputSchema'.");
   }

   @Test
   void deveLerMetadadosPdfPeloServidorMcp() {
      CallToolRequest request = CallToolRequest.builder("ler_metadados_pdf")
                                         // Caminhos relativos são resolvidos dentro de pdf-workspace.
                                         .arguments(Map.of("filePath", TEST_PDF_NAME))
    			                               .build();
      CallToolResult  result  = client.callTool(request);

      assertNotNull(result,"O servidor não retornou resultado.");
      assertFalse(result.isError(),"A execução da ferramenta não deveria resultar em erro: "
                                   + result.content());
      assertNotNull(result.content(),"O resultado deveria possuir 'content'.");
      assertFalse(result.content().isEmpty(),"O resultado deveria possuir " +
                                             "pelo menos um 'content'." );
   }

   /** Verifica que a ferramenta MCP também persiste alterações no documento. */
   @Test
   void deveAtualizarMetadadosPdfPeloServidorMcp() {
      CallToolRequest request = CallToolRequest.builder("atualizar_metadados_pdf")
            // O nome é relativo à pasta autorizada configurada no servidor.
            .arguments(Map.of("filePath", TEST_PDF_NAME,
                              "author", "Autor de integração",
                              "subject", "Teste de integração",
                              "creationDate", "2025-01-02T03:04:05Z",
                              "modificationDate", "2025-02-03T04:05:06Z"))
            .build();
      CallToolResult result = client.callTool(request);

      assertNotNull(result, "O servidor não retornou resultado.");
      assertFalse(result.isError(), "A atualização não deveria resultar em erro: "
                                    + result.content());
      assertFalse(result.content().isEmpty(), "A atualização deveria retornar confirmação.");
   }

   /**
    * Quando solicitado por propriedade, testa o PDF fornecido pelo usuário
    * usando uma cópia descartável e a comunicação MCP real cliente-servidor.
    */
   @Test
   @EnabledIfSystemProperty(named = "mcp.pdf.sample", matches = ".+")
   void deveAtualizarPdfFornecidoPeloClienteMcp() throws Exception {
      Path suppliedPdf = Path.of(System.getProperty("mcp.pdf.sample")).toRealPath();
      Path temporaryCopy = PDF_WORKSPACE.resolve("mcp-user-sample-test.pdf");
      Files.copy(suppliedPdf, temporaryCopy, StandardCopyOption.REPLACE_EXISTING);

      try {
         CallToolRequest request = CallToolRequest.builder("atualizar_metadados_pdf")
               .arguments(Map.of("filePath", temporaryCopy.getFileName().toString(),
                                 "title", "Teste MCP",
                                 "author", "Teste MCP",
                                 "subject", "Teste MCP",
                                 "keywords", "Teste MCP",
                                 "creator", "Teste MCP",
                                 "creationDate", "2025-04-05T06:07:08Z",
                                 "modificationDate", "2025-04-06T07:08:09Z"))
               .build();
         CallToolResult result = client.callTool(request);

         assertNotNull(result, "O servidor não retornou resultado.");
         assertFalse(result.isError(), "A atualização da cópia deveria funcionar: "
                                      + result.content());
         assertFalse(result.content().isEmpty(), "A ferramenta deveria confirmar a atualização.");
      }
      finally {
         Files.deleteIfExists(temporaryCopy);
      }
   }
}
