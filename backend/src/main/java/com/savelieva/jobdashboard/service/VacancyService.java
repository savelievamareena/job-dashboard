package com.savelieva.jobdashboard.service;

import com.savelieva.jobdashboard.model.CvChoices;
import com.savelieva.jobdashboard.model.JobStatus;
import com.savelieva.jobdashboard.model.Vacancy;
import com.savelieva.jobdashboard.repository.CvRepository;
import com.savelieva.jobdashboard.repository.StatusRepository;
import com.savelieva.jobdashboard.repository.VacancyRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Joins the picked postings with the marks the dashboard has stored for them and with the CV the
 * tailoring built for each company.
 *
 * <p>Three queries rather than one join. They read three tables that answer three different
 * questions, the two small ones are a handful of rows each, and keeping them apart is what lets
 * the marks be written without touching anything the loader owns.
 */
@Service
public class VacancyService {

    /**
     * The statuses offered in the UI. The empty one means the posting has not been touched yet;
     * "closed" means the posting is gone from LinkedIn, so it is dead rather than rejected.
     */
    public static final List<String> STATUSES = List.of("", "applied", "not a fit", "closed");

    /** Statuses that take a posting out of play: the board greys these rows out. */
    public static final List<String> INACTIVE = List.of("not a fit", "closed");

    private final VacancyRepository vacancies;
    private final StatusRepository statuses;
    private final CvRepository cvs;

    public VacancyService(VacancyRepository vacancies, StatusRepository statuses,
                          CvRepository cvs) {
        this.vacancies = vacancies;
        this.statuses = statuses;
        this.cvs = cvs;
    }

    public List<Vacancy> findAll() {
        Map<String, JobStatus> stored = statuses.findAll();
        // Both sides are read once here: the postings are joined in memory, not file by file.
        CvChoices built = cvs.findAll();
        return vacancies.findSelected().stream()
                .map(v -> v.withStatus(stored.getOrDefault(v.url(), JobStatus.EMPTY))
                        .withCv(built.find(v.track(), v.url(), v.company())))
                .toList();
    }

    public JobStatus updateStatus(String url, JobStatus status) {
        statuses.save(url, status);
        return status;
    }

    /**
     * Stores the address behind the Apply button, as pasted on the board.
     *
     * <p>Validated here rather than at the edge because the column is read as a link: an address
     * the browser cannot open is worse than an empty cell, which at least reads as "nobody has
     * looked yet". Blank clears the cell, and clearing is always allowed.
     *
     * @throws InvalidApplyUrlException when the text is not an absolute http(s) address
     */
    public String updateApplyUrl(String url, String applyUrl) {
        String value = applyUrl == null ? "" : applyUrl.strip();
        if (!value.isEmpty() && !isHttpUrl(value)) {
            throw new InvalidApplyUrlException(value);
        }
        vacancies.saveApplyUrl(url, value);
        return value;
    }

    private static boolean isHttpUrl(String value) {
        try {
            String scheme = java.net.URI.create(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** What arrived in the apply link box was not a link. */
    public static class InvalidApplyUrlException extends RuntimeException {
        public InvalidApplyUrlException(String value) {
            super("not an http(s) address: " + value);
        }
    }
}
