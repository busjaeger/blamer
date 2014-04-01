package edu.uiuc.cs.dais.cfg;

import static java.nio.file.StandardOpenOption.CREATE;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

public class ClasspathAggregator {

    public static void main(String[] args) throws IOException {
        String classpathFilePath = args.length > 0 ? args[0] : "/home/bbusjaeger/Desktop/classpath";
        String tempDirPath = args.length > 1 ? args[1] : "/tmp/jars";

        Path tempDir = Paths.get(tempDirPath);
        Files.createDirectories(tempDir);

        Path p = Paths.get(classpathFilePath);
        String line = Files.readAllLines(p, Charset.forName("UTF-8")).get(0);

        final Path jarPath = tempDir.resolve("AAA.jar");
        Files.delete(jarPath);
        final Set<String> entries = new HashSet<>();
        final JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath, CREATE));
        for (String elem : line.split(":")) {
            final Path e = Paths.get(elem);
            if (Files.isDirectory(e)) {
                Files.walkFileTree(e, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String relative = e.relativize(file).toString();
                        if (entries.add(relative)) {
                            jar.putNextEntry(new ZipEntry(relative.toString()));
                            Files.copy(file, jar);
                            jar.closeEntry();
                        } else {
                            System.err.println("Ommiting duplicate entry " + relative);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            if (Files.isRegularFile(e)) {
                Path target = tempDir.resolve(e.getFileName());
                if (!Files.exists(target)) {
                    Files.copy(e, tempDir.resolve(e.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        jar.close();
    }

}
