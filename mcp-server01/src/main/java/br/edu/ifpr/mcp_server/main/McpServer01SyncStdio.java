package br.edu.ifpr.mcp_server.main;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.edu.ifpr.mcp_server.mcp.tools.ReadPdfMetadataTool;
import br.edu.ifpr.mcp_server.mcp.tools.UpdatePdfMetadataTool;
import br.edu.ifpr.mcp_server.mcp.utils.McpToolDefinition;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServer.SingleSessionSyncSpecification;
import io.modelcontextprotocol.server.McpServer.SyncSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.Implementation;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import tools.jackson.databind.json.JsonMapper;

public class McpServer01SyncStdio {
   private static final Logger LOG =
                            LoggerFactory.getLogger(McpServer01SyncStdio.class);

   private McpSyncServer server;

   private void registerTools(
                          SyncSpecification<SingleSessionSyncSpecification> mcp)
                throws Exception {
      // cria a lista de ferramentas disponíveis no servidor...
      List<McpToolDefinition> tools = List.of(new ReadPdfMetadataTool(),
                                               new UpdatePdfMetadataTool());
      // ...para cada ferramenta, executa seu registro para posterior execução
      for (McpToolDefinition tool : tools)
         if (tool != null)
            tool.register(mcp);
   }

   private SyncSpecification<SingleSessionSyncSpecification> configMcpServer() {
      // cria o objeto para comunicação entre servidor e cliente
      JacksonMcpJsonMapper         jsonMapper        =
                                     new JacksonMcpJsonMapper(new JsonMapper());
      // estabelece que a comunicação usará o transporte STDIO
      StdioServerTransportProvider transportProvider =
                                   new StdioServerTransportProvider(jsonMapper);
      // identifica quais "capacidades" o servidor disponibiliza, neste caso,
      // ferramentas e 'logging'
      /* capacidades, o que o servidor disponibiliza, do servidor
      completions  presente se o servidor suporta sugestões para auto completar
                   argumentos
      experimental capacidades não padronizadas que o servidor suporta!!!
      logging      presente se o servidor suporta envio de mensagen ('logging')
                   ao cliente
      prompts      presente se o servidor oferece modelos ('templates') de
                   promptsPresent if the server offers any prompt templates
      resources    presente se o servidor oferece recursos que podem ser lidos
      tools        presente se o servidor oferece ferramentas
       */
      ServerCapabilities capabilities    = ServerCapabilities.builder()
                                                             .tools(true)
                                                             .logging()
                                                             .build();
      // identificação do servidor
      Implementation      implementation = Implementation.builder("MCP Server",
    			                                                  "1.0.0")
                                                         .build(); 

      // configura o servidor com transporte STDIO
      return McpServer.sync(transportProvider)
                      .serverInfo(implementation)
                      .capabilities(capabilities);
   }

   public McpServer01SyncStdio() throws Exception {
      // configura/inicializa o servidor
      SyncSpecification<SingleSessionSyncSpecification> srv = configMcpServer();
      
      // registra as ferramentas disponíveis neste servidor
      registerTools(srv);
      // cria o servidor
      this.server = srv.build();

      LOG.info("MCP Server 01 iniciado via STDIO...");
   }

   public static void main(String[] args) throws Exception {
      new McpServer01SyncStdio();
//      McpServer01SyncStdio p = new McpServer01SyncStdio();
//
//      Runtime.getRuntime().addShutdownHook(new Thread() {
//         public void run() {
//            synchronized (p) {
//               p.notify();
//            }
//         }
//      });
//      synchronized (p) {
//         p.wait();
//         server.closeGracefully();
//      }
   }
}
