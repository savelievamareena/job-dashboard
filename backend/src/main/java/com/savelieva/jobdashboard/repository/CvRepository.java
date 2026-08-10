package com.savelieva.jobdashboard.repository;

import com.savelieva.jobdashboard.config.CvProperties;
import com.savelieva.jobdashboard.model.CvChoices;
import com.savelieva.jobdashboard.model.CvKind;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * Reads which CV was built for each company out of the queue the tailoring skill appends to:
 *
 * <pre>
 *   &lt;root&gt;/&lt;core&gt;/review-queue.csv     company,url,pdf_path,built,verdict
 * </pre>
 *
 * <p>A row is one posting: two ads from the same agency often get different answers, one tailored
 * and one sent the core, so keying by company would let the first one speak for the second.
 *
 * <p>The path in a row says which of the two answers it is. A CV tailored for a company lives in a
 * folder named after that company; anything else under the core is the core CV, queued under the
 * company it will be sent to. That naming is what the tailoring skill guarantees, and it is the
 * only marker the queue carries.
 *
 * <p>Rows written before the queue carried a URL are kept under their company, where they answer
 * for every posting of it. That is all such a row can honestly say.
 *
 * <p>Read on every request, like the postings themselves, so a CV built while the board is open
 * shows up on reload.
 */
@Repository
public class CvRepository {

    private static final Logger log = LoggerFactory.getLogger(CvRepository.class);
    private static final String QUEUE = "review-queue.csv";
    private static final CSVFormat CSV = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreSurroundingSpaces(true)
            .build();

    private final CvProperties properties;

    public CvRepository(CvProperties properties) {
        this.properties = properties;
    }

    public CvChoices findAll() {
        Path root = properties.root();
        if (root == null || !Files.isDirectory(root)) {
            log.info("CV root {} does not exist, no CV is shown for any company", root);
            return CvChoices.EMPTY;
        }
        Map<String, CvChoices.Queue> byCore = new LinkedHashMap<>();
        for (String core : properties.cores()) {
            // Every configured core is listed, queue or not: a core with nothing built yet still
            // owns its track, and dropping it would send that track looking for an answer in the
            // other core, which is exactly the mix-up the per-core split exists to prevent.
            byCore.put(core.toLowerCase(Locale.ROOT), readQueue(root.resolve(core).resolve(QUEUE)));
        }
        return new CvChoices(byCore);
    }

    private CvChoices.Queue readQueue(Path queue) {
        if (!Files.isRegularFile(queue)) {
            return new CvChoices.Queue(Map.of(), Map.of());
        }
        Map<String, CvKind> byUrl = new LinkedHashMap<>();
        Map<String, CvKind> byCompany = new LinkedHashMap<>();
        for (CSVRecord row : read(queue)) {
            String company = column(row, "company");
            if (company.isBlank()) {
                continue;
            }
            CvKind kind = kind(company, column(row, "pdf_path"));
            String url = column(row, "url");
            if (url.isBlank()) {
                byCompany.put(company.toLowerCase(Locale.ROOT), kind);
            } else {
                byUrl.put(url, kind);
            }
        }
        return new CvChoices.Queue(byUrl, byCompany);
    }

    /** A CV built for this company sits in a folder carrying its name; everything else is the core. */
    private CvKind kind(String company, String pdfPath) {
        Path parent = Path.of(pdfPath).getParent();
        boolean ownFolder = parent != null
                && parent.getFileName().toString().equalsIgnoreCase(company.trim());
        return ownFolder ? CvKind.TAILORED : CvKind.BASE;
    }

    private Iterable<CSVRecord> read(Path file) {
        try (CSVParser parser = CSVParser.parse(file, StandardCharsets.UTF_8, CSV)) {
            return parser.getRecords();
        } catch (IOException | IllegalArgumentException e) {
            // The queue is a side note on the board: a malformed one costs a column, not the rows.
            log.warn("cannot read {}", file, e);
            return List.of();
        }
    }

    private String column(CSVRecord row, String name) {
        return row.isMapped(name) && row.isSet(name) ? row.get(name).trim() : "";
    }
}
