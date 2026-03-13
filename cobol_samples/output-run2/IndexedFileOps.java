package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * This class contains the migration of the {@code INDEXED-FILE-OPS} section
 * and its body paragraph {@code IX-1}.
 */
@Slf4j
@Service
public class MegademoService {

    private static final String MSG_ERR_PREFIX = "ERROR: ";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Represents the indexed file record (IX-REC) used in INDEXED-FILE-OPS.
     * Maps to the COBOL FD / record layout for the indexed file.
     */
    public static class IxRecord {
        /** PIC X(10) — primary key */
        private String ixKey;
        /** PIC X(5)  — alternate key 1 */
        private String ixAk1;
        /** PIC 9(6)V99 COMP-3 — amount field */
        private BigDecimal ixAmt;
        /** PIC 9(8)  — date in YYYYMMDD format */
        private int ixDate;
        /** File-status code (PIC XX) — WS-FS-IX */
        private String wsFileStatus;

        public IxRecord() {
            this.ixKey = "";
            this.ixAk1 = "";
            this.ixAmt = BigDecimal.ZERO;
            this.ixDate = 0;
            this.wsFileStatus = "00";
        }

        public String getIxKey()          { return ixKey; }
        public void   setIxKey(String v)  { this.ixKey = v; }

        public String getIxAk1()          { return ixAk1; }
        public void   setIxAk1(String v)  { this.ixAk1 = v; }

        public BigDecimal getIxAmt()             { return ixAmt; }
        public void       setIxAmt(BigDecimal v) { this.ixAmt = v; }

        public int  getIxDate()       { return ixDate; }
        public void setIxDate(int v)  { this.ixDate = v; }

        public String getWsFileStatus()         { return wsFileStatus; }
        public void   setWsFileStatus(String v) { this.wsFileStatus = v; }

        @Override
        public String toString() {
            return "IxRecord{ixKey='" + ixKey + "', ixAk1='" + ixAk1
                    + "', ixAmt=" + ixAmt + ", ixDate=" + ixDate
                    + ", wsFileStatus='" + wsFileStatus + "'}";
        }
    }

    /**
     * Represents the result of an indexed-file write operation.
     * Carries the populated record and a flag indicating write success.
     */
    public static class IndexedFileOpsResult {
        private final IxRecord record;
        private final boolean  writeSucceeded;

        public IndexedFileOpsResult(IxRecord record, boolean writeSucceeded) {
            this.record         = record;
            this.writeSucceeded = writeSucceeded;
        }

        public IxRecord getRecord()          { return record; }
        public boolean  isWriteSucceeded()   { return writeSucceeded; }
    }

    // -------------------------------------------------------------------------
    // SECTION entry point: INDEXED-FILE-OPS
    // -------------------------------------------------------------------------

    /**
     * Entry point for the {@code INDEXED-FILE-OPS} COBOL section.
     * <p>
     * The section body consists solely of paragraph {@code IX-1}, which is
     * invoked here via {@link #ix1()}.
     * <p>
     * Original COBOL: section {@code INDEXED-FILE-OPS} in program {@code MEGADEMO}.
     *
     * @return {@link IndexedFileOpsResult} containing the populated IX-REC and
     *         a flag indicating whether the WRITE succeeded.
     */
    public IndexedFileOpsResult indexedFileOps() {
        log.debug("Entering INDEXED-FILE-OPS section");
        IndexedFileOpsResult result = ix1();
        log.debug("Exiting INDEXED-FILE-OPS section — writeSucceeded={}",
                result.isWriteSucceeded());
        return result;
    }

    // -------------------------------------------------------------------------
    // Paragraph: IX-1
    // -------------------------------------------------------------------------

    /**
     * Migrated from COBOL paragraph {@code IX-1} in program {@code MEGADEMO}.
     * <p>
     * Behaviour:
     * <ol>
     *   <li>Populates the indexed-file record fields:
     *       <ul>
     *         <li>{@code IX-KEY}  ← {@code 'K000000001'}</li>
     *         <li>{@code IX-AK1}  ← {@code 'AK001'}</li>
     *         <li>{@code IX-AMT}  ← {@code 1111.11}</li>
     *         <li>{@code IX-DATE} ← {@code 20260305}</li>
     *       </ul>
     *   </li>
     *   <li>Attempts a logical WRITE of the record.</li>
     *   <li>On INVALID KEY (duplicate / out-of-sequence key), logs an error
     *       message equivalent to:
     *       {@code DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX}.</li>
     * </ol>
     *
     * <p>Original COBOL: paragraph {@code IX-1} in program {@code MEGADEMO}.
     *
     * @return {@link IndexedFileOpsResult} containing the populated record and
     *         a boolean indicating whether the write succeeded.
     */
    public IndexedFileOpsResult ix1() {
        log.debug("Entering IX-1");

        // MOVE 'K000000001' TO IX-KEY
        // MOVE 'AK001'      TO IX-AK1
        // MOVE 1111.11      TO IX-AMT
        // MOVE 20260305     TO IX-DATE
        IxRecord ixRec = new IxRecord();
        ixRec.setIxKey("K000000001");
        ixRec.setIxAk1("AK001");
        ixRec.setIxAmt(new BigDecimal("1111.11"));
        ixRec.setIxDate(20260305);

        // WRITE IX-REC INVALID KEY
        //   DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX
        // END-WRITE
        //
        // In the COBOL source the file-status WS-FS-IX is set by the runtime
        // after the WRITE verb.  Here we simulate the write and treat any
        // exception as an INVALID KEY condition, setting a non-zero file status.
        boolean writeSucceeded = performWrite(ixRec);

        if (!writeSucceeded) {
            // Equivalent to: DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX
            String errorMessage = MSG_ERR_PREFIX
                    + "IX WRITE FAILED FS=" + ixRec.getWsFileStatus();
            log.error(errorMessage);
            System.out.println(errorMessage);   // preserve DISPLAY semantics
        }

        log.debug("Exiting IX-1 — writeSucceeded={}", writeSucceeded);
        return new IndexedFileOpsResult(ixRec, writeSucceeded);
    }

    // -------------------------------------------------------------------------
    // Internal helper — simulates the COBOL WRITE verb for the indexed file.
    // In a real deployment this would delegate to a repository, a file-adapter,
    // or a messaging gateway.  The file-status code is set on the record so
    // that callers can inspect WS-FS-IX after the call.
    // -------------------------------------------------------------------------

    /**
     * Simulates the COBOL {@code WRITE IX-REC} verb.
     * <p>
     * Sets {@code WS-FS-IX} on the supplied record:
     * <ul>
     *   <li>{@code "00"} — successful write</li>
     *   <li>{@code "22"} — duplicate key (INVALID KEY)</li>
     *   <li>{@code "30"} — permanent I/O error (INVALID KEY)</li>
     * </ul>
     *
     * @param ixRec the record to write; {@code wsFileStatus} is updated in-place.
     * @return {@code true} if the write succeeded ({@code WS-FS-IX == "00"}),
     *         {@code false} on INVALID KEY.
     */
    private boolean performWrite(IxRecord ixRec) {
        try {
            // Validate that mandatory key fields are present before attempting write.
            if (ixRec.getIxKey() == null || ixRec.getIxKey().isBlank()) {
                ixRec.setWsFileStatus("30");
                log.warn("IX WRITE aborted — IX-KEY is blank");
                return false;
            }

            // Validate date field is a plausible YYYYMMDD value.
            String dateStr = String.valueOf(ixRec.getIxDate());
            if (dateStr.length() != 8) {
                ixRec.setWsFileStatus("30");
                log.warn("IX WRITE aborted — IX-DATE '{}' is not a valid YYYYMMDD value",
                        dateStr);
                return false;
            }
            LocalDate.parse(dateStr, DATE_FORMATTER); // throws if invalid

            // Validate amount is non-negative (business rule inferred from context).
            if (ixRec.getIxAmt() == null
                    || ixRec.getIxAmt().compareTo(BigDecimal.ZERO) < 0) {
                ixRec.setWsFileStatus("30");
                log.warn("IX WRITE aborted — IX-AMT is null or negative");
                return false;
            }

            // All validations passed — record the successful write.
            ixRec.setWsFileStatus("00");
            log.info("IX WRITE succeeded for key='{}' ak1='{}' amt={} date={}",
                    ixRec.getIxKey(), ixRec.getIxAk1(),
                    ixRec.getIxAmt(), ixRec.getIxDate());
            return true;

        } catch (Exception ex) {
            // Any unexpected exception maps to a permanent I/O error (FS=30).
            ixRec.setWsFileStatus("30");
            log.error("IX WRITE raised an unexpected exception — FS set to 30", ex);
            return false;
        }
    }
}