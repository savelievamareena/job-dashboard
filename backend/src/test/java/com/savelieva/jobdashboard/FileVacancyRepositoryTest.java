package com.savelieva.jobdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.savelieva.jobdashboard.config.SearchProperties;
import com.savelieva.jobdashboard.model.Vacancy;
import com.savelieva.jobdashboard.repository.FileVacancyRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileVacancyRepositoryTest {

    @TempDir
    Path root;

    FileVacancyRepository repository;

    @BeforeEach
    void setUp() {
        SearchProperties properties = new SearchProperties(
                List.of(root.resolve("DailySearch")),
                root.resolve("DailySearch/_status.json"),
                List.of("frontend", "fullstack"));
        repository = new FileVacancyRepository(properties, new ObjectMapper());
    }

    @Test
    void returnsOnlySelectedPostings() throws IOException {
        Path day = day("2026-08-05");
        jobs(day, "frontend", """
                company,url,stack
                Atos,https://www.linkedin.com/jobs/view/1,React
                Ignored,https://www.linkedin.com/jobs/view/2,Vue
                """);
        selected(day, "frontend", """
                company,title,url,gap
                Atos,Senior Frontend Developer,https://www.linkedin.com/jobs/view/1,Azure
                """);

        List<Vacancy> found = repository.findSelected();

        assertThat(found).singleElement().satisfies(v -> {
            assertThat(v.company()).isEqualTo("Atos");
            assertThat(v.url()).endsWith("/1");
            assertThat(v.stack()).isEqualTo("React");
            assertThat(v.gap()).isEqualTo("Azure");
            assertThat(v.track()).isEqualTo("frontend");
            assertThat(v.date()).isEqualTo("2026-08-05");
            assertThat(v.source()).isEqualTo("Daily");
        });
    }

    @Test
    void skipsTracksWithNothingSelected() throws IOException {
        Path day = day("2026-08-05");
        jobs(day, "fullstack", """
                company,url
                Regnology,https://www.linkedin.com/jobs/view/9
                """);

        assertThat(repository.findSelected()).isEmpty();
    }

    @Test
    void enrichesFromTheCachedRecord() throws IOException {
        Path day = day("2026-08-04");
        jobs(day, "frontend", """
                company,url
                Tripadvisor,https://www.linkedin.com/jobs/view/42
                """);
        selected(day, "frontend", """
                company,title,url,gap
                Tripadvisor,,https://www.linkedin.com/jobs/view/42,
                """);
        Files.writeString(day.resolve("_descriptions/42.json"), """
                {"job_title":"Software Engineer II","experience_level":"Mid-Senior level",
                 "job_type":"Full-time","location":"Cracow, Poland","applicants":"12",
                 "easy_apply":true,"skills":"None"}
                """);
        Files.writeString(day.resolve("_descriptions/42.txt"), "description text");

        Vacancy found = repository.findSelected().getFirst();

        assertThat(found.title()).isEqualTo("Software Engineer II");
        assertThat(found.level()).isEqualTo("Mid-Senior level");
        assertThat(found.location()).isEqualTo("Cracow, Poland");
        assertThat(found.applicants()).isEqualTo("12");
        assertThat(found.easyApply()).isTrue();
        assertThat(found.hasText()).isTrue();
    }

    @Test
    void fallsBackToTheOlderRecordShape() throws IOException {
        Path day = day("2026-08-04");
        jobs(day, "frontend", """
                company,url
                Old,https://www.linkedin.com/jobs/view/7
                """);
        selected(day, "frontend", """
                company,title,url,gap
                Old,Title from csv,https://www.linkedin.com/jobs/view/7,
                """);
        Files.writeString(day.resolve("_descriptions/7.json"),
                """
                {"job_location":"Warsaw, Poland","applies":"3"}
                """);

        Vacancy found = repository.findSelected().getFirst();

        assertThat(found.location()).isEqualTo("Warsaw, Poland");
        assertThat(found.applicants()).isEqualTo("3");
        assertThat(found.title()).isEqualTo("Title from csv");
        // The flag predates this record shape: unknown, not "no".
        assertThat(found.easyApply()).isNull();
        assertThat(found.hasText()).isFalse();
    }

    @Test
    void takesTheTitleFromTitlesJsonWhenTheRecordHasNone() throws IOException {
        Path day = day("2026-08-04");
        jobs(day, "frontend", """
                company,url
                Some,https://www.linkedin.com/jobs/view/5
                """);
        selected(day, "frontend", """
                company,title,url,gap
                Some,,https://www.linkedin.com/jobs/view/5,
                """);
        Files.writeString(day.resolve("_titles.json"), """
                {"5":"Frontend Engineer"}
                """);

        assertThat(repository.findSelected().getFirst().title()).isEqualTo("Frontend Engineer");
    }

    @Test
    void sortsNewestDayFirst() throws IOException {
        for (String date : List.of("2026-08-04", "2026-08-05")) {
            Path day = day(date);
            jobs(day, "frontend", "company,url\nX,https://www.linkedin.com/jobs/view/" + date + "\n");
            selected(day, "frontend",
                    "company,title,url,gap\nX,T,https://www.linkedin.com/jobs/view/" + date + ",\n");
        }

        assertThat(repository.findSelected()).extracting(Vacancy::date)
                .containsExactly("2026-08-05", "2026-08-04");
    }

    @Test
    void survivesAMissingSearchRoot() {
        assertThat(repository.findSelected()).isEmpty();
    }

    private Path day(String date) throws IOException {
        Path day = root.resolve("DailySearch").resolve(date);
        Files.createDirectories(day.resolve("_descriptions"));
        return day;
    }

    private void jobs(Path day, String track, String csv) throws IOException {
        write(day.resolve(track).resolve("jobs.csv"), csv);
    }

    private void selected(Path day, String track, String csv) throws IOException {
        write(day.resolve(track).resolve("selected.csv"), csv);
    }

    private void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
