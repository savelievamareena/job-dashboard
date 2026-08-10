package com.savelieva.jobdashboard.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savelieva.jobdashboard.config.SearchProperties;
import com.savelieva.jobdashboard.model.JobStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * The one file this app owns: a url to {status, note} map, kept in the shape the Python dashboard
 * wrote so the two can be swapped without losing what has already been marked.
 *
 * <p>Read on every request rather than cached, because the file is small and may also be edited by
 * hand between requests. Writes are serialised and land through a temp file, so an interrupted save
 * cannot leave a half written map behind.
 */
@Repository
public class StatusRepository {

    private static final Logger log = LoggerFactory.getLogger(StatusRepository.class);
    private static final TypeReference<LinkedHashMap<String, JobStatus>> MAP_TYPE =
            new TypeReference<>() {};

    private final Path file;
    private final ObjectMapper mapper;
    private final ReentrantLock writeLock = new ReentrantLock();

    public StatusRepository(SearchProperties properties, ObjectMapper mapper) {
        this.file = properties.statusFile();
        this.mapper = mapper;
    }

    public Map<String, JobStatus> findAll() {
        if (file == null || !Files.isRegularFile(file)) {
            return Map.of();
        }
        try {
            return mapper.readValue(Files.readString(file), MAP_TYPE);
        } catch (IOException e) {
            // A corrupt status file must not take the whole dashboard down: the vacancies on disk
            // are still worth showing, and the next save rewrites the file cleanly.
            log.warn("cannot read status file {}, continuing without stored statuses", file, e);
            return Map.of();
        }
    }

    public JobStatus find(String url) {
        return findAll().getOrDefault(url, JobStatus.EMPTY);
    }

    /** Stores the status for one posting, or removes the entry once it is empty again. */
    public void save(String url, JobStatus status) {
        writeLock.lock();
        try {
            Map<String, JobStatus> all = new LinkedHashMap<>(findAll());
            if (status.isEmpty()) {
                all.remove(url);
            } else {
                all.put(url, status);
            }
            write(all);
        } finally {
            writeLock.unlock();
        }
    }

    private void write(Map<String, JobStatus> all) {
        try {
            Path parent = file.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Path tmp = Files.createTempFile(parent, "_status", ".json");
            Files.writeString(tmp, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(all));
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write status file " + file, e);
        }
    }
}
