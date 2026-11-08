
public class ProcessTest {
	   public static void main(String[] args)
			         throws Exception {
		   /*
		   System.err.println(
					      "Console charset: "
					            + (System.console() == null
					                  ? "null"
					                  : System.console().charset())
					);

					System.err.println(
					      "stderr.encoding: "
					            + System.getProperty("stderr.encoding")
					);

					System.err.println(
					      "native.encoding: "
					            + System.getProperty("native.encoding")
					);

					System.err.println(
					      "file.encoding: "
					            + System.getProperty("file.encoding")
					);
					System.err.println(
							      "Teste UTF-8: á é í ó ú ç ã õ Â Ê Ó"
							);
	*/

			      Process process =
			            new ProcessBuilder(
			                  "java",
			                  "-jar",
			                  "mcp_server01_server.jar"
			            )
			            .inheritIO()
			            .start();

			      System.out.println(
			            "PID: " + process.pid()
			      );

			      Thread.sleep(5000);

			      System.out.println(
			            "Fechando 'stdin' do processo..."
			      );

			      process.getOutputStream().close();

			      int exitCode =
			            process.waitFor();

			      System.out.println(
			            "Exit code: " + exitCode
			      );
			   }
}
