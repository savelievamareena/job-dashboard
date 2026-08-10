package com.savelieva.jobdashboard.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.savelieva.jobdashboard.config.SearchProperties;
import com.savelieva.jobdashboard.model.Vacancy;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads the postings straight out of the folders the parsing skills write, so the dashboard shows
 * the same data the skills produced without an import step in between.
 *
 * <p>Layout per search root:
 * <pre>
 *   &lt;root&gt;/&lt;date&gt;/_descriptions/&lt;jobId&gt;.json   cached posting, two key shapes over time
 *   &lt;root&gt;/&lt;date&gt;/_descriptions/&lt;jobId&gt;.txt    cached description text, older days only
 *   &lt;root&gt;/&lt;date&gt;/_titles.json                  jobId to title, older days only
 *   &lt;root&gt;/&lt;date&gt;/&lt;track&gt;/jobs.csv             everything parsed that day
 *   &lt;root&gt;/&lt;date&gt;/&lt;track&gt;/selected.csv         the ones worth applying to: what this app shows
 * </pre>
 *
 * <p>selected.csv drives the scan, not jobs.csv. It already carries company, title and gap, so a
 * selected posting still shows up if the bigger file was rewritten or trimmed underneath it.
 */
@Repository
public class FileVacancyRepository implements VacancyRepository {

    private static final Logger log = LoggerFactory.getLogger(FileVacancyRepository.class);
    private static final Pattern DATE_FOLDER = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    private final SearchProperties properties;
    private final ObjectMapper mapper;

    public FileVacancyRepository(SearchProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public List<Vacancy> findSelected() {
        List<Vacancy> vacancies = new ArrayList<>();
        for (Path root : properties.roots()) {
            if (!Files.isDirectory(root)) {
                log.info("search root {} does not exist, skipping", root);
                continue;
            }
            String source = root.getFileName().toString().replace("Search", "");
            for (Path day : dayFolders(root)) {
                readDay(day, source, vacancies);
            }
        }
        vacancies.sort(Comparator.comparing(Vacancy::date).reversed()
                .thenComparing(Vacancy::company, String.CASE_INSENSITIVE_ORDER));
        return vacancies;
    }

    private void readDay(Path day, String source, List<Vacancy> target) {
        Path cache = day.resolve("_descriptions");
        Map<String, String> titles = readTitles(day.resolve("_titles.json"));
        for (String track : properties.tracks()) {
            Path selectedFile = day.resolve(track).resolve("selected.csv");
            if (!Files.isRegularFile(selectedFile)) {
                continue;   // nothing picked for that track that day
            }
            Map<String, String> stacks = readStacks(day.resolve(track).resolve("jobs.csv"));
            for (CSVRecord row : read(selectedFile)) {
                String url = column(row, "url");
                if (url.isBlank()) {
                    continue;
                }
                target.add(toVacancy(row, url, day, source, track, cache, titles, stacks));
            }
        }
    }

    private Vacancy toVacancy(CSVRecord row, String url, Path day, String source, String track,
                              Path cache, Map<String, String> titles, Map<String, String> stacks) {
        String jobId = jobId(url);
        JsonNode record = readRecord(cache.resolve(jobId + ".json"));
        String title = firstNonBlank(
                text(record, "job_title"), titles.get(jobId), column(row, "title"));
        return new Vacancy(
                day.getFileName().toString(),
                source,
                track,
                column(row, "company"),
                title,
                url,
                stacks.getOrDefault(url, ""),
                easyApply(record),
                text(record, "experience_level"),
                text(record, "job_type"),
                firstNonBlank(text(record, "location"), text(record, "job_location")),
                firstNonBlank(text(record, "applicants"), text(record, "applies")),
                column(row, "gap"),
                Files.isRegularFile(cache.resolve(jobId + ".txt")),
                null,
                "",
                "");
    }

    private List<Path> dayFolders(Path root) {
        try (Stream<Path> children = Files.list(root)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(p -> DATE_FOLDER.matcher(p.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
        } catch (IOException e) {
            log.warn("cannot list search root {}", root, e);
            return List.of();
        }
    }

    /** jobs.csv only adds the stack column, and older days do not even have that. */
    private Map<String, String> readStacks(Path jobsFile) {
        if (!Files.isRegularFile(jobsFile)) {
            return Map.of();
        }
        Map<String, String> stacks = new LinkedHashMap<>();
        for (CSVRecord row : read(jobsFile)) {
            String url = column(row, "url");
            if (!url.isBlank()) {
                stacks.put(url, column(row, "stack"));
            }
        }
        return stacks;
    }

    private Map<String, String> readTitles(Path titlesFile) {
        if (!Files.isRegularFile(titlesFile)) {
            return Map.of();
        }
        JsonNode node = readRecord(titlesFile);
        Map<String, String> titles = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> titles.put(e.getKey(), e.getValue().asText("")));
        return titles;
    }

    private List<CSVRecord> read(Path file) {
        try (CSVParser parser = CSVParser.parse(file, StandardCharsets.UTF_8, CSV)) {
            return parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            // One malformed file must not hide every other day on the board.
            log.warn("cannot read {}", file, e);
            return List.of();
        }
    }

    private JsonNode readRecord(Path file) {
        if (!Files.isRegularFile(file)) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(file.toFile());
        } catch (IOException e) {
            log.warn("cannot read cached record {}", file, e);
            return mapper.createObjectNode();
        }
    }

    /**
     * Null means the cached record predates the flag, which reads differently from a known "no":
     * the UI shows "?" rather than claiming the posting needs an external site.
     */
    private Boolean easyApply(JsonNode record) {
        JsonNode value = record.path("easy_apply");
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        if (value.isTextual() && !value.textValue().isBlank()) {
            return Boolean.parseBoolean(value.textValue());
        }
        return null;
    }

    private String jobId(String url) {
        String trimmed = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        int cut = trimmed.lastIndexOf('/');
        return cut < 0 ? trimmed : trimmed.substring(cut + 1);
    }

    private String column(CSVRecord row, String name) {
        return row.isMapped(name) && row.isSet(name) ? row.get(name).trim() : "";
    }

    /** Python wrote its None into some cached fields as the literal text; treat that as missing. */
    private String text(JsonNode record, String key) {
        JsonNode value = record.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return "";
        }
        String asText = value.asText("").trim();
        return "None".equals(asText) ? "" : asText;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
