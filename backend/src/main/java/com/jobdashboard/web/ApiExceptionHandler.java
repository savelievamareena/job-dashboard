package com.jobdashboard.web;

import com.jobdashboard.repository.StatusRepository.UnknownPostingException;
import com.jobdashboard.service.VacancyService.InvalidApplyUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Says which failure it was: "no data" and "no database" look the same on an empty table. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** The database could not be reached: the one failure the reader can fix themselves. */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<String> unavailable(DataAccessResourceFailureException e) {
        log.warn("cannot reach the database", e);
        return text(HttpStatus.SERVICE_UNAVAILABLE,
                "база недоступна: проверьте, что контейнер запущен (docker compose up -d)");
    }

    /** The database answered and refused: a fault in this app, not something a restart fixes. */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> refused(DataAccessException e) {
        log.error("the database refused the request", e);
        return text(HttpStatus.INTERNAL_SERVER_ERROR,
                "база отклонила запрос: смотрите журнал приложения");
    }

    /** A mark for a posting the database does not hold - a stale tab, not a server fault. */
    @ExceptionHandler(UnknownPostingException.class)
    public ResponseEntity<String> unknownPosting(UnknownPostingException e) {
        log.info("{}", e.getMessage());
        return text(HttpStatus.NOT_FOUND,
                "вакансия не найдена в базе: обновите страницу");
    }

    /** Not a link: her mistake to fix, shown next to the table rather than logged as a fault. */
    @ExceptionHandler(InvalidApplyUrlException.class)
    public ResponseEntity<String> invalidApplyUrl(InvalidApplyUrlException e) {
        log.info("{}", e.getMessage());
        return text(HttpStatus.BAD_REQUEST,
                "это не ссылка: адрес должен начинаться с http:// или https://");
    }

    private ResponseEntity<String> text(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN).body(body);
    }
}
