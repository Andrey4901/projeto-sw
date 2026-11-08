package br.edu.ifpr.mcp_server.main;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;
import io.modelcontextprotocol.spec.McpSchema.LoggingLevel;
import io.modelcontextprotocol.spec.McpSchema.LoggingMessageNotification;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import tools.jackson.databind.json.JsonMapper;

public class McpClient01SyncStdio {
   private static final Logger LOG =
                            LoggerFactory.getLogger(McpClient01SyncStdio.class);
   // nome e caminho para o servidor MCP
   private static final String SERVER_PATH =
                                         "../../server/mcp_server01_server.jar";
   // objeto cliente para comunicação com o servidor
   private final McpSyncClient client;

   // será invocado (chamado) pelo servidor (é a forma que o servidor tem para
   // "conversar" com o cliente)
   private void loggingMessageNotification(
                                      LoggingMessageNotification notification) {
      LOG.info("[NOTIFICAÇÃO MCP] Origem: {} | Nível: {} | Mensagem: {}", 
               notification.logger(), 
               notification.level(), 
               notification.data());
   }

   private McpSyncClient createClient() {
      // identifica o servidor a ser chamado/executado
      File serverJar = new File(SERVER_PATH);
      // se o objeto File não é um arquivo: erro!!!
      if (!serverJar.isFile())
         throw new IllegalStateException("JAR do servidor não encontrado: " +
		                                 serverJar.getAbsolutePath());
      // caminho absoluto do programa executável do servidor
      String jarPath = serverJar.getAbsolutePath();
      LOG.info("Servidor MCP: {}",jarPath);
      // comando para executar a aplicação servidor
      ServerParameters params  = ServerParameters.builder("java")
                                                 .args("-jar",jarPath)
                                                 .build();
      // cria o objeto para comunicação entre cliente e servidor
      JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(
                                                              new JsonMapper());
      // estabelece que a comunicação usará o transporte STDIO
      StdioClientTransport   transport  = new StdioClientTransport(params,
                                                                   jsonMapper);
      // captura a comunicação entre o cliente e o servidor
      transport.setStdErrorHandler(
                                   line -> LOG.info("[SERVER STDERR] {}", line)
                                  );
      // constrói o objeto cliente
      return McpClient.sync(transport)
                      // CONFIGURAÇÃO DO LISTENER DE LOGGING:
                      // Registra o handler (tratador) para capturar as
                      // notificações enviadas pelo servidor
                      .loggingConsumer(this::loggingMessageNotification)
                      .build();
   }

   private void execute() {
      // inicializa o cliente com o transporte STDIO para o servidor
      client.initialize();
      // define o nível mínimo de "logging" que o cliente quer receber do
      // servidor
      client.setLoggingLevel(LoggingLevel.INFO);

      // executa o 'looping' para:
      // - listar ferramentas
      // - selecionar ferramenta
      // - realizar entrada de dados da ferramenta, se houver
      // - executar ferramenta
      listSelectRunToolLooping();
   }

   private void close() {
      LOG.info("Fechando cliente MCP...");
      // encerra o cliente
      client.closeGracefully();
      LOG.info("Cliente MCP fechado.");
   }

   private void listSelectRunToolLooping() {
      // os parâmetros para a(s) ferramenta(s) serão lidos da entrada padrão
      Scanner         scanner = new Scanner(System.in);
      // recupera as ferramentas disponíveis no servidor
      ListToolsResult tools   = loadTools();
      // executa "in perpetuum"
      while (true) {
         // mostra as ferramentas (menu) ao usuário
         showMenu(tools);
         // exibe o prompt ao usuário
         System.out.print("> ");
         // se não houver qualquer entrada, encerra o 'looping'
         if (!scanner.hasNextLine())
            break;
         // obtém uma entrada e remove espaços de ambos os lados, se houverem
         String command = scanner.nextLine().trim();
         // se nada foi digitado, reinicia o 'looping'
         if (command.isBlank())
            continue;
         // se o usuário desejar encerrar
         if ("sair".equalsIgnoreCase(command))
            break;
         // se o usuário desejar a lista de ferramentas disponibilizada pelo
         // servidor MCP
         if ("listar".equalsIgnoreCase(command)) {
            tools = loadTools();
            // reinicia o 'looping'
            continue;
         }
         // tenta encontrar a ferramenta desejada
         Tool selectedTool = selectTool(tools.tools(),command);
         // se a ferramenta não foi encontrada, reinicia o 'looping'
         if (selectedTool == null) {
            LOG.warn("Ferramenta não encontrada: {}",command);
            continue;
         }
         // ao chegarmos aqui a ferramenta pode ser executada
         executeTool(selectedTool,scanner);
      }
   }

   private ListToolsResult loadTools() {
      ListToolsResult result = client.listTools();

      LOG.info("Servidor disponibilizou {} ferramenta(s).",
               result.tools().size());

      return result;
   }

   private void showMenu(ListToolsResult tools) {
      // exibe as ferramentas e opções ao usuário
      System.out.println();
      System.out.println("========================================");
      System.out.println(" CLIENTE MCP");
      System.out.println("========================================");

      if (tools.tools().isEmpty()) {
         System.out.println("Nenhuma ferramenta disponibilizada.");
      }
      else {
         System.out.println("Ferramentas disponíveis:");
         for (int i = 0; i < tools.tools().size(); i++) {
            Tool tool = tools.tools().get(i);

            System.out.printf(" %d - %s%n",i + 1,tool.name());
            if (tool.description() != null && !tool.description().isBlank())
               System.out.printf(" %s%n", tool.description());
         }
      }
      System.out.println();
      System.out.println("Comandos:");
      System.out.println(" listar - atualizar lista de ferramentas");
      System.out.println(" sair - encerrar o cliente");
      System.out.println();
   }

   private Tool selectTool(List<Tool> tools, String command) {
      try {
         int index = Integer.parseInt(command) - 1;

         if (index >= 0 && index < tools.size())
            return tools.get(index);
      }
      catch (NumberFormatException ignored) {
         // o comando pode ser o nome da ferramenta
      }
      // tenta encontrar a ferramenta solicitada pelo usuário
      for (Tool tool : tools)
         if (tool.name().equalsIgnoreCase(command))
            return tool;
      // se não encontra a ferramenta
      return null;
   }

   private void executeTool(Tool tool, Scanner scanner) {
      System.out.println();
      System.out.println("----------------------------------------");
      System.out.println("Ferramenta: " + tool.name());

      if (tool.description() != null)
         System.out.println("Descrição: " + tool.description());

      Map<String, Object> arguments = readArguments(tool,scanner);
      CallToolRequest     request   = CallToolRequest.builder(tool.name())
    			                                     .arguments(arguments)
    			                                     .build();
      LOG.info("Executando ferramenta '{}'",tool.name());
      try {
         CallToolResult result = client.callTool(request);

         showResult(result);
      }
      catch (Exception ex) {
         LOG.error("Erro executando ferramenta '{}'",tool.name(),ex);
      }
   }

   private Map<String, Object> readArguments(Tool tool, Scanner scanner) {
      Map<String, Object> arguments = new LinkedHashMap<>();
      // recupera os esquema JSON, que define os parâmetros da ferramenta, para
      // solicitar os valores ao usuário
      Map<String, Object> schema    = tool.inputSchema();
      // se o esquema não existe ou está vazio, retorna uma lista vazia
      if (schema == null || schema.isEmpty())
         return arguments;
      // recupera as propriedades do esquema da ferramenta
      // ver os métodos register() das classes de ferramentas em mcp.tools
      Object propertiesObject = schema.get("properties");

      if (!(propertiesObject instanceof Map<?, ?>))
         return arguments;

      Map<?, ?> properties = (Map<?, ?>) propertiesObject;
      List<?>   required   = getRequiredProperties(schema);

      if (properties.isEmpty())
         return arguments;

      System.out.println();
      System.out.println("Informe os parâmetros:");
      for (Map.Entry<?, ?> entry : properties.entrySet()) {
         String name = String.valueOf(entry.getKey());

         if (!(entry.getValue() instanceof Map<?, ?>))
            continue;
         // recupera o esquema (a descrição dos parâmetros) da ferramenta
         Map<?, ?> property    = (Map<?, ?>) entry.getValue();
         String    type        = getStringValue(property,"type","string");
         String    description = getStringValue(property,"description","");
         boolean   isRequired  = required.contains(name);
         Object    value       = readArgument(scanner,name,type,description,
        			                          isRequired,property);

         if (value != null)
            arguments.put(name,value);
      }
      return arguments;
   }

   private String getStringValue(Map<? ,?> map, String key,
                                 String defaultValue) {
      Object value = map.get(key);

      return value != null ? String.valueOf(value) : defaultValue;
   }

   private List<?> getRequiredProperties(Map<String, Object> schema) {
      Object required = schema.get("required");
      // verifica se há uma ou mais propriedades no esquema JSON que são
      // obrigatórias
      if (required instanceof List<?>)
         return (List<?>) required;
      // retorna uma lista vazia
      return List.of();
   }

   private Object readArgument(Scanner scanner, String name, String type,
			                   String description, boolean required,
			                   Map<?, ?> schema) {
      while (true) {
         System.out.println();
         System.out.println("'" + name + "' [" + type + "] " +
                            (required ? "(obrigatório)" : "(opcional)"));
         System.out.println("Descrição: " + description);
         System.out.print("Valor: ");

         if (!scanner.hasNextLine())
            return null;

         String value = scanner.nextLine().trim();
         if (value.isBlank()) {
            if (!required) return null;

            System.out.println("Este parâmetro é obrigatório.");
            continue;
         }
         try { return convertValue(value,type,schema); }
         catch (IllegalArgumentException ex) {
            System.out.println("Valor inválido: " + ex.getMessage());
         }
      }
   }

   private Object convertValue(String value, String type, Map<?, ?> schema) {
      return switch (type) {
    	        case "integer" -> Integer.valueOf(value);
    	        case "number" -> Double.valueOf(value);
    	        case "boolean" -> parseBoolean(value);
    	        case "string" -> value;
    	        default -> parseComplexValue(value,type,schema);
             };
   }

   private Boolean parseBoolean(String value) {
      if ("true".equalsIgnoreCase(value) || "sim".equalsIgnoreCase(value))
         return true;

      if ("false".equalsIgnoreCase(value) ||
          "não".equalsIgnoreCase(value) || "nao".equalsIgnoreCase(value))
         return false;

      throw new IllegalArgumentException("informe true/false ou sim/não");
   }

   private Object parseComplexValue(String value, String type,
			                        Map<?, ?> schema) {
      if ("null".equals(type))
         return null;

      throw new IllegalArgumentException("tipo não suportado pelo modo " +
                                         "interativo: " + type);
   }

   private void showResult(CallToolResult result) {
      System.out.println();
      System.out.println("=============== RESULTADO ===============");
      System.out.println("   Erro: " + (result.isError() ? "Sim" : "Não"));
      System.out.println("Content:" );
         result.content().forEach(content -> System.out.println(" " + content));

      if (result.structuredContent() != null) {
         System.out.println();
         System.out.println("Structured Content:");
         System.out.println(result.structuredContent());
      }
      System.out.println( "==========================================" );
   }

   public McpClient01SyncStdio() {
      this.client = createClient();
   }

   public static void main(String... args) {
	   McpClient01SyncStdio clientStdio = new McpClient01SyncStdio();

	   try { clientStdio.execute(); }
	   finally { clientStdio.close(); }
   }
}
