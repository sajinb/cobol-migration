package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * This class contains the migration of the {@code RELATIVE-FILE-OPS} section
 * and its subordinate paragraph {@code REL-1}, which performs relative file
 * write and read operations against a relative-access file (RELFILE).
 */
@Slf4j
@Service
public class MegademoService {

    /** Prefix used in COBOL MSG-ERR-PREFIX for error display messages. */
    private static final String MSG_ERR_PREFIX = "ERROR: ";

    /**
     * Represents the relative file record structure (REL-REC).
     * Maps COBOL fields: REL-ID (PIC 9) and REL-DATA (PIC X(13)).
     */
    public static class RelRec {
        private int relId;
        private String relData;

        public RelRec() {
            this.relId = 0;
            this.relData = "";
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
            return "RelRec{relId=" + relId + ", relData='" + relData + "'}";
        }
    }

    /**
     * Represents the in-memory relative file store, keyed by relative record number.
     * In a real migration this would be backed by a repository or file-system resource.
     */
    private final java.util.Map<Integer, RelRec> relFileStore = new java.util.HashMap<>();

    /**
     * RELATIVE-FILE-OPS — section entry point.
     * <p>
     * This is the COBOL SECTION entry point that delegates immediately to
     * {@link #rel1()} (the section body paragraph {@code REL-1}).
     * <p>
     * Original COBOL: paragraph {@code RELATIVE-FILE-OPS} in program {@code MEGADEMO}.
     */
    public void relativeFileOps() {
        log.debug("Entering RELATIVE-FILE-OPS section");
        rel1();
        log.debug("Exiting RELATIVE-FILE-OPS section");
    }

    /**
     * REL-1 — section body paragraph.
     * <p>
     * Sets the relative key (WS-REL-KEY) to 1, populates a relative file record
     * with REL-ID=1 and REL-DATA='RELATIVE DATA', writes it to the relative file,
     * and then reads it back. Any I/O failure is reported via an error log message
     * mirroring the COBOL DISPLAY of MSG-ERR-PREFIX and the file-status field.
     * <p>
     * Original COBOL: paragraph {@code REL-1} in program {@code MEGADEMO}.
     */
    public void rel1() {
        log.debug("Entering REL-1");

        // MOVE 1 TO WS-REL-KEY
        int wsRelKey = 1;

        // MOVE 1 TO REL-ID  /  MOVE 'RELATIVE DATA' TO REL-DATA
        RelRec relRec = new RelRec();
        relRec.setRelId(1);
        relRec.setRelData("RELATIVE DATA");

        // WRITE REL-REC INVALID KEY DISPLAY MSG-ERR-PREFIX 'REL WRITE FAILED FS=' WS-FS-REL
        String wsFileStatusRel = "00"; // default "success" file status
        boolean writeInvalidKey = false;

        try {
            if (relFileStore.containsKey(wsRelKey)) {
                // Relative key already exists — simulate INVALID KEY condition
                writeInvalidKey = true;
                wsFileStatusRel = "22"; // duplicate key file status
            } else {
                relFileStore.put(wsRelKey, relRec);
                wsFileStatusRel = "00";
                log.info("REL-1: Successfully wrote record with key={} record={}", wsRelKey, relRec);
            }
        } catch (Exception e) {
            writeInvalidKey = true;
            wsFileStatusRel = "99"; // undefined / unexpected error
            log.error("REL-1: Unexpected error during relative file write", e);
        }

        if (writeInvalidKey) {
            log.error("{}REL WRITE FAILED FS={}", MSG_ERR_PREFIX, wsFileStatusRel);
        }

        // READ RELFILE RECORD INTO REL-REC INVALID KEY DISPLAY MSG-ERR-PREFIX 'REL READ FAILED FS=' WS-FS-REL
        boolean readInvalidKey = false;

        try {
            RelRec readRec = relFileStore.get(wsRelKey);
            if (readRec == null) {
                // Record not found — simulate INVALID KEY condition
                readInvalidKey = true;
                wsFileStatusRel = "23"; // record not found file status
            } else {
                // MOVE read result INTO REL-REC
                relRec.setRelId(readRec.getRelId());
                relRec.setRelData(readRec.getRelData());
                wsFileStatusRel = "00";
                log.info("REL-1: Successfully read record with key={} record={}", wsRelKey, relRec);
            }
        } catch (Exception e) {
            readInvalidKey = true;
            wsFileStatusRel = "99";
            log.error("REL-1: Unexpected error during relative file read", e);
        }

        if (readInvalidKey) {
            log.error("{}REL READ FAILED FS={}", MSG_ERR_PREFIX, wsFileStatusRel);
        }

        log.debug("Exiting REL-1");
    }
}