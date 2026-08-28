package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.Vacancy;
import java.util.List;

/** Source of the postings the dashboard shows: only the ones the loader marked is_selected. */
public interface VacancyRepository {

    /** All picked postings, newest pick first. Marks and CVs are not filled in here. */
    List<Vacancy> findSelected();

    /** Stores where the Apply button leads, as pasted on the board. Null or blank clears it. */
    void saveApplyUrl(String url, String applyUrl);

    void saveMaySubmit(String url, boolean maySubmit);
}
