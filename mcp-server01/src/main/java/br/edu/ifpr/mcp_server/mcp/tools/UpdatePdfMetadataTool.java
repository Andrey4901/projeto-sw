package br.edu.ifpr.mcp_server.mcp.tools;

import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService;
import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService.PdfMetadata;
import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService.PdfMetadataUpdate;
import br.edu.ifpr.mcp_server.mcp.utils.McpToolDefinition;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;

/** Ferramenta MCP para atualizar campos de metadados padrão de um PDF. */
public class UpdatePdfMetadataTool implements McpToolDefinition {

   /** Logger técnico; evita incluir os valores dos metadados no log. */
   private static final Logger LOG = LoggerFactory.getLogger(UpdatePdfMetadataTool.class);

   /** Serviço que contém as regras de acesso e persistência dos metadados. */
   private final PdfMetadataService metadataService;

   /** Cria a ferramenta usando o serviço e a pasta padrão configurável. */
   public UpdatePdfMetadataTool() {
      this(new PdfMetadataService());
   }

   /** Injeta o serviço para permitir configuração e testes independentes. */
   public UpdatePdfMetadataTool(PdfMetadataService metadataService) {
      this.metadataService = metadataService;
   }

   /** Registra a ferramenta e seu esquema de argumentos opcionais. */
   @Override
   public void register(SyncSpecification<SingleSessionSyncSpecification> mcp) {
      Map<String, Object> textField = Map.of("type", "string");
      Map<String, Object> dateTimeField = Map.of(
            "type", "string",
            "format", "date-time",
            "description", "Data ISO 8601 com fuso, por exemplo 2026-11-08T12:30:00Z.");
      Map<String, Object> properties = Map.of(
            "filePath", Map.of("type", "string",
                               "description", "Caminho do PDF dentro da pasta de trabalho."),
            "title", textField,
            "author", textField,
            "subject", textField,
            "keywords", textField,
            "creator", textField,
            "creationDate", dateTimeField,
            "modificationDate", dateTimeField);
      Map<String, Object> inputSchema = Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("filePath"),
            "additionalProperties", false);

      Tool tool = Tool.builder("atualizar_metadados_pdf", inputSchema)
                      .title("Atualizar metadados de PDF")
                      .description("Atualiza campos textuais e as datas creationDate e modificationDate em ISO 8601. Campos omitidos são preservados.")
                      .build();

      mcp.tools(SyncToolSpecification.builder()
                                    .tool(tool)
                                    .callHandler(this::run)
                                    .build());
   }

   /** Valida argumentos, executa a atualização e informa o estado final. */
   @Override
   public CallToolResult run(McpSyncServerExchange exchange, CallToolRequest request) {
      try {
         Map<String, Object> arguments = request.arguments();
         String filePath = requiredString(arguments, "filePath");
         PdfMetadataUpdate update = new PdfMetadataUpdate(
               optionalString(arguments, "title"),
               optionalString(arguments, "author"),
               optionalString(arguments, "subject"),
               optionalString(arguments, "keywords"),
               optionalString(arguments, "creator"),
               optionalInstant(arguments, "creationDate"),
               optionalInstant(arguments, "modificationDate"));

         PdfMetadata metadata = metadataService.updateMetadata(filePath, update);
         String result = "Metadados atualizados com sucesso.\n"
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
         // O stack trace facilita diagnóstico sem enviar detalhes pelo protocolo MCP.
         LOG.error("Falha ao atualizar metadados do PDF.", exception);
         return errorResult(exception.getMessage());
      }
   }

   /** Obtém o caminho obrigatório informado pelo cliente MCP. */
   private String requiredString(Map<String, Object> arguments, String name) {
      String value = optionalString(arguments, name);
      if (value == null || value.isBlank()) {
         throw new IllegalArgumentException("O parâmetro '" + name + "' é obrigatório.");
      }
      return value;
   }

   /** Obtém um campo opcional e rejeita valores que não sejam texto. */
   private String optionalString(Map<String, Object> arguments, String name) {
      Object value = arguments == null ? null : arguments.get(name);
      if (value == null) return null;
      if (!(value instanceof String text)) {
         throw new IllegalArgumentException("O parâmetro '" + name + "' deve ser texto.");
      }
      return text;
   }

   /** Converte uma data ISO 8601 com fuso para o instante armazenado no PDF. */
   private Instant optionalInstant(Map<String, Object> arguments, String name) {
      String value = optionalString(arguments, name);
      if (value == null || value.isBlank()) return null;

      try {
         // OffsetDateTime exige data, hora e fuso (Z ou deslocamento como -03:00).
         return OffsetDateTime.parse(value).toInstant();
      }
      catch (DateTimeParseException exception) {
         throw new IllegalArgumentException("O parâmetro '" + name
               + "' deve usar ISO 8601 com fuso, por exemplo 2026-11-08T12:30:00Z.",
               exception);
      }
   }

   /** Exibe valor vazio sem confundi-lo com erro de leitura. */
   private String display(String value) {
      return value == null || value.isBlank() ? "(não informado)" : value;
   }

   /** Devolve falhas como resultado MCP com a indicação isError=true. */
   private CallToolResult errorResult(String message) {
      return CallToolResult.builder()
                           .content(List.of(TextContent.builder("Erro ao atualizar PDF: " + message).build()))
                           .isError(true)
                           .build();
   }
}
