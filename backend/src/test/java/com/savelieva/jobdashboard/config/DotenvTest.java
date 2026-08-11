package com.savelieva.jobdashboard.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DotenvTest {

    private static final String KEY = "JOB_DASHBOARD_TEST_KEY";

    @TempDir
    Path dir;

    @AfterEach
    void tearDown() {
        System.clearProperty(KEY);
        System.clearProperty("DASHBOARD_ROOT_TEST");
    }

    @Test
    void readsKeyValuePairs() throws IOException {
        Map<String, String> values = Dotenv.read(write("""
                DASHBOARD_ROOT=/Users/someone/CVs
                OTHER=42
                """));

        assertThat(values)
                .containsEntry("DASHBOARD_ROOT", "/Users/someone/CVs")
                .containsEntry("OTHER", "42");
    }

    @Test
    void skipsCommentsAndBlankLines() throws IOException {
        Map<String, String> values = Dotenv.read(write("""
                # the path to the CVs folder

                DASHBOARD_ROOT=/tmp/cvs
                """));

        assertThat(values).containsExactly(Map.entry("DASHBOARD_ROOT", "/tmp/cvs"));
    }

    @Test
    void stripsSurroundingQuotes() throws IOException {
        Map<String, String> values = Dotenv.read(write("DASHBOARD_ROOT=\"/path with spaces/CVs\"\n"));

        assertThat(values).containsEntry("DASHBOARD_ROOT", "/path with spaces/CVs");
    }

    @Test
    void keepsEqualsSignsInsideTheValue() throws IOException {
        assertThat(Dotenv.read(write("TOKEN=abc=def=ghi\n"))).containsEntry("TOKEN", "abc=def=ghi");
    }

    @Test
    void setsTheValueAsASystemProperty() throws IOException {
        write(KEY + "=from-the-file\n");

        Dotenv.load(dir);

        assertThat(System.getProperty(KEY)).isEqualTo("from-the-file");
    }

    @Test
    void findsTheFileInAParentDirectory() throws IOException {
        // Maven runs the app from backend/ while .env sits at the root of the repository.
        write(KEY + "=from-the-parent\n");
        Path nested = Files.createDirectories(dir.resolve("backend"));

        Dotenv.load(nested);

        assertThat(System.getProperty(KEY)).isEqualTo("from-the-parent");
    }

    @Test
    void doesNotOverrideAValueThatIsAlreadySet() throws IOException {
        write(KEY + "=from-the-file\n");
        System.setProperty(KEY, "set-earlier");

        Dotenv.load(dir);

        assertThat(System.getProperty(KEY)).isEqualTo("set-earlier");
    }

    @Test
    void staysQuietWhenThereIsNoFile() {
        Dotenv.load(dir);

        assertThat(System.getProperty(KEY)).isNull();
    }

    private Path write(String content) throws IOException {
        Path file = dir.resolve(".env");
        Files.writeString(file, content);
        return file;
    }
}
