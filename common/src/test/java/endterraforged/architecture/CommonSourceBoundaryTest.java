package endterraforged.architecture;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class CommonSourceBoundaryTest {

    private static final Path COMMON_MAIN_JAVA = commonMainJava();
    private static final Path COMMON_MAIN_RESOURCES = commonMainResources();
    private static final Path NEOFORGE_MAIN_JAVA = neoForgeMainJava();
    private static final String[] FORBIDDEN_TOKENS = {
            "com.lowdragmc.",
            "com/lowdragmc/",
            "net.fabricmc.",
            "net/fabricmc/",
            "net.neoforged.",
            "net/neoforged/"
    };
    private static final Set<String> ALLOWED_TEXT_REFERENCES = Set.of(
            "LdLib2ActionBars.java::com.lowdragmc."
    );

    @Test
    void commonJavaDoesNotReferencePlatformOrLdLib2Apis() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(COMMON_MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                collectForbiddenTextReferences(file, violations);
            }
        }
        if (!violations.isEmpty()) {
            fail("common Java source must not directly reference platform or LDLib2 APIs:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void neoForgeSourcesDoNotDeclareCommonPackages() throws IOException {
        Set<String> commonPackages = packageNames(COMMON_MAIN_JAVA);
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(NEOFORGE_MAIN_JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String packageName = packageName(file);
                if (commonPackages.contains(packageName)) {
                    violations.add(file + " -> " + packageName);
                }
            }
        }
        if (!violations.isEmpty()) {
            fail("NeoForge sources must not declare packages owned by common because the development runtime "
                    + "loads them as separate modules:\n" + String.join("\n", violations));
        }
    }

    @Test
    void commonResourcesDoNotReferencePlatformApis() throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> files = Files.walk(COMMON_MAIN_RESOURCES)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                collectForbiddenTextReferences(file, violations);
            }
        }
        if (!violations.isEmpty()) {
            fail("common resources must not reference platform-specific APIs:\n"
                    + String.join("\n", violations));
        }
    }

    private static void collectForbiddenTextReferences(Path file, List<String> violations) throws IOException {
        List<String> lines = Files.readAllLines(file);
        Set<String> reported = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            for (String forbidden : FORBIDDEN_TOKENS) {
                if (line.contains(forbidden) && reported.add((i + 1) + forbidden)
                        && !isAllowedTextReference(file, forbidden)) {
                    violations.add(file + ":" + (i + 1) + " -> " + line.strip());
                }
            }
        }
    }

    private static Set<String> packageNames(Path sourceRoot) throws IOException {
        Set<String> packages = new HashSet<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                packages.add(packageName(file));
            }
        }
        return packages;
    }

    private static String packageName(Path file) throws IOException {
        for (String line : Files.readAllLines(file)) {
            String stripped = line.strip();
            if (stripped.startsWith("package ") && stripped.endsWith(";")) {
                return stripped.substring("package ".length(), stripped.length() - 1);
            }
        }
        throw new IOException("Java source has no package declaration: " + file);
    }

    private static boolean isAllowedTextReference(Path file, String forbidden) {
        String key = file.getFileName() + "::" + forbidden;
        return ALLOWED_TEXT_REFERENCES.contains(key);
    }

    private static Path commonMainJava() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("common", "src", "main", "java");
    }

    private static Path commonMainResources() {
        Path moduleRelative = Path.of("src", "main", "resources");
        if (Files.isDirectory(moduleRelative)) {
            return moduleRelative;
        }
        return Path.of("common", "src", "main", "resources");
    }

    private static Path neoForgeMainJava() {
        Path siblingModule = Path.of("..", "neoforge", "src", "main", "java");
        if (Files.isDirectory(siblingModule)) {
            return siblingModule;
        }
        return Path.of("neoforge", "src", "main", "java");
    }
}
