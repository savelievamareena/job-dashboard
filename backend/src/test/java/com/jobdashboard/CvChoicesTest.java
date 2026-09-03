package com.jobdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobdashboard.model.CvChoices;
import com.jobdashboard.model.CvKind;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The rules deciding which CV answers for a posting. */
class CvChoicesTest {

    private static final String CLICKUP = "https://www.linkedin.com/jobs/view/4393492829";
    private static final String HIRE_FEED_CSS = "https://www.linkedin.com/jobs/view/4451332672";
    private static final String HIRE_FEED_JS = "https://www.linkedin.com/jobs/view/4451338634";

    @Test
    void answersEachPostingOfTheSameCompanySeparately() {
        // Both ads are Hire Feed, and only one of them was tailored: the other has to keep saying
        // "core CV" instead of inheriting its neighbour's answer.
        CvChoices choices = choices(queue()
                .url(HIRE_FEED_CSS, CvKind.TAILORED)
                .url(HIRE_FEED_JS, CvKind.BASE));

        assertThat(choices.find("frontend", HIRE_FEED_CSS, "Hire Feed")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", HIRE_FEED_JS, "Hire Feed")).isEqualTo(CvKind.BASE);
    }

    @Test
    void saysNothingAboutAPostingTheQueueDoesNotName() {
        CvChoices choices = choices(queue().url(HIRE_FEED_CSS, CvKind.TAILORED));

        assertThat(choices.find("frontend", HIRE_FEED_JS, "Hire Feed")).isNull();
    }

    @Test
    void letsARowWithoutAUrlAnswerForEveryPostingOfThatCompany() {
        // Written before the queue carried a URL: it cannot say which ad it was built for, and
        // answering for all of them beats dropping what is known.
        CvChoices choices = choices(queue().company("Hired", CvKind.BASE));

        assertThat(choices.find("frontend", HIRE_FEED_JS, "Hired")).isEqualTo(CvKind.BASE);
    }

    @Test
    void prefersTheRowNamingThePostingOverTheCompanyWideOne() {
        CvChoices choices = choices(queue()
                .company("Hire Feed", CvKind.BASE)
                .url(HIRE_FEED_CSS, CvKind.TAILORED));

        assertThat(choices.find("frontend", HIRE_FEED_CSS, "Hire Feed")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", HIRE_FEED_JS, "Hire Feed")).isEqualTo(CvKind.BASE);
    }

    @Test
    void answersATrackFromItsOwnCoreOnly() {
        // The two cores disagree on job titles and on the 2022-2024 employer, so a fullstack CV is
        // not an answer for a frontend posting: that row has to read as "nothing built yet".
        CvChoices choices = new CvChoices(Map.of(
                "frontend", queue().build(),
                "fullstack", queue().url(CLICKUP, CvKind.TAILORED).build()));

        assertThat(choices.find("fullstack", CLICKUP, "Regnology")).isEqualTo(CvKind.TAILORED);
        assertThat(choices.find("frontend", CLICKUP, "Regnology")).isNull();
    }

    @Test
    void answersATrackWithoutACoreFromWhicheverCoreKnowsThePosting() {
        CvChoices choices = choices(queue().url(CLICKUP, CvKind.TAILORED));

        assertThat(choices.find("other-stacks", CLICKUP, "MWDN")).isEqualTo(CvKind.TAILORED);
    }

    @Test
    void matchesTheCompanyRegardlessOfCase() {
        CvChoices choices = choices(queue().company("Hire Feed", CvKind.TAILORED));

        assertThat(choices.find("frontend", "", "hire feed")).isEqualTo(CvKind.TAILORED);
    }

    @Test
    void saysNothingWhenNoCoreHasAQueueYet() {
        // What an empty cv_queue looks like: every configured core is still listed, so a track
        // still finds its own core and simply gets no answer out of it.
        CvChoices choices = choices(queue());

        assertThat(choices.find("frontend", CLICKUP, "ClickUp")).isNull();
        assertThat(choices.find("other-stacks", CLICKUP, "ClickUp")).isNull();
    }

    /** One core named "frontend", the shape {@code CvRepository} builds out of a single core. */
    private CvChoices choices(Queue queue) {
        return new CvChoices(Map.of("frontend", queue.build()));
    }

    private Queue queue() {
        return new Queue();
    }

    private static final class Queue {

        private final Map<String, CvKind> byUrl = new LinkedHashMap<>();
        private final Map<String, CvKind> byCompany = new LinkedHashMap<>();

        Queue url(String url, CvKind kind) {
            byUrl.put(url, kind);
            return this;
        }

        Queue company(String company, CvKind kind) {
            byCompany.put(company.toLowerCase(Locale.ROOT), kind);
            return this;
        }

        CvChoices.Queue build() {
            return new CvChoices.Queue(byUrl, byCompany);
        }
    }
}
