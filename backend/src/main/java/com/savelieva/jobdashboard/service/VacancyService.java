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
 * Joins the selected postings on disk with the statuses the dashboard has stored for them and with
 * the CV the tailoring built for each company.
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
}
