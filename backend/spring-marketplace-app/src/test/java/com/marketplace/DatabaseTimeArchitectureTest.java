package com.marketplace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * SQL must not read the database clock; times are passed in from the application's clock so that
 * tests can control them (設計_004 12.1, 設計_006 4.1).
 *
 * <p>ArchUnit cannot see inside SQL strings, so this test scans the source files.
 */
class DatabaseTimeArchitectureTest {

  private static final Path SOURCES = Path.of("src", "main");

  private static final Pattern DATABASE_TIME =
      Pattern.compile(
          "(?<![.\\w])now\\s*\\(\\s*\\)"
              + "|\\bcurrent_timestamp\\b|\\blocaltimestamp\\b"
              + "|\\b(clock|statement|transaction)_timestamp\\s*\\(",
          Pattern.CASE_INSENSITIVE);

  @Test
  void sqlDoesNotReadTheDatabaseClock() throws IOException {
    List<String> violations = new ArrayList<>();
    try (Stream<Path> files = Files.walk(SOURCES)) {
      for (Path file : files.filter(DatabaseTimeArchitectureTest::isSourceFile).toList()) {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
          String line = lines.get(i);
          if (!isComment(line) && DATABASE_TIME.matcher(line).find()) {
            violations.add(file + ":" + (i + 1) + ": " + line.strip());
          }
        }
      }
    }
    assertThat(violations).as("SQL that reads the database clock").isEmpty();
  }

  private static boolean isSourceFile(Path file) {
    String name = file.getFileName().toString();
    return name.endsWith(".sql") || name.endsWith(".java");
  }

  private static boolean isComment(String line) {
    String stripped = line.strip();
    return stripped.startsWith("--")
        || stripped.startsWith("//")
        || stripped.startsWith("*")
        || stripped.startsWith("/*");
  }
}
