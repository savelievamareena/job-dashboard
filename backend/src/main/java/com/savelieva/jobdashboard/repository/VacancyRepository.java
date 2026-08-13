package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.model.Vacancy;
import java.util.List;

/**
 * Source of the postings the dashboard shows. Only the picked ones are ever returned: a posting
 * that never reached a selected.csv does not belong on this board, and the loader records that as
 * the is_selected flag.
 */
public interface VacancyRepository {

    /** All picked postings, newest pick first. Marks and CVs are not filled in here. */
    List<Vacancy> findSelected();

    /**
     * Stores where the Apply button leads, as pasted on the board. Null or blank clears it.
     *
     * @throws StatusRepository.UnknownPostingException when no posting carries this url
     */
    void saveApplyUrl(String url, String applyUrl);
}
