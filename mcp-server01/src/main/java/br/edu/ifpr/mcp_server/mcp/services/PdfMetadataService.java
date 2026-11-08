package br.edu.ifpr.mcp_server.mcp.services;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Objects;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;

/**
 * Centraliza o acesso a arquivos PDF e a leitura/gravação de seus metadados.
 *
 * <p>A classe não conhece o protocolo MCP. Essa separação permite que as
 * ferramentas MCP cuidem da comunicação, enquanto este serviço concentra as
 * regras de negócio e o acesso à biblioteca PDFBox.</p>
 */
public class PdfMetadataService {

   /**
    * Nome da propriedade Java usada para configurar a pasta de PDFs.
    * A propriedade de sistema tem prioridade sobre a variável de ambiente.
    */
   public static final String WORKSPACE_PROPERTY = "mcp.pdf.workspace";

   /** Nome da variável de ambiente alternativa para configurar a pasta. */
   public static final String WORKSPACE_ENVIRONMENT_VARIABLE = "MCP_PDF_WORKSPACE";

   /** Pasta raiz autorizada para leitura e alteração de documentos PDF. */
   private final Path workspace;

   /**
    * Cria o serviço usando a pasta configurada pelo sistema ou o padrão.
    *
    * <p>O padrão é a pasta "pdf-workspace" dentro do diretório de execução.
    * Para alterar o local, configure -Dmcp.pdf.workspace=... ou a variável
    * MCP_PDF_WORKSPACE.</p>
    */
   public PdfMetadataService() {
      this(configuredWorkspace());
   }

   /**
    * Cria o serviço para uma pasta específica.
    *
    * @param workspace pasta que conterá os documentos autorizados
    */
   public PdfMetadataService(Path workspace) {
      this.workspace = Objects.requireNonNull(workspace, "A pasta de trabalho é obrigatória.")
                               .toAbsolutePath()
                               .normalize();
   }

   /**
    * Lê os metadados padrão de um PDF localizado dentro da pasta autorizada.
    *
    * @param filePath caminho absoluto ou relativo ao espaço de trabalho
    * @return metadados encontrados no documento
    * @throws IOException quando o arquivo não existir, não for PDF válido,
    *                     estiver protegido ou não puder ser lido
    */
   public PdfMetadata readMetadata(String filePath) throws IOException {
      Path pdfPath = resolvePdfPath(filePath);

      try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
         PDDocumentInformation information = document.getDocumentInformation();
         return new PdfMetadata(information.getTitle(),
                                information.getAuthor(),
                                information.getSubject(),
                                information.getKeywords(),
                                information.getCreator(),
                                information.getProducer(),
                                formatDate(information.getCreationDate()),
                                formatDate(information.getModificationDate()));
      }
   }

   /**
    * Atualiza somente os campos informados, preservando os demais metadados.
    *
    * @param filePath caminho absoluto ou relativo ao espaço de trabalho
    * @param update campos que devem ser modificados; valores nulos significam
    *               que o campo correspondente deve permanecer inalterado
    * @return metadados após a gravação do documento
    * @throws IOException quando o arquivo não puder ser lido ou gravado
    */
   public PdfMetadata updateMetadata(String filePath, PdfMetadataUpdate update)
         throws IOException {
      Objects.requireNonNull(update, "Os campos de atualização são obrigatórios.");
      if (!update.hasAnyField()) {
         throw new IllegalArgumentException("Informe ao menos um metadado para atualizar.");
      }

      Path pdfPath = resolvePdfPath(filePath);

      // PDFBox não permite gravar sobre o mesmo arquivo usado como origem.
      // O temporário fica na mesma pasta para possibilitar uma substituição atômica.
      Path temporaryPdf = Files.createTempFile(pdfPath.getParent(), ".pdf-metadata-", ".tmp");
      boolean replacedOriginal = false;
      try {
         try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            PDDocumentInformation information = document.getDocumentInformation();

            // Cada atribuição é condicional para preservar campos não solicitados.
            if (update.title() != null) information.setTitle(update.title());
            if (update.author() != null) information.setAuthor(update.author());
            if (update.subject() != null) information.setSubject(update.subject());
            if (update.keywords() != null) information.setKeywords(update.keywords());
            if (update.creator() != null) information.setCreator(update.creator());
            if (update.creationDate() != null) {
               information.setCreationDate(GregorianCalendar.from(
                     update.creationDate().atZone(ZoneOffset.UTC)));
            }
            if (update.modificationDate() != null) {
               information.setModificationDate(GregorianCalendar.from(
                     update.modificationDate().atZone(ZoneOffset.UTC)));
            }

            document.setDocumentInformation(information);
            document.save(temporaryPdf.toFile());
         }

         try {
            Files.move(temporaryPdf, pdfPath, StandardCopyOption.ATOMIC_MOVE,
                      StandardCopyOption.REPLACE_EXISTING);
         }
         catch (AtomicMoveNotSupportedException exception) {
            // Alguns sistemas de arquivos não aceitam movimento atômico;
            // nesse caso, ainda substituímos o original após salvar por completo.
            Files.move(temporaryPdf, pdfPath, StandardCopyOption.REPLACE_EXISTING);
         }
         replacedOriginal = true;
      }
      finally {
         // Remove o temporário se a leitura, gravação ou substituição falhar.
         if (!replacedOriginal) Files.deleteIfExists(temporaryPdf);
      }

      // Reabre o arquivo para retornar os valores persistidos e normalizados.
      return readMetadata(filePath);
   }

   /** Converte uma data do PDF em texto ISO 8601 UTC; null indica campo ausente. */
   private String formatDate(Calendar date) {
      return date == null ? null : date.toInstant().toString();
   }

   /**
    * Resolve e valida o caminho para garantir que permaneça dentro da pasta
    * autorizada e aponte para um arquivo PDF existente.
    */
   private Path resolvePdfPath(String filePath) throws IOException {
      if (filePath == null || filePath.isBlank()) {
         throw new IllegalArgumentException("O caminho do arquivo PDF é obrigatório.");
      }

      try {
         Path requestedPath = Path.of(filePath.trim());
         Path resolvedPath = (requestedPath.isAbsolute()
                              ? requestedPath
                              : workspace.resolve(requestedPath))
                             .toAbsolutePath()
                             .normalize();

         if (!resolvedPath.startsWith(workspace)) {
            throw new IllegalArgumentException(
                  "O arquivo deve estar dentro da pasta de trabalho autorizada: " + workspace);
         }
         if (resolvedPath.getFileName() == null
               || !resolvedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("O arquivo informado deve possuir extensão .pdf.");
         }
         if (!Files.isRegularFile(resolvedPath)) {
            throw new IOException("Arquivo PDF não encontrado: " + resolvedPath);
         }

         // Resolve links simbólicos para impedir que um caminho aparentemente
         // interno aponte, na prática, para fora da pasta permitida.
         Files.createDirectories(workspace);
         Path realWorkspace = workspace.toRealPath();
         Path realPdfPath = resolvedPath.toRealPath();
         if (!realPdfPath.startsWith(realWorkspace)) {
            throw new IllegalArgumentException(
                  "O arquivo deve permanecer dentro da pasta de trabalho autorizada.");
         }

         return realPdfPath;
      }
      catch (InvalidPathException exception) {
         throw new IllegalArgumentException("O caminho informado não é válido.", exception);
      }
   }

   /**
    * Obtém a pasta de trabalho pela propriedade Java, pela variável de
    * ambiente ou, na ausência de ambas, pelo diretório "pdf-workspace".
    */
   private static Path configuredWorkspace() {
      String configuredPath = System.getProperty(WORKSPACE_PROPERTY);
      if (configuredPath == null || configuredPath.isBlank()) {
         configuredPath = System.getenv(WORKSPACE_ENVIRONMENT_VARIABLE);
      }
      if (configuredPath == null || configuredPath.isBlank()) {
         configuredPath = Path.of(System.getProperty("user.dir"), "pdf-workspace").toString();
      }
      return Path.of(configuredPath);
   }

   /**
    * Representa os campos padrão que este projeto expõe ao cliente MCP.
    *
    * @param title título do documento
    * @param author autor informado no documento
    * @param subject assunto do documento
    * @param keywords palavras-chave separadas conforme o formato PDF
    * @param creator aplicação ou pessoa que criou o conteúdo
    * @param producer ferramenta que gerou o PDF
    * @param creationDate data de criação em ISO 8601 UTC, ou null se ausente
    * @param modificationDate data de modificação em ISO 8601 UTC, ou null se ausente
    */
   public record PdfMetadata(String title, String author, String subject,
                             String keywords, String creator, String producer,
                             String creationDate, String modificationDate) { }

   /**
    * Agrupa os campos opcionais aceitos na operação de atualização.
    * Um valor nulo significa que o campo não foi solicitado para alteração.
    */
   public record PdfMetadataUpdate(String title, String author, String subject,
                                   String keywords, String creator,
                                   Instant creationDate, Instant modificationDate) {
      /** Indica se pelo menos um campo foi informado pelo cliente MCP. */
      public boolean hasAnyField() {
         return title != null || author != null || subject != null
                || keywords != null || creator != null
                || creationDate != null || modificationDate != null;
      }
   }
}
