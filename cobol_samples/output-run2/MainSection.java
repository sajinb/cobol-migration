package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class MegademoService {

    private static final String MSG_INFO_PREFIX = "INFO: ";
    private static final String MSG_ERR_PREFIX  = "ERROR: ";

    // -----------------------------------------------------------------------
    // Working-storage equivalents (instance fields mirroring WS variables)
    // -----------------------------------------------------------------------
    private boolean wsDebug = false;

    private String  wsFlag        = "N";
    private boolean flagYes       = false;
    private int     wsTblCount    = 0;
    private int     wsIdxBin      = 0;
    private int     wsIdxComp     = 0;

    private String  wsDateYyyymmdd = "        ";
    private String  wsDatePrint    = "  /  /  ";
    private String  wsAlnum        = "                    ";
    private String  wsAl           = "                    ";
    private String  wsPointer      = null;

    private BigDecimal ixAmt  = BigDecimal.ZERO;
    private int        ixDate = 0;
    private String     ixKey  = "          ";
    private String     ixAk1  = "     ";

    private int    wsRelKey  = 0;
    private int    relId     = 0;
    private String relData   = "             ";
    private String wsFsIx    = "00";
    private String wsFsRel   = "00";

    private int    inseqId      = 0;
    private String inseqName    = "        ";
    private BigDecimal inseqAmt = BigDecimal.ZERO;
    private int    inseqDate    = 0;
    private String inseqComment = "             ";

    private String empFname = "";
    private String empLname = "";
    private String empId    = "";

    // Table (OCCURS 10 TIMES equivalent)
    private static final int TBL_MAX = 10;
    private String[] tblItemKey = new String[TBL_MAX];
    private String[] tblItemVal = new String[TBL_MAX];
    private int      tblIdx     = 0;
    private int      tblCount   = TBL_MAX;

    // In-memory "file" stores
    private final List<String> inseqFile  = new ArrayList<>();
    private final List<String> ixFileStore  = new ArrayList<>();
    private final List<String> relFileStore = new ArrayList<>();
    private final List<String> sortFileStore = new ArrayList<>();

    // -----------------------------------------------------------------------
    // MAIN-SECTION  — section entry point, delegates to mainEntry()
    // -----------------------------------------------------------------------

    /**
     * Entry point for the MAIN-SECTION COBOL section.
     * Delegates immediately to {@link #mainEntry()} which contains the
     * section body (MAIN-ENTRY paragraph).
     * <p>Original COBOL: paragraph {@code MAIN-SECTION} in program {@code MEGADEMO}.
     */
    @Transactional
    public void mainSection() {
        mainEntry();
    }

    // -----------------------------------------------------------------------
    // MAIN-ENTRY paragraph
    // -----------------------------------------------------------------------

    /**
     * Displays program-start banner, optionally prints debug notice, then
     * drives all major processing phases in sequence.
     * <p>Original COBOL: paragraph {@code MAIN-ENTRY} in program {@code MEGADEMO}.
     */
    public void mainEntry() {
        String currentDate = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        log.info("{}Program start: {}", MSG_INFO_PREFIX, currentDate);

        if (wsDebug) {
            log.info("{}Debug mode enabled.", MSG_INFO_PREFIX);
        }

        initFilesAndData();
        tableOps();
        stringAndEditingOps();
        arithmeticAndFunctions();
        // PERFORM IN — maps to the IN / INDEXED-FILE-OPS section
        indexedFileOps();
    }

    // -----------------------------------------------------------------------
    // INIT-FILES-AND-DATA  →  init1()
    // -----------------------------------------------------------------------

    /**
     * Initialises files and working-storage data structures.
     * <p>Original COBOL: paragraph {@code INIT-FILES-AND-DATA} / {@code INIT-1}
     * in program {@code MEGADEMO}.
     */
    public void initFilesAndData() {
        init1();
    }

    /**
     * Opens output/IO files, resets flags, and pre-populates the in-memory
     * table with 10 entries.
     * <p>Original COBOL: paragraph {@code INIT-1} in program {@code MEGADEMO}.
     */
    public void init1() {
        // OPEN OUTPUT INSEQ
        inseqFile.clear();
        log.info("{}INSEQ opened for OUTPUT", MSG_INFO_PREFIX);

        // OPEN I-O IXFILE  (simulate: if not '00' re-create)
        if (!"00".equals(wsFsIx)) {
            ixFileStore.clear();
            log.info("{}IXFILE re-created (was not open)", MSG_INFO_PREFIX);
        }
        log.info("{}IXFILE opened I-O", MSG_INFO_PREFIX);

        // OPEN I-O RELFILE
        relFileStore.clear();
        log.info("{}RELFILE opened I-O", MSG_INFO_PREFIX);

        // MOVE 'Y' TO WS-FLAG-AREA
        wsFlag = "Y";

        // SET FLAG-YES TO TRUE
        flagYes = true;

        // MOVE 10 TO WS-TBL-COUNT
        wsTblCount = 10;

        // PERFORM VARYING WS-IDX-BIN FROM 1 BY 1 UNTIL WS-IDX-BIN > 10
        for (wsIdxBin = 1; wsIdxBin <= wsTblCount; wsIdxBin++) {
            int zeroBasedIdx = wsIdxBin - 1;
            tblItemKey[zeroBasedIdx] = String.format("KEY%07d", wsIdxBin);
            tblItemVal[zeroBasedIdx] = String.format("VAL%07d", wsIdxBin);
        }
        log.info("{}Table initialised with {} entries", MSG_INFO_PREFIX, wsTblCount);
    }

    // -----------------------------------------------------------------------
    // TABLE-OPS  →  tbl1()
    // -----------------------------------------------------------------------

    /**
     * Drives table-operation demonstrations.
     * <p>Original COBOL: paragraph {@code TABLE-OPS} in program {@code MEGADEMO}.
     */
    public void tableOps() {
        tbl1();
    }

    /**
     * Iterates over the in-memory table and performs a linear search for
     * entries whose key starts with "AA".
     * <p>Original COBOL: paragraph {@code TBL-1} in program {@code MEGADEMO}.
     */
    public void tbl1() {
        // SET TBL-IDX TO 1  (1-based in COBOL → 0-based in Java)
        tblIdx = 0;

        // PERFORM UNTIL TBL-IDX > TBL-COUNT
        while (tblIdx < tblCount) {
            log.info("TBL ITEM ({}) KEY={}", tblIdx + 1, tblItemKey[tblIdx]);
            tblIdx++;
        }

        // SEARCH TBL-ITEM — linear search for key starting with "AA"
        boolean found = false;
        tblIdx = 0;
        while (tblIdx < tblCount) {
            String keyPrefix = tblItemKey[tblIdx].length() >= 2
                    ? tblItemKey[tblIdx].substring(0, 2)
                    : tblItemKey[tblIdx];
            if ("AA".equals(keyPrefix)) {
                log.info("LINEAR SEARCH: FOUND at index {} KEY={}", tblIdx + 1, tblItemKey[tblIdx]);
                found = true;
                break;
            }
            tblIdx++;
        }
        if (!found) {
            log.info("LINEAR SEARCH: NOT FOUND");
        }
    }

    // -----------------------------------------------------------------------
    // STRING-AND-EDITING-OPS  →  str1()
    // -----------------------------------------------------------------------

    /**
     * Drives string and editing demonstrations.
     * <p>Original COBOL: paragraph {@code STRING-AND-EDITING-OPS} in program {@code MEGADEMO}.
     */
    public void stringAndEditingOps() {
        str1();
    }

    /**
     * Formats a hard-coded date string and concatenates employee name fields.
     * <p>Original COBOL: paragraph {@code STR-1} in program {@code MEGADEMO}.
     */
    public void str1() {
        // MOVE '20260305' TO WS-DATE-YYYYMMDD
        wsDateYyyymmdd = "20260305";

        // Build WS-DATE-PRINT as "YYYY/MM/DD" style (positions 1:2, 4:2, 7:2)
        // COBOL reference modification is 1-based; Java substring is 0-based
        StringBuilder datePrint = new StringBuilder("  /  /  ");
        datePrint.replace(0, 2, wsDateYyyymmdd.substring(0, 2)); // YYYY
        datePrint.replace(3, 5, wsDateYyyymmdd.substring(2, 4)); // MM
        datePrint.replace(6, 8, wsDateYyyymmdd.substring(4, 6)); // DD
        wsDatePrint = datePrint.toString();
        log.info("WS-DATE-PRINT={}", wsDatePrint);

        // STRING 'Hello, ' EMP-FNAME ' ' EMP-LNAME '!' DELIMITED BY SIZE INTO WS-AL
        wsAl = "Hello, " + empFname + " " + empLname + "!";
        // Truncate / pad to field width (20 chars assumed)
        if (wsAl.length() > 20) {
            wsAl = wsAl.substring(0, 20);
        }
        log.info("WS-AL={}", wsAl);
    }

    // -----------------------------------------------------------------------
    // ARITHMETIC-AND-FUNCTIONS  →  ari1()
    // -----------------------------------------------------------------------

    /**
     * Drives arithmetic and intrinsic-function demonstrations.
     * <p>Original COBOL: paragraph {@code ARITHMETIC-AND-FUNCTIONS} in program {@code MEGADEMO}.
     */
    public void arithmeticAndFunctions() {
        ari1();
    }

    /**
     * Demonstrates COMPUTE, CURRENT-DATE, MAX, MIN, RANDOM, and LENGTH
     * intrinsic functions.
     * <p>Original COBOL: paragraph {@code ARI-1} in program {@code MEGADEMO}.
     */
    public void ari1() {
        // COMPUTE IX-AMT = (13 + 7) * 2 - 5 / 2
        // Integer arithmetic in COBOL: 5/2 = 2 (truncated), result = 40 - 2 = 38
        BigDecimal result = BigDecimal.valueOf((13 + 7) * 2)
                .subtract(BigDecimal.valueOf(5).divide(BigDecimal.valueOf(2),
                        0, RoundingMode.DOWN));
        ixAmt = result;
        log.info("COMPUTE IX-AMT={}", ixAmt);

        // DISPLAY 'CURRENT-DATE=' FUNCTION CURRENT-DATE
        String currentDate = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        log.info("CURRENT-DATE={}", currentDate);

        // DISPLAY 'MAX(3,7)=' FUNCTION MAX(3 7)
        int maxVal = Math.max(3, 7);
        log.info("MAX(3,7)={}", maxVal);

        // DISPLAY 'MIN(3,7)=' FUNCTION MIN(3 7)
        int minVal = Math.min(3, 7);
        log.info("MIN(3,7)={}", minVal);

        // DISPLAY 'RANDOM=' FUNCTION RANDOM
        double randomVal = Math.random();
        log.info("RANDOM={}", randomVal);

        // DISPLAY 'LENGTH(WS-ALNUM)=' FUNCTION LENGTH(WS-ALNUM)
        int lengthVal = wsAlnum.length();
        log.info("LENGTH(WS-ALNUM)={}", lengthVal);
    }

    // -----------------------------------------------------------------------
    // INDEXED-FILE-OPS  →  ix1()
    // -----------------------------------------------------------------------

    /**
     * Drives indexed-file operation demonstrations.
     * <p>Original COBOL: paragraph {@code INDEXED-FILE-OPS} in program {@code MEGADEMO}.
     */
    public void indexedFileOps() {
        ix1();
    }

    /**
     * Writes a record to the indexed file store and handles write-failure
     * reporting.
     * <p>Original COBOL: paragraph {@code IX-1} in program {@code MEGADEMO}.
     */
    public void ix1() {
        ixKey  = "K000000001";
        ixAk1  = "AK001";
        ixAmt  = new BigDecimal("1111.11").setScale(2, RoundingMode.HALF_UP);
        ixDate = 20260305;

        // Simulate WRITE IX-REC INVALID KEY
        boolean writeOk = performIxWrite(ixKey, ixAk1, ixAmt, ixDate);
        if (!writeOk) {
            log.error("{}IX WRITE FAILED FS={}", MSG_ERR_PREFIX, wsFsIx);
        } else {
            log.info("{}IX WRITE OK KEY={}", MSG_INFO_PREFIX, ixKey);
        }
    }

    // -----------------------------------------------------------------------
    // RELATIVE-FILE-OPS  →  rel1()
    // -----------------------------------------------------------------------

    /**
     * Drives relative-file operation demonstrations.
     * <p>Original COBOL: paragraph {@code RELATIVE-FILE-OPS} in program {@code MEGADEMO}.
     */
    public void relativeFileOps() {
        rel1();
    }

    /**
     * Writes and reads back a record in the relative file store.
     * <p>Original COBOL: paragraph {@code REL-1} in program {@code MEGADEMO}.
     */
    public void rel1() {
        wsRelKey = 1;
        relId    = 1;
        relData  = "RELATIVE DATA";

        // WRITE REL-REC INVALID KEY
        boolean writeOk = performRelWrite(wsRelKey, relId, relData);
        if (!writeOk) {
            log.error("{}REL WRITE FAILED FS={}", MSG_ERR_PREFIX, wsFsRel);
        } else {
            log.info("{}REL WRITE OK KEY={}", MSG_INFO_PREFIX, wsRelKey);
        }

        // READ RELFILE RECORD INTO REL-REC INVALID KEY
        String readRecord = performRelRead(wsRelKey);
        if (readRecord == null) {
            log.error("{}REL READ FAILED FS={}", MSG_ERR_PREFIX, wsFsRel);
        } else {
            log.info("{}REL READ OK DATA={}", MSG_INFO_PREFIX, readRecord);
        }
    }

    // -----------------------------------------------------------------------
    // SEQ-FILE-AND-SORT-OPS  →  sq1()
    // -----------------------------------------------------------------------

    /**
     * Drives sequential-file and sort operation demonstrations.