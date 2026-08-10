package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.Vacancy;
import java.util.List;

/**
 * Source of the postings the dashboard shows. Only the selected ones are ever returned: a posting
 * that did not make it into selected.csv does not belong on this board.
 */
public interface VacancyRepository {

    /** All selected postings, newest date first. Statuses are not filled in here. */
    List<Vacancy> findSelected();
}
