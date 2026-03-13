package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class MegademoService {

    private static final String MSG_ERR_PREFIX = "ERROR: ";

    @Autowired
    private RelfileRepository relfileRepository;

    /**
     * Migrated from COBOL paragraph {@code REL-1} in program {@code MEGADEMO}.
     * <p>
     * This method simulates relative file I/O operations:
     * <ol>
     *   <li>Sets the relative key to 1.</li>
     *   <li>Constructs a {@link RelRec} record with ID=1 and data="RELATIVE DATA".</li>
     *   <li>Attempts to write (save) the record; logs an error if the write fails.</li>
     *   <li>Attempts to read (retrieve) the record back by key; logs an error if the read fails.</li>
     * </ol>
     * <p>Original COBOL: paragraph {@code REL-1} in program {@code MEGADEMO}.
     *
     * @return the {@link RelRec} that was read back from the repository, or {@code null} if the read failed.
     */
    public RelRec rel1() {

        // MOVE 1 TO WS-REL-KEY
        int wsRelKey = 1;

        // MOVE 1 TO REL-ID
        // MOVE 'RELATIVE DATA' TO REL-DATA
        RelRec relRec = new RelRec();
        relRec.setRelId(1);
        relRec.setRelData("RELATIVE DATA");
        relRec.setRelKey(wsRelKey);

        // WRITE REL-REC INVALID KEY DISPLAY MSG-ERR-PREFIX 'REL WRITE FAILED FS=' WS-FS-REL
        String wsFsRel = "00";
        try {
            relfileRepository.save(relRec);
            log.debug("REL WRITE succeeded for key={}", wsRelKey);
        } catch (Exception e) {
            wsFsRel = "99";
            log.error("{}REL WRITE FAILED FS={}", MSG_ERR_PREFIX, wsFsRel, e);
        }

        // READ RELFILE RECORD INTO REL-REC INVALID KEY DISPLAY MSG-ERR-PREFIX 'REL READ FAILED FS=' WS-FS-REL
        RelRec readRelRec = null;
        try {
            Optional<RelRec> optionalRelRec = relfileRepository.findByRelKey(wsRelKey);
            if (optionalRelRec.isPresent()) {
                readRelRec = optionalRelRec.get();
                log.debug("REL READ succeeded for key={}, record={}", wsRelKey, readRelRec);
            } else {
                wsFsRel = "23";
                log.error("{}REL READ FAILED FS={}", MSG_ERR_PREFIX, wsFsRel);
            }
        } catch (Exception e) {
            wsFsRel = "99";
            log.error("{}REL READ FAILED FS={}", MSG_ERR_PREFIX, wsFsRel, e);
        }

        return readRelRec;
    }

    // ---------------------------------------------------------------------------
    // Inner domain model — represents the COBOL REL-REC / RELFILE record layout
    // ---------------------------------------------------------------------------

    /**
     * Represents the COBOL record layout for RELFILE (REL-REC).
     * <pre>
     *   01 REL-REC.
     *      05 REL-ID   PIC 9(6).
     *      05 REL-DATA PIC X(13).
     *      05 REL-KEY  PIC 9(6).   (WS-REL-KEY used as relative key)
     * </pre>
     */
    @jakarta.persistence.Entity
    @jakarta.persistence.Table(name = "RELFILE")
    public static class RelRec {

        @jakarta.persistence.Id
        @jakarta.persistence.Column(name = "REL_KEY", nullable = false)
        private int relKey;

        @jakarta.persistence.Column(name = "REL_ID", nullable = false)
        private int relId;

        @jakarta.persistence.Column(name = "REL_DATA", length = 13)
        private String relData;

        public int getRelKey() {
            return relKey;
        }

        public void setRelKey(int relKey) {
            this.relKey = relKey;
        }

        public int getRelId() {
            return relId;
        }

        public void setRelId(int relId) {
            this.relId = relId;
        }

        public String getRelData() {
            return relData;
        }

        public void setRelData(String relData) {
            this.relData = relData;
        }

        @Override
        public String toString() {
            return "RelRec{relKey=" + relKey + ", relId=" + relId + ", relData='" + relData + "'}";
        }
    }

    // ---------------------------------------------------------------------------
    // Repository interface — represents relative-file access via Spring Data JPA
    // ---------------------------------------------------------------------------

    /**
     * Spring Data JPA repository for {@link RelRec}, replacing COBOL relative-file I/O.
     */
    @org.springframework.stereotype.Repository
    public interface RelfileRepository extends org.springframework.data.jpa.repository.JpaRepository<RelRec, Integer> {

        /**
         * Finds a record by its relative key (WS-REL-KEY).
         *
         * @param relKey the relative record key
         * @return an {@link Optional} containing the matching record, or empty if not found
         */
        Optional<RelRec> findByRelKey(int relKey);
    }
}