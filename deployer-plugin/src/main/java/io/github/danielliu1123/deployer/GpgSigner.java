package io.github.danielliu1123.deployer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class GpgSigner {

    public static void signDirectory(Path dir, String armoredPrivateKey, String passphrase) throws Exception {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Path is not a directory: " + dir);
        }

        // 1. create temporary GNUPGHOME
        Path gnupgHome = Files.createTempDirectory("gnupg-home");
        Files.createDirectories(gnupgHome.resolve("private-keys-v1.d"));

        // 2. write armored private key to a temp file
        Path keyFile = gnupgHome.resolve("private.key");
        Files.writeString(keyFile, armoredPrivateKey);

        // 3. import the private key
        runGpg(gnupgHome, List.of("gpg", "--batch", "--yes", "--import", keyFile.toString()), null);

        // 4. get the fingerprint of the imported key
        String fingerprint = runGpg(
                gnupgHome,
                List.of("gpg", "--with-colons", "--list-secret-keys"),
                null
        ).lines()
                .filter(l -> l.startsWith("fpr:"))
                .map(l -> l.split(":")[9])
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Failed to get key fingerprint"));

        // 5. sign all relevant files in the directory
        try (var stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .filter(e -> e.toString().endsWith(".jar")
                                 || e.toString().endsWith(".pom")
                                 || e.toString().endsWith(".xml")
                                 || e.toString().endsWith(".module")
                    )
                    .forEach(file -> {
                        try {
                            signFile(gnupgHome, file, fingerprint, passphrase);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        // 6. cleanup
        deleteRecursively(gnupgHome);
    }

    private static void signFile(Path gnupgHome, Path file, String fingerprint, String passphrase) throws Exception {
        Path asc = file.resolveSibling(file.getFileName() + ".asc");

        System.out.println("Signing: " + file);

        List<String> cmd = List.of(
                "gpg",
                "--batch",
                "--yes",
                "--pinentry-mode", "loopback",
                "--passphrase", passphrase,
                "--armor",
                "--local-user", fingerprint,
                "--detach-sign",
                "--output", asc.toString(),
                file.toString()
        );

        runGpg(gnupgHome, cmd, null);
    }

    private static String runGpg(Path gnupgHome, List<String> cmd, String input) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().put("GNUPGHOME", gnupgHome.toString());
        pb.redirectErrorStream(true);

        Process p = pb.start();

        if (input != null) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(input.getBytes());
            }
        }

        String output = new String(p.getInputStream().readAllBytes());
        int exit = p.waitFor();
        if (exit != 0) {
            throw new RuntimeException("gpg failed: " + output);
        }

        return output;
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception ignored) {
                    }
                });
    }
}