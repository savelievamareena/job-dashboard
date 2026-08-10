package com.savelieva.jobdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.savelieva.jobdashboard.config.CvProperties;
import com.savelieva.jobdashboard.model.CvChoices;
import com.savelieva.jobdashboard.model.CvKind;
import com.savelieva.jobdashboard.repository.CvRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CvRepositoryTest {

    private static final String CLICKUP = "https://www.linkedin.com/jobs/view/4393492829";
    private static final String HIRE_FEED_CSS = "https://www.linkedin.com/jobs/view/4451332672";
    private static final String HIRE_FEED_JS = "https://www.linkedin.com/jobs/view/4451338634";

    @TempDir
    Path root;

    CvRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CvRepository(
                new CvProperties(root.resolve("cv-tailored/Poland"), List.of("Frontend", "Fullstack")));
    }

    @Test
    void readsATailoredCvFromTheFolderNamedAfterTheCompany() throws IOException {
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                ClickUp,%s,cv-tailored/Poland/Frontend/ClickUp/00001-Marina_Savelieva.pdf,2026-08-09,
                """.formatted(CLICKUP));

        assertThat(repository.findAll().find("frontend", CLICKUP, "ClickUp"))
                .isEqualTo(CvKind.TAILORED);
    }

    @Test
    void readsTheCoreCvFromAPathOutsideTheCompanyFolder() throws IOException {
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Quik Hire Staffing,%s,cv-tailored/Poland/Frontend/gap/00001-Marina_Savelieva.pdf,2026-08-09,
                """.formatted(CLICKUP));

        assertThat(repository.findAll().find("frontend", CLICKUP, "Quik Hire Staffing"))
                .isEqualTo(CvKind.BASE);
    }

    @Test
    void answersEachPostingOfTheSameCompanySeparately() throws IOException {
        // Both ads are Hire Feed, and only one of them was tailored: the other has to keep saying
        // "core CV" instead of inheriting its neighbour's answer.
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Hire Feed,%s,cv-tailored/Poland/Frontend/Hire Feed/00001-Marina_Savelieva.pdf,2026-08-09,
                Hire Feed,%s,cv-tailored/Poland/Frontend/gap/00001-Marina_Savelieva.pdf,2026-08-09,
                """.formatted(HIRE_FEED_CSS, HIRE_FEED_JS));

        CvChoices choices = repository.findAll();

        assertThat(choices.find("frontend", HIRE_FEED_CSS, "Hire Feed")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", HIRE_FEED_JS, "Hire Feed")).isEqualTo(CvKind.BASE);
    }

    @Test
    void saysNothingAboutAPostingTheQueueDoesNotName() throws IOException {
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Hire Feed,%s,cv-tailored/Poland/Frontend/Hire Feed/00001-Marina_Savelieva.pdf,2026-08-09,
                """.formatted(HIRE_FEED_CSS));

        assertThat(repository.findAll().find("frontend", HIRE_FEED_JS, "Hire Feed")).isNull();
    }

    @Test
    void letsARowWithoutAUrlAnswerForEveryPostingOfThatCompany() throws IOException {
        // Written before the queue carried a URL: it cannot say which ad it was built for, and
        // answering for all of them beats dropping what is known.
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Hired,,cv-tailored/Poland/Frontend/gap/00001-Marina_Savelieva.pdf,2026-08-07,
                """);

        assertThat(repository.findAll().find("frontend", HIRE_FEED_JS, "Hired"))
                .isEqualTo(CvKind.BASE);
    }

    @Test
    void prefersTheRowNamingThePostingOverTheCompanyWideOne() throws IOException {
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Hire Feed,,cv-tailored/Poland/Frontend/gap/00001-Marina_Savelieva.pdf,2026-08-07,
                Hire Feed,%s,cv-tailored/Poland/Frontend/Hire Feed/00001-Marina_Savelieva.pdf,2026-08-09,
                """.formatted(HIRE_FEED_CSS));

        CvChoices choices = repository.findAll();

        assertThat(choices.find("frontend", HIRE_FEED_CSS, "Hire Feed")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", HIRE_FEED_JS, "Hire Feed")).isEqualTo(CvKind.BASE);
    }

    @Test
    void answersATrackFromItsOwnCoreOnly() throws IOException {
        // The two cores disagree on job titles and on the 2022-2024 employer, so a fullstack CV is
        // not an answer for a frontend posting: that row has to read as "nothing built yet".
        queue("Fullstack", """
                company,url,pdf_path,built,verdict
                Regnology,%s,cv-tailored/Poland/Fullstack/Regnology/00001-Marina_Savelieva.pdf,2026-08-09,
                """.formatted(CLICKUP));

        CvChoices choices = repository.findAll();

        assertThat(choices.find("fullstack", CLICKUP, "Regnology")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", CLICKUP, "Regnology")).isNull();
    }

    @Test
    void answersATrackWithoutACoreFromWhicheverCoreKnowsThePosting() throws IOException {
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                MWDN,%s,cv-tailored/Poland/Frontend/MWDN/00001-Marina_Savelieva.pdf,2026-08-07,
                """.formatted(CLICKUP));

        assertThat(repository.findAll().find("other-stacks", CLICKUP, "MWDN"))
                .isEqualTo(CvKind.TAILORED);
    }

    @Test
    void matchesTheCompanyRegardlessOfCase() throws IOException {
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Hire Feed,,cv-tailored/Poland/Frontend/Hire Feed/00001-Marina_Savelieva.pdf,2026-08-09,
                """);

        assertThat(repository.findAll().find("frontend", "", "hire feed")).isEqualTo(CvKind.TAILORED);
    }

    @Test
    void keepsReadingWhenAVerdictHoldsCommas() throws IOException {
        // Verdicts are written by hand and run long: an unquoted comma must not lose the row.
        queue("Frontend", """
                company,url,pdf_path,built,verdict
                Etteplan,%s,cv-tailored/Poland/Frontend/Etteplan/00001-Marina_Savelieva.pdf,2026-08-07,fixed the page break, rebuilt
                Tesco Technology,%s,cv-tailored/Poland/Frontend/Tesco Technology/00001-Marina_Savelieva.pdf,2026-08-07,
                """.formatted(HIRE_FEED_CSS, HIRE_FEED_JS));

        CvChoices choices = repository.findAll();

        assertThat(choices.find("frontend", HIRE_FEED_CSS, "Etteplan")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", HIRE_FEED_JS, "Tesco Technology"))
                .isEqualTo(CvKind.TAILORED);
    }

    @Test
    void survivesAMissingCvRoot() {
        assertThat(repository.findAll()).isEqualTo(CvChoices.EMPTY);
        assertThat(repository.findAll().find("frontend", CLICKUP, "ClickUp")).isNull();
    }

    private void queue(String core, String csv) throws IOException {
        Path file = root.resolve("cv-tailored/Poland").resolve(core).resolve("review-queue.csv");
        Files.createDirectories(file.getParent());
        Files.writeString(file, csv);
    }
}
