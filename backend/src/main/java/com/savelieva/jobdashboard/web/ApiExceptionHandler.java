package com.savelieva.jobdashboard.web;

import com.savelieva.jobdashboard.repository.StatusRepository.UnknownPostingException;
import com.savelieva.jobdashboard.service.VacancyService.InvalidApplyUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the failures both pages share into an answer that says which failure it was.
 *
 * <p>The board used to read the folders and owed the database nothing, so a stopped container cost
 * the statistics page alone. It reads the database now, so the same stopped container empties the
 * board too, and the page has to say so: "no data" and "no database" look identical on an empty
 * table, and only one of them is fixed by starting a container.
 *
 * <p>The application still starts without a database. That is what
 * {@code initialization-fail-timeout: -1} buys, and it is worth keeping: a dashboard that refuses
 * to boot cannot tell anyone why it refused.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * The database could not be reached at all: no container, wrong port, wrong credentials. Told
     * apart from the failures below because this is the one the reader can fix themselves, and
     * because it is by far the likeliest.
     */
    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<String> unavailable(DataAccessResourceFailureException e) {
        log.warn("cannot reach the database", e);
        return text(HttpStatus.SERVICE_UNAVAILABLE,
                "база недоступна: проверьте, что контейнер запущен (docker compose up -d)");
    }

    /**
     * The database answered and refused. A broken query or a constraint is a fault in this
     * application, not something a restart fixes, so it must not borrow the message above.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> refused(DataAccessException e) {
        log.error("the database refused the request", e);
        return text(HttpStatus.INTERNAL_SERVER_ERROR,
                "база отклонила запрос: смотрите журнал приложения");
    }

    /**
     * A mark arrived for a posting the database does not hold, which a tab left open across a
     * reload of the table can do. Not a server fault, and not worth a stack trace.
     */
    @ExceptionHandler(UnknownPostingException.class)
    public ResponseEntity<String> unknownPosting(UnknownPostingException e) {
        log.info("{}", e.getMessage());
        return text(HttpStatus.NOT_FOUND,
                "вакансия не найдена в базе: обновите страницу");
    }

    /**
     * The apply link box got something that is not a link. Her mistake to see and fix, not a
     * server fault: the board shows the message next to the table.
     */
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
