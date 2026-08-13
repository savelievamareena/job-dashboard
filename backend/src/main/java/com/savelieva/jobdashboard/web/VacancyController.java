package com.savelieva.jobdashboard.web;

import com.savelieva.jobdashboard.model.JobStatus;
import com.savelieva.jobdashboard.model.Vacancy;
import com.savelieva.jobdashboard.service.VacancyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VacancyController {

    private final VacancyService service;

    public VacancyController(VacancyService service) {
        this.service = service;
    }

    /** Everything the board needs in one call: the rows plus the statuses that may be set on them. */
    @GetMapping("/vacancies")
    public VacanciesResponse vacancies() {
        return new VacanciesResponse(service.findAll(), VacancyService.STATUSES);
    }

    @PutMapping("/vacancies/status")
    public JobStatus updateStatus(@Valid @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(request.url(), new JobStatus(request.status(), request.note()));
    }

    /**
     * Where the Apply button leads, pasted by hand. It used to arrive only from a paid lookup,
     * which stopped answering on 2026-08-13, so the board is now the way it gets in at all.
     */
    @PutMapping("/vacancies/apply-url")
    public ApplyUrlResponse updateApplyUrl(@Valid @RequestBody ApplyUrlUpdateRequest request) {
        return new ApplyUrlResponse(service.updateApplyUrl(request.url(), request.applyUrl()));
    }

    public record VacanciesResponse(List<Vacancy> vacancies, List<String> statuses) {}

    public record StatusUpdateRequest(@NotBlank String url, String status, String note) {}

    public record ApplyUrlUpdateRequest(@NotBlank String url, String applyUrl) {}

    public record ApplyUrlResponse(String applyUrl) {}
}
