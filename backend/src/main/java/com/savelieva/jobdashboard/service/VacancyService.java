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

/** Joins the picked postings with their marks and the CV built for the company: three queries. */
@Service
public class VacancyService {

    /** The statuses offered in the UI; empty means untouched, "closed" means gone from LinkedIn. */
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

    /** Stores the pasted Apply address; blank clears it, anything not http(s) is refused. */
    public String updateApplyUrl(String url, String applyUrl) {
        String value = applyUrl == null ? "" : applyUrl.strip();
        if (!value.isEmpty() && !isHttpUrl(value)) {
            throw new InvalidApplyUrlException(value);
        }
        vacancies.saveApplyUrl(url, value);
        return value;
    }

    public boolean updateMaySubmit(String url, boolean maySubmit) {
        vacancies.saveMaySubmit(url, maySubmit);
        return maySubmit;
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
