import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import javax.tools.ToolProvider;

import org.jooq.codegen.GenerationTool;
import org.jooq.meta.jaxb.Configuration;
import org.jooq.meta.jaxb.Database;
import org.jooq.meta.jaxb.Generator;
import org.jooq.meta.jaxb.Jdbc;
import org.jooq.meta.jaxb.Strategy;
import org.jooq.meta.jaxb.Target;
import org.jooq.meta.jaxb.ForcedType;

public class JooqCodegen {
    private record CliArguments(String jdbcUrl, String schema, String packageName, String directory, String jarName) {
        String safeJdbcUrl() {
            return removeCredentials(jdbcUrl);
        }
    }

    public static void main(String[] args) {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Expected 5 arguments: <JDBC_URL> <SCHEMA> <PACKAGE_NAME> <DIRECTORY> <JAR_NAME>");
        }

        final var cliArgs = new CliArguments(args[0], args[1], args[2], args[3], args[4]);

        final var configuration = new Configuration()
                .withJdbc(new Jdbc()
                        .withDriver("org.postgresql.Driver")
                        .withUrl(cliArgs.jdbcUrl()))
                .withGenerator(new Generator()
                        .withName("org.jooq.codegen.DefaultGenerator")
                        .withStrategy(new Strategy()
                                .withName("org.jooq.codegen.DefaultGeneratorStrategy"))
                        .withDatabase(new Database()
                                .withName("org.jooq.meta.postgres.PostgresDatabase")
                                .withInputSchema(cliArgs.schema())
                                .withForcedTypes(
                                        new ForcedType()
                                                .withUserType("com.fasterxml.jackson.databind.JsonNode")
                                                .withJsonConverter(true)
                                                .withIncludeTypes("JSON|JSONB")))
                        .withTarget(new Target()
                                .withPackageName(cliArgs.packageName())
                                .withDirectory(cliArgs.directory())));

        try {
            // Generate code
            GenerationTool.generate(configuration);

            // Compile generated code
            compileGeneratedCode(cliArgs.directory());

            // Create JAR file
            createJarFile(cliArgs);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void compileGeneratedCode(final String directory) throws IOException {
        final var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "Cannot find the system Java compiler. Check that your class path includes tools.jar");
        }

        try (final var fileManager = compiler.getStandardFileManager(null, null, null)) {
            final var javaFiles = Files.walk(Paths.get(directory))
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(Path::toFile)
                    .toList();

            final var compilationUnits = fileManager.getJavaFileObjectsFromFiles(javaFiles);

            final var options = List.of("-d", directory);
            final var task = compiler.getTask(null, fileManager, null, options, null,
                    compilationUnits);

            if (!task.call()) {
                throw new RuntimeException("Compilation failed");
            }
        }
    }

    private static void createJarFile(final CliArguments cliArgs) throws IOException {
        final var dirPath = Paths.get(cliArgs.directory());

        // Create a manifest with metadata
        final var manifest = new Manifest();
        final var attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(new Attributes.Name("Generated-By"), JooqCodegen.class.getName());
        attributes.put(new Attributes.Name("Generated-By-Java-Version"), System.getProperty("java.version"));
        attributes.put(new Attributes.Name("Generated-At"), new Date().toString());
        attributes.put(new Attributes.Name("Generated-On-Host-Name"), getHostName());
        attributes.put(new Attributes.Name("Generated-On-Host-IP"), getHostIP());
        attributes.put(new Attributes.Name("Generated-On-OS-Name"), System.getProperty("os.name"));
        attributes.put(new Attributes.Name("Generated-On-OS-Version"), System.getProperty("os.version"));
        attributes.put(new Attributes.Name("Generated-From-Schema"), cliArgs.schema());
        attributes.put(new Attributes.Name("Generated-From-JDBC-URL"), cliArgs.safeJdbcUrl());
        attributes.put(new Attributes.Name("Package-Name"), cliArgs.packageName());
        attributes.put(new Attributes.Name("Build-Dir"), cliArgs.directory());

        try (final var jos = new JarOutputStream(Files.newOutputStream(Paths.get(cliArgs.jarName())), manifest)) {
            Files.walk(dirPath)
                    .filter(path -> path.toString().endsWith(".class") || path.toString().endsWith(".java"))
                    .forEach(path -> {
                        final var entryName = dirPath.relativize(path).toString().replace("\\", "/");
                        try {
                            jos.putNextEntry(new JarEntry(entryName));
                            Files.copy(path, jos);
                            jos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        System.out.println("%s:META-INF/MANIFEST.MF".formatted(cliArgs.jarName()));
        try (final var jar = new java.util.zip.ZipFile(cliArgs.jarName())) {
            jar.stream()
                    .filter(e -> e.getName().equals("META-INF/MANIFEST.MF"))
                    .findFirst()
                    .ifPresent(e -> {
                        try (final var is = jar.getInputStream(e)) {
                            is.transferTo(System.out);
                        } catch (java.io.IOException ex) {
                            ex.printStackTrace();
                        }
                    });
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private static String getHostIP() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            return "Unknown";
        }
    }

    private static String removeCredentials(final String jdbcUrl) {
        try {
            final var uri = new URI(jdbcUrl.replaceFirst("^jdbc:", ""));
            // remove the userInfo and query parts of the URI since those will include
            // credentials
            final var safeUri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null);
            return "jdbc:" + safeUri.toString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid JDBC URL", e);
        }
    }
}
