package com.savelieva.jobdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savelieva.jobdashboard.config.SearchProperties;
import com.savelieva.jobdashboard.model.JobStatus;
import com.savelieva.jobdashboard.repository.StatusRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StatusRepositoryTest {

    private static final String URL = "https://www.linkedin.com/jobs/view/1";

    @TempDir
    Path root;

    Path statusFile;
    StatusRepository repository;

    @BeforeEach
    void setUp() {
        statusFile = root.resolve("DailySearch/_status.json");
        repository = new StatusRepository(
                new SearchProperties(List.of(), statusFile, List.of()), new ObjectMapper());
    }

    @Test
    void readsAnEmptyMapWhenTheFileIsNotThereYet() {
        assertThat(repository.findAll()).isEmpty();
        assertThat(repository.find(URL)).isEqualTo(JobStatus.EMPTY);
    }

    @Test
    void storesAndReadsBackAStatus() {
        repository.save(URL, new JobStatus("applied", "sent on Thursday"));

        assertThat(repository.find(URL)).isEqualTo(new JobStatus("applied", "sent on Thursday"));
        assertThat(statusFile).exists();
    }

    @Test
    void keepsTheOtherEntriesWhenOneIsUpdated() {
        String other = "https://www.linkedin.com/jobs/view/2";
        repository.save(URL, new JobStatus("reviewing", ""));
        repository.save(other, new JobStatus("applied", ""));

        repository.save(URL, new JobStatus("not a fit", ""));

        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.find(other).status()).isEqualTo("applied");
    }

    @Test
    void dropsTheEntryOnceItIsEmptyAgain() {
        repository.save(URL, new JobStatus("reviewing", "note"));

        repository.save(URL, new JobStatus("", ""));

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void treatsNullsAsEmptyRatherThanFailing() {
        repository.save(URL, new JobStatus(null, "just a note"));

        assertThat(repository.find(URL).status()).isEmpty();
        assertThat(repository.find(URL).note()).isEqualTo("just a note");
    }

    @Test
    void readsTheFileThePythonDashboardWrote() throws IOException {
        Files.createDirectories(statusFile.getParent());
        Files.writeString(statusFile, """
                {"%s": {"status": "applied", "note": "via the old app"}}
                """.formatted(URL));

        assertThat(repository.find(URL)).isEqualTo(new JobStatus("applied", "via the old app"));
    }

    @Test
    void writesOnlyStatusAndNote() throws IOException {
        repository.save(URL, new JobStatus("applied", "sent on Thursday"));

        // isEmpty() is a question, not a field: an "empty" key here would leak into the file the
        // Python dashboard reads as well.
        assertThat(Files.readString(statusFile)).doesNotContain("empty");
    }

    @Test
    void keepsGoingWhenTheStatusFileIsCorrupt() throws IOException {
        Files.createDirectories(statusFile.getParent());
        Files.writeString(statusFile, "{ this is not json");

        assertThat(repository.findAll()).isEmpty();

        repository.save(URL, new JobStatus("reviewing", ""));
        assertThat(repository.find(URL).status()).isEqualTo("reviewing");
    }
}
