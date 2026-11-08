package br.edu.ifpr.mcp_server.mcp.tools;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService;
import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService.PdfMetadata;
import br.edu.ifpr.mcp_server.mcp.utils.McpToolDefinition;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** Ferramenta MCP que consulta os metadados padrão de um documento PDF. */
public class ReadPdfMetadataTool implements McpToolDefinition {

   /** Logger usado para registrar detalhes técnicos sem expor o conteúdo do PDF. */
   private static final Logger LOG = LoggerFactory.getLogger(ReadPdfMetadataTool.class);

   /** Serviço responsável por localizar e ler os documentos PDF. */
   private final PdfMetadataService metadataService;

   /** Cria a ferramenta com o serviço padrão e sua pasta configurável. */
   public ReadPdfMetadataTool() {
      this(new PdfMetadataService());
   }

   /** Permite fornecer um serviço específico, útil também para testes. */
   public ReadPdfMetadataTool(PdfMetadataService metadataService) {
      this.metadataService = metadataService;
   }

   /** Registra a ferramenta e descreve ao cliente o único parâmetro aceito. */
   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp) {
      Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", Map.of("filePath", Map.of(
                  "type", "string",
                  "description", "Caminho do PDF, relativo ou absoluto dentro da pasta autorizada.")),
            "required", List.of("filePath"),
            "additionalProperties", false);

      Tool tool = Tool.builder("ler_metadados_pdf", inputSchema)
                      .title("Ler metadados de PDF")
                      .description("Retorna título, autor, assunto, palavras-chave, criador e produtor de um PDF.")
                      .build();

      mcp.tools(SyncToolSpecification.builder()
                                    .tool(tool)
                                    .callHandler(this::run)
                                    .build());
   }

   /** Executa a consulta e converte resultados e erros em resposta MCP. */
   @Override
   public CallToolResult run(McpSyncServerExchange exchange, CallToolRequest request) {
      try {
         String filePath = requiredString(request.arguments(), "filePath");
         PdfMetadata metadata = metadataService.readMetadata(filePath);
         String result = "Metadados do PDF:\n"
               + "Título: " + display(metadata.title()) + "\n"
               + "Autor: " + display(metadata.author()) + "\n"
               + "Assunto: " + display(metadata.subject()) + "\n"
               + "Palavras-chave: " + display(metadata.keywords()) + "\n"
               + "Criador: " + display(metadata.creator()) + "\n"
               + "Produtor: " + display(metadata.producer()) + "\n"
               + "Data de criação: " + display(metadata.creationDate()) + "\n"
               + "Data de modificação: " + display(metadata.modificationDate());
         return CallToolResult.builder()
                              .content(List.of(TextContent.builder(result).build()))
                              .build();
      }
      catch (IOException | IllegalArgumentException exception) {
         // Detalhes vão para STDERR/logs do servidor; a resposta MCP permanece simples.
         LOG.error("Falha ao ler metadados do PDF.", exception);
         return errorResult(exception.getMessage());
      }
   }

   /** Lê um argumento textual obrigatório sem aceitar valor ausente ou vazio. */
   private String requiredString(Map<String, Object> arguments, String name) {
      Object value = arguments == null ? null : arguments.get(name);
      if (!(value instanceof String text) || text.isBlank()) {
         throw new IllegalArgumentException("O parâmetro '" + name + "' é obrigatório.");
      }
      return text;
   }

   /** Substitui campos vazios por indicação compreensível ao usuário. */
   private String display(String value) {
      return value == null || value.isBlank() ? "(não informado)" : value;
   }

   /** Cria resposta MCP marcada como erro para que o cliente identifique falhas. */
   private CallToolResult errorResult(String message) {
      return CallToolResult.builder()
                           .content(List.of(TextContent.builder("Erro ao ler PDF: " + message).build()))
                           .isError(true)
                           .build();
   }
}
