package br.edu.ifpr.mcp_server.mcp.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService.PdfMetadata;
import br.edu.ifpr.mcp_server.mcp.services.PdfMetadataService.PdfMetadataUpdate;

/** Testes unitários das regras de leitura, validação e atualização de PDFs. */
class PdfMetadataServiceTest {

   /** JUnit cria uma pasta isolada e a remove ao final de cada teste. */
   @TempDir
   Path temporaryDirectory;

   /** Serviço configurado para operar somente na pasta isolada do teste. */
   private PdfMetadataService service;

   /** Inicializa cada teste com uma instância de serviço independente. */
   @BeforeEach
   void setUp() {
      service = new PdfMetadataService(temporaryDirectory);
   }

   /** Confirma que a leitura devolve os campos gravados e preserva campos ausentes como null. */
   @Test
   void deveLerMetadadosExistentesECamposAusentes() throws Exception {
      Path pdf = temporaryDirectory.resolve("documento.pdf");
      Path source = temporaryDirectory.resolve("documento-origem.pdf");
      try (PDDocument document = new PDDocument()) {
         document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
         document.getDocumentInformation().setTitle("Relatório acadêmico");
         document.getDocumentInformation().setAuthor("Estudante");
         document.save(source.toFile());
      }
      Files.move(source, pdf);

      PdfMetadata result = service.readMetadata("documento.pdf");

      assertEquals("Relatório acadêmico", result.title());
      assertEquals("Estudante", result.author());
      assertNull(result.subject());
      assertNull(result.keywords());
      assertNull(result.creationDate());
      assertNull(result.modificationDate());
   }

   /** Confirma que a atualização altera os campos pedidos e mantém os demais. */
   @Test
   void deveAtualizarCamposSemAlterarOsDemais() throws Exception {
      Path pdf = temporaryDirectory.resolve("atualizar.pdf");
      Path source = temporaryDirectory.resolve("atualizar-origem.pdf");
      Instant originalCreationDate = Instant.parse("2024-01-02T03:04:05Z");
      Instant originalModificationDate = Instant.parse("2024-02-03T04:05:06Z");
      try (PDDocument document = new PDDocument()) {
         document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
         document.getDocumentInformation().setAuthor("Autor original");
         document.getDocumentInformation().setTitle("Título original");
         document.getDocumentInformation().setCreationDate(
               GregorianCalendar.from(originalCreationDate.atZone(ZoneOffset.UTC)));
         document.getDocumentInformation().setModificationDate(
               GregorianCalendar.from(originalModificationDate.atZone(ZoneOffset.UTC)));
         document.save(source.toFile());
      }
      Files.move(source, pdf);

      PdfMetadata result = service.updateMetadata(
            "atualizar.pdf",
            new PdfMetadataUpdate("Novo título", null, "Novo assunto", null, null,
                                  null, null));

      assertEquals("Novo título", result.title());
      assertEquals("Autor original", result.author());
      assertEquals("Novo assunto", result.subject());
      assertEquals(originalCreationDate.toString(), result.creationDate());
      assertEquals(originalModificationDate.toString(), result.modificationDate());
   }

   /** Confirma que as datas ISO 8601 recebidas são gravadas no PDF. */
   @Test
   void deveAtualizarDatasDeCriacaoEModificacao() throws Exception {
      Path pdf = temporaryDirectory.resolve("datas.pdf");
      try (PDDocument document = new PDDocument()) {
         document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
         document.save(pdf.toFile());
      }

      Instant creationDate = Instant.parse("2025-05-06T07:08:09Z");
      Instant modificationDate = Instant.parse("2025-06-07T08:09:10Z");
      PdfMetadata result = service.updateMetadata("datas.pdf",
            new PdfMetadataUpdate(null, null, null, null, null,
                                  creationDate, modificationDate));

      assertEquals(creationDate.toString(), result.creationDate());
      assertEquals(modificationDate.toString(), result.modificationDate());
   }

   /**
    * Faz uma leitura e uma atualização completas em uma cópia descartável de
    * um PDF externo quando a propriedade mcp.pdf.sample é informada.
    */
   @Test
   @EnabledIfSystemProperty(named = "mcp.pdf.sample", matches = ".+")
   void deveProcessarPdfFornecidoEmCopiaTemporaria() throws Exception {
      Path suppliedPdf = Path.of(System.getProperty("mcp.pdf.sample")).toRealPath();
      Path temporaryCopy = temporaryDirectory.resolve("pdf-fornecido.pdf");
      Files.copy(suppliedPdf, temporaryCopy);

      PdfMetadataService isolatedService = new PdfMetadataService(temporaryDirectory);
      PdfMetadata originalMetadata = isolatedService.readMetadata("pdf-fornecido.pdf");
      PdfMetadata updatedMetadata = isolatedService.updateMetadata(
            "pdf-fornecido.pdf",
            new PdfMetadataUpdate("Teste MCP", "Teste MCP", "Teste MCP",
                                  "Teste MCP", "Teste MCP",
                                  Instant.parse("2025-04-05T06:07:08Z"),
                                  Instant.parse("2025-04-06T07:08:09Z")));

      assertEquals("Teste MCP", updatedMetadata.title());
      assertEquals("Teste MCP", updatedMetadata.author());
      assertEquals("Teste MCP", updatedMetadata.subject());
      assertEquals("Teste MCP", updatedMetadata.keywords());
      assertEquals("Teste MCP", updatedMetadata.creator());
      assertEquals("2025-04-05T06:07:08Z", updatedMetadata.creationDate());
      assertEquals("2025-04-06T07:08:09Z", updatedMetadata.modificationDate());
      assertEquals(originalMetadata.producer(), updatedMetadata.producer());
   }

   /** Não permite uma atualização sem nenhum campo solicitado. */
   @Test
   void deveRejeitarAtualizacaoSemCampos() {
      assertThrows(IllegalArgumentException.class,
            () -> service.updateMetadata("qualquer.pdf",
                  new PdfMetadataUpdate(null, null, null, null, null, null, null)));
   }

   /** Não permite caminhos vazios, arquivos ausentes, extensões incorretas ou escape da pasta. */
   @Test
   void deveRejeitarCaminhosInvalidos() throws Exception {
      Path outsideFile = Files.createFile(temporaryDirectory.getParent().resolve("fora.pdf"));
      try {
         assertThrows(IllegalArgumentException.class, () -> service.readMetadata(" "));
         assertThrows(IOException.class, () -> service.readMetadata("inexistente.pdf"));
         assertThrows(IllegalArgumentException.class, () -> service.readMetadata("notas.docx"));
         assertThrows(IllegalArgumentException.class,
               () -> service.readMetadata(outsideFile.toAbsolutePath().toString()));
      }
      finally {
         Files.deleteIfExists(outsideFile);
      }
   }

}
