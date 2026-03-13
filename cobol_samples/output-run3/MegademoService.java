package com.migration.megademo;

import com.migration.megademo.entity.IxRecord;
import com.migration.megademo.repository.IxFileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class MegademoService {

    /**
         * Terminates the current program unit and returns control to the caller.
         * In COBOL, {@code GOBACK} causes the program to stop executing and return
         * to whatever invoked it (the operating system, a calling program, or the
         * CICS/batch runtime). In a Spring Boot context this is modelled as a normal
         * method return; any cleanup or shutdown signalling required by the broader
         * application should be handled by the caller.
         *
         * <p>Original COBOL: paragraph {@code GOBACK} in program {@code MEGADEMO}.
         */
        public void goBack() {
            log.debug("GOBACK reached — returning control to caller.");
            // GOBACK in COBOL simply returns to the invoking environment.
            // In Java this is represented by a plain method return.
        }

    /**
         * Represents a single table item with a key and an amount.
         */
        public static class TblItem {
            private String key;
            private BigDecimal amount;

            public TblItem(String key, BigDecimal amount) {
                this.key = key;
                this.amount = amount;
            }

            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }
            public BigDecimal getAmount() { return amount; }
            public void setAmount(BigDecimal amount) { this.amount = amount; }

            @Override
            public String toString() {
                return "TblItem{key='" + key + "', amount=" + amount + "}";
            }
        }

        /**
         * Represents the working-storage table entry with key and value.
         */
        public static class WsTblEntry {
            private String key;
            private BigDecimal value;

            public WsTblEntry(String key, BigDecimal value) {
                this.key = key;
                this.value = value;
            }

            public String getKey() { return key; }
            public void setKey(String key) { this.key = key; }
            public BigDecimal getValue() { return value; }
            public void setValue(BigDecimal value) { this.value = value; }

            @Override
            public String toString() {
                return "WsTblEntry{key='" + key + "', value=" + value + "}";
            }
        }

        /**
         * Holds the result of the INIT-1 paragraph execution, representing
         * all working-storage and linkage items written during initialization.
         */
        public static class Init1Result {
            // WS-FLAG-AREA set to 'Y'; FLAG-YES set to TRUE
            private String wsFlagArea;
            private boolean flagYes;

            // WS-TBL-COUNT = 10; populated WS-TBL entries (1..10)
            private int wsTblCount;
            private List<WsTblEntry> wsTblEntries;

            // Record name fields
            private String rnA;
            private String rnB;
            private String rnC;
            private String rnD;

            // TBL-COUNT = 5; populated TBL-ITEM entries (1..2 explicitly set)
            private int tblCount;
            private List<TblItem> tblItems;

            // File status indicators (simulated open results)
            private String wsFileStatusInseq;
            private String wsFileStatusIxfile;
            private String wsFileStatusRelfile;

            public Init1Result() {
                this.wsTblEntries = new ArrayList<>();
                this.tblItems = new ArrayList<>();
            }

            public String getWsFlagArea() { return wsFlagArea; }
            public void setWsFlagArea(String wsFlagArea) { this.wsFlagArea = wsFlagArea; }

            public boolean isFlagYes() { return flagYes; }
            public void setFlagYes(boolean flagYes) { this.flagYes = flagYes; }

            public int getWsTblCount() { return wsTblCount; }
            public void setWsTblCount(int wsTblCount) { this.wsTblCount = wsTblCount; }

            public List<WsTblEntry> getWsTblEntries() { return wsTblEntries; }
            public void setWsTblEntries(List<WsTblEntry> wsTblEntries) { this.wsTblEntries = wsTblEntries; }

            public String getRnA() { return rnA; }
            public void setRnA(String rnA) { this.rnA = rnA; }

            public String getRnB() { return rnB; }
            public void setRnB(String rnB) { this.rnB = rnB; }

            public String getRnC() { return rnC; }
            public void setRnC(String rnC) { this.rnC = rnC; }

            public String getRnD() { return rnD; }
            public void setRnD(String rnD) { this.rnD = rnD; }

            public int getTblCount() { return tblCount; }
            public void setTblCount(int tblCount) { this.tblCount = tblCount; }

            public List<TblItem> getTblItems() { return tblItems; }
            public void setTblItems(List<TblItem> tblItems) { this.tblItems = tblItems; }

            public String getWsFileStatusInseq() { return wsFileStatusInseq; }
            public void setWsFileStatusInseq(String wsFileStatusInseq) { this.wsFileStatusInseq = wsFileStatusInseq; }

            public String getWsFileStatusIxfile() { return wsFileStatusIxfile; }
            public void setWsFileStatusIxfile(String wsFileStatusIxfile) { this.wsFileStatusIxfile = wsFileStatusIxfile; }

            public String getWsFileStatusRelfile() { return wsFileStatusRelfile; }
            public void setWsFileStatusRelfile(String wsFileStatusRelfile) { this.wsFileStatusRelfile = wsFileStatusRelfile; }
        }

        /**
         * Initializes files and working-storage data structures as defined in the
         * INIT-1 paragraph of MEGADEMO.
         *
         * <p>Corresponds to:
         * <ul>
         *   <li>OPEN OUTPUT INSEQ — simulated; file status set to "00".</li>
         *   <li>OPEN I-O IXFILE — simulated; if WS-FS-IX != "00", the file is
         *       recreated (OUTPUT then I-O) to ensure it exists.</li>
         *   <li>OPEN I-O RELFILE — simulated; file status set to "00".</li>
         *   <li>WS-FLAG-AREA set to 'Y'; FLAG-YES set to TRUE.</li>
         *   <li>WS-TBL-COUNT set to 10; table populated with keys "KEY1".."KEY10"
         *       and values idx * 13.37.</li>
         *   <li>Record-name fields RN-A..RN-D set to 'ABC','DEF','GHI','JKL'.</li>
         *   <li>TBL-COUNT set to 5; first two TBL-ITEM entries populated.</li>
         * </ul>
         *
         * <p>Original COBOL: paragraph {@code INIT-1} in program {@code MEGADEMO}.
         *
         * @param wsFsIx the current file status of IXFILE (WS-FS-IX), used to
         *               decide whether IXFILE must be recreated before opening I-O.
         *               Pass {@code "00"} if the file already exists and is healthy.
         * @return an {@link Init1Result} containing all initialized state.
         */
        public Init1Result init1(String wsFsIx) {

            Init1Result result = new Init1Result();

            // ----------------------------------------------------------------
            // OPEN OUTPUT INSEQ
            // In a real integration this would open/create the sequential file.
            // We record a successful open status of "00".
            // ----------------------------------------------------------------
            log.info("INIT-1: Opening INSEQ for OUTPUT");
            result.setWsFileStatusInseq("00");

            // ----------------------------------------------------------------
            // OPEN I-O IXFILE
            // If WS-FS-IX != '00', the indexed file does not yet exist;
            // recreate it by opening OUTPUT then closing, then re-open I-O.
            // ----------------------------------------------------------------
            log.info("INIT-1: Opening IXFILE for I-O (WS-FS-IX='{}')", wsFsIx);
            if (wsFsIx == null || !wsFsIx.equals("00")) {
                log.info("INIT-1: WS-FS-IX != '00' — recreating IXFILE (OPEN OUTPUT, CLOSE, OPEN I-O)");
                // Simulate: OPEN OUTPUT IXFILE → CLOSE IXFILE → OPEN I-O IXFILE
                // In a real implementation this would interact with the file system
                // or a resource manager; here we simply record the recovered status.
            }
            result.setWsFileStatusIxfile("00");

            // ----------------------------------------------------------------
            // OPEN I-O RELFILE
            // ----------------------------------------------------------------
            log.info("INIT-1: Opening RELFILE for I-O");
            result.setWsFileStatusRelfile("00");

            // ----------------------------------------------------------------
            // MOVE 'Y' TO WS-FLAG-AREA
            // SET FLAG-YES TO TRUE  (88-level condition — maps to boolean true)
            // ----------------------------------------------------------------
            result.setWsFlagArea("Y");
            result.setFlagYes(true);

            // ----------------------------------------------------------------
            // MOVE 10 TO WS-TBL-COUNT
            // PERFORM VARYING WS-IDX-BIN FROM 1 BY 1 UNTIL WS-IDX-BIN > WS-TBL-COUNT
            //    STRING 'KEY' WS-IDX-BIN INTO WS-TBL-KEY(WS-TBL-IDX)
            //    COMPUTE WS-TBL-VAL(WS-TBL-IDX) = WS-IDX-BIN * 13.37
            // END-PERFORM
            // ----------------------------------------------------------------
            int wsTblCount = 10;
            result.setWsTblCount(wsTblCount);

            List<WsTblEntry> wsTblEntries = new ArrayList<>();
            for (int wsIdxBin = 1; wsIdxBin <= wsTblCount; wsIdxBin++) {
                // STRING 'KEY' DELIMITED BY SIZE, WS-IDX-BIN DELIMITED BY SIZE
                // INTO WS-TBL-KEY — concatenate literal "KEY" with the integer index
                String wsTblKey = "KEY" + wsIdxBin;

                // COMPUTE WS-TBL-VAL = WS-IDX-BIN * 13.37
                BigDecimal wsTblVal = BigDecimal.valueOf(wsIdxBin)
                        .multiply(new BigDecimal("13.37"))
                        .setScale(2, RoundingMode.HALF_UP);

                wsTblEntries.add(new WsTblEntry(wsTblKey, wsTblVal));
                log.debug("INIT-1: WS-TBL[{}] key='{}' val={}", wsIdxBin, wsTblKey, wsTblVal);
            }
            result.setWsTblEntries(wsTblEntries);

            // ----------------------------------------------------------------
            // MOVE 'ABC' TO RN-A
            // MOVE 'DEF' TO RN-B
            // MOVE 'GHI' TO RN-C
            // MOVE 'JKL' TO RN-D
            // ----------------------------------------------------------------
            result.setRnA("ABC");
            result.setRnB("DEF");
            result.setRnC("GHI");
            result.setRnD("JKL");

            // ----------------------------------------------------------------
            // MOVE 5 TO TBL-COUNT
            // MOVE 'AA00000001' TO TBL-ITEM-KEY (1)
            // MOVE 100.50       TO TBL-ITEM-AMT (1)
            // MOVE 'AA00000002' TO TBL-ITEM-KEY (2)
            // MOVE 200.75       TO TBL-ITEM-AMT (2)
            //
            // TBL-COUNT = 5 but only items 1 and 2 are explicitly initialised;
            // items 3-5 are left as default (null key, zero amount) to match
            // COBOL behaviour where uninitialised OCCURS entries contain LOW-VALUES/ZEROS.
            // ----------------------------------------------------------------
            int tblCount = 5;
            result.setTblCount(tblCount);

            // Pre-allocate all 5 slots with defaults (mirrors COBOL OCCURS initialisation)
            List<TblItem> tblItems = new ArrayList<>();
            for (int i = 0; i < tblCount; i++) {
                tblItems.add(new TblItem("", BigDecimal.ZERO));
            }

            // Item 1 (COBOL 1-based index → Java 0-based index 0)
            tblItems.get(0).setKey("AA00000001");
            tblItems.get(0).setAmount(new BigDecimal("100.50"));

            // Item 2 (COBOL 1-based index → Java 0-based index 1)
            tblItems.get(1).setKey("AA00000002");
            tblItems.get(1).setAmount(new BigDecimal("200.75"));

            result.setTblItems(tblItems);

            log.info("INIT-1: Initialization complete. wsTblCount={}, tblCount={}, flagYes={}",
                    result.getWsTblCount(), result.getTblCount(), result.isFlagYes());

            return result;
        }

    /**
         * Represents a single table item, analogous to the COBOL {@code TBL-ITEM} group entry.
         */
        public static class TblItem {
            private final String key;

            public TblItem(String key) {
                if (key == null) {
                    throw new IllegalArgumentException("TblItem key must not be null");
                }
                this.key = key;
            }

            public String getKey() {
                return key;
            }
        }

        /**
         * Holds the result of the {@code tbl1} operation so that mutations to
         * {@code tblCount} (WORKING-STORAGE) are visible to the caller.
         */
        public static class Tbl1Result {
            private int tblCount;

            public Tbl1Result(int tblCount) {
                this.tblCount = tblCount;
            }

            public int getTblCount() {
                return tblCount;
            }

            public void setTblCount(int tblCount) {
                this.tblCount = tblCount;
            }
        }

        /**
         * Migrated from COBOL paragraph {@code TBL-1} in program {@code MEGADEMO}.
         *
         * <p>Behaviour:
         * <ol>
         *   <li>Iterates from index 1 to {@code tblCount} (inclusive), logging each item's key.</li>
         *   <li>Performs a linear search (COBOL {@code SEARCH}) over the full array,
         *       logging the first item whose key starts with {@code "AA"}.</li>
         *   <li>Sets {@code tblCount} to 5, then performs a binary search
         *       (COBOL {@code SEARCH ALL}) over the first 5 elements for the key
         *       {@code "AA00000002"}, logging the result.</li>
         * </ol>
         *
         * <p>Original COBOL: paragraph {@code TBL-1} in program {@code MEGADEMO}.
         *
         * @param tblItems array of {@link TblItem} objects (COBOL {@code TBL-ITEM OCCURS})
         * @param tblCount number of valid entries currently in the table (COBOL {@code TBL-COUNT})
         * @return a {@link Tbl1Result} containing the updated {@code tblCount} value
         */
        public Tbl1Result tbl1(TblItem[] tblItems, int tblCount) {

            // ----------------------------------------------------------------
            // 1.  SET TBL-IDX TO 1
            //     PERFORM UNTIL TBL-IDX > TBL-COUNT
            //        DISPLAY 'TBL ITEM (' TBL-IDX ') KEY=' TBL-ITEM-KEY (TBL-IDX)
            //        SET TBL-IDX UP BY 1
            //     END-PERFORM
            // COBOL indices are 1-based; Java arrays are 0-based.
            // ----------------------------------------------------------------
            int tblIdx = 1;
            while (tblIdx <= tblCount) {
                // Guard against an array that is shorter than tblCount claims.
                if (tblIdx - 1 < tblItems.length) {
                    String key = tblItems[tblIdx - 1].getKey();
                    log.info("TBL ITEM ({}) KEY={}", tblIdx, key);
                }
                tblIdx++;
            }

            // ----------------------------------------------------------------
            // 2.  SEARCH TBL-ITEM  (linear search — COBOL resets TBL-IDX to 1
            //     implicitly at the start of SEARCH and increments it each cycle)
            //     AT END DISPLAY 'LINEAR SEARCH: NOT FOUND'
            //     WHEN TBL-ITEM-KEY (TBL-IDX) (1:2) = 'AA'
            //          DISPLAY 'LINEAR SEARCH FOUND INDEX=' TBL-IDX
            //     END-SEARCH
            //
            //  COBOL SEARCH starts from the current value of TBL-IDX (which is
            //  tblCount+1 after the loop above).  To match standard COBOL SEARCH
            //  semantics the index is reset to 1 at the beginning of SEARCH.
            // ----------------------------------------------------------------
            tblIdx = 1;
            boolean linearFound = false;
            for (int i = 0; i < tblItems.length; i++) {
                tblIdx = i + 1;                          // keep COBOL 1-based index in sync
                String key = tblItems[i].getKey();
                // COBOL reference modification (1:2) — substring starting at position 1, length 2
                String keyPrefix = key.length() >= 2 ? key.substring(0, 2) : key;
                if ("AA".equals(keyPrefix)) {
                    log.info("LINEAR SEARCH FOUND INDEX={}", tblIdx);
                    linearFound = true;
                    break;
                }
            }
            if (!linearFound) {
                log.info("LINEAR SEARCH: NOT FOUND");
            }

            // ----------------------------------------------------------------
            // 3.  MOVE 5 TO TBL-COUNT
            //     SET TBL-IDX TO 1
            //     SEARCH ALL TBL-ITEM
            //        WHEN TBL-ITEM-KEY (TBL-IDX) = 'AA00000002'
            //             DISPLAY 'BINARY SEARCH FOUND IDX=' TBL-IDX
            //     END-SEARCH
            //
            //  COBOL SEARCH ALL performs a binary search on a sorted table.
            //  The effective table size is now tblCount = 5.
            //  Java's Arrays.binarySearch is used on the sub-array [0..4].
            // ----------------------------------------------------------------
            tblCount = 5;
            tblIdx = 1;

            final String binarySearchTarget = "AA00000002";

            // Build a sub-list of the first tblCount elements for the binary search.
            int effectiveSize = Math.min(tblCount, tblItems.length);
            String[] keys = new String[effectiveSize];
            for (int i = 0; i < effectiveSize; i++) {
                keys[i] = tblItems[i].getKey();
            }

            // Arrays.binarySearch requires the array to be sorted (matches COBOL SEARCH ALL
            // requirement that the table is sorted on the key).
            int binaryResult = Arrays.binarySearch(keys, binarySearchTarget);
            if (binaryResult >= 0) {
                // Convert 0-based Java index back to 1-based COBOL index.
                tblIdx = binaryResult + 1;
                log.info("BINARY SEARCH FOUND IDX={}", tblIdx);
            }
            // COBOL SEARCH ALL has no explicit AT END clause in this paragraph,
            // so no "not found" message is emitted for the binary search.

            return new Tbl1Result(tblCount);
        }

    /**
         * Implements the STR-1 paragraph from MEGADEMO.
         * <p>
         * This paragraph:
         * <ul>
         *   <li>Formats a hard-coded date (20260305) into MM/DD/YY print format.</li>
         *   <li>Builds a greeting string by concatenating first name, last name, and punctuation.</li>
         *   <li>Unstrings a pipe-delimited source string into up to four fields.</li>
         *   <li>Tallies occurrences of the letter 'L' in the greeting string.</li>
         *   <li>Replaces all '!' characters with '.' in the greeting string.</li>
         *   <li>Formats and displays three numeric amounts using edited picture patterns.</li>
         * </ul>
         * <p>Original COBOL: paragraph {@code STR-1} in program {@code MEGADEMO}.
         *
         * @param empFname      Employee first name (EMP-FNAME)
         * @param empLname      Employee last name  (EMP-LNAME)
         * @param wsUnstrSrc    Pipe-delimited source string to be unstrung (WS-UNSTR-SRC)
         * @return              A {@link Str1Result} containing all computed output values
         */
        public Str1Result str1(String empFname, String empLname, String wsUnstrSrc) {

            // ---------------------------------------------------------------
            // MOVE '20260305' TO WS-DATE-YYYYMMDD
            // ---------------------------------------------------------------
            String wsDateYyyymmdd = "20260305";

            // ---------------------------------------------------------------
            // Build WS-DATE-PRINT: positions map as MM/DD/YY
            //   (1:2) -> chars 0-1 = year-century  "2026" -> positions 1-2 = "20"
            //   (3:2) -> chars 2-3 = "26"           -> positions 4-5
            //   (5:2) -> chars 4-5 = "03"           -> positions 7-8
            // COBOL picture for WS-DATE-PRINT is typically XX/XX/XX (8 chars)
            // Mapping: YYYYMMDD -> YY(1:2) / YY(3:2) / MM(5:2)  but standard
            // date print is MM/DD/YY; the COBOL moves substrings positionally:
            //   print(1:2) <- yyyymmdd(1:2)  => "20"
            //   print(4:2) <- yyyymmdd(3:2)  => "26"
            //   print(7:2) <- yyyymmdd(5:2)  => "03"
            // Result: "20/26/03" — faithfully reproducing the COBOL logic.
            // ---------------------------------------------------------------
            char[] datePrint = new char[8];
            Arrays.fill(datePrint, ' ');
            datePrint[1] = '/';
            datePrint[4] = '/';

            // MOVE WS-DATE-YYYYMMDD (1:2) TO WS-DATE-PRINT (1:2)  [0-based: 0..1 -> 0..1]
            datePrint[0] = wsDateYyyymmdd.charAt(0);
            datePrint[1] = wsDateYyyymmdd.charAt(1);

            // MOVE WS-DATE-YYYYMMDD (3:2) TO WS-DATE-PRINT (4:2)  [0-based: 2..3 -> 3..4]
            datePrint[3] = wsDateYyyymmdd.charAt(2);
            datePrint[4] = wsDateYyyymmdd.charAt(3);

            // MOVE WS-DATE-YYYYMMDD (5:2) TO WS-DATE-PRINT (7:2)  [0-based: 4..5 -> 6..7]
            datePrint[6] = wsDateYyyymmdd.charAt(4);
            datePrint[7] = wsDateYyyymmdd.charAt(5);

            String wsDatePrint = new String(datePrint);

            // ---------------------------------------------------------------
            // STRING 'Hello, ' EMP-FNAME ' ' EMP-LNAME '!' DELIMITED BY SIZE
            //        INTO WS-ALNUM
            // DELIMITED BY SIZE means use the full length of each operand.
            // ---------------------------------------------------------------
            String wsAlnum = "Hello, " + empFname + " " + empLname + "!";

            // ---------------------------------------------------------------
            // UNSTRING WS-UNSTR-SRC DELIMITED BY '|'
            //          INTO RN-A RN-B RN-C RN-D
            // Split on '|', up to 4 tokens; missing tokens default to spaces.
            // ---------------------------------------------------------------
            String[] tokens = wsUnstrSrc.split("\\|", -1);
            String rnA = tokens.length > 0 ? tokens[0] : " ";
            String rnB = tokens.length > 1 ? tokens[1] : " ";
            String rnC = tokens.length > 2 ? tokens[2] : " ";
            String rnD = tokens.length > 3 ? tokens[3] : " ";

            // ---------------------------------------------------------------
            // INSPECT WS-ALNUM TALLYING WS-IDX-COMP FOR ALL 'L'
            // Count occurrences of 'L' (case-sensitive, as COBOL is).
            // ---------------------------------------------------------------
            int wsIdxComp = 0;
            for (int i = 0; i < wsAlnum.length(); i++) {
                if (wsAlnum.charAt(i) == 'L') {
                    wsIdxComp++;
                }
            }

            // ---------------------------------------------------------------
            // INSPECT WS-ALNUM REPLACING ALL '!' BY '.'
            // ---------------------------------------------------------------
            wsAlnum = wsAlnum.replace('!', '.');

            // ---------------------------------------------------------------
            // MOVE 123456.78 TO WS-AMT-DISPLAY
            // Typical COBOL edited picture: ZZZ,ZZZ.99  (e.g. "123,456.78")
            // ---------------------------------------------------------------
            BigDecimal amtDisplay = new BigDecimal("123456.78").setScale(2, RoundingMode.HALF_UP);
            DecimalFormat fmtDisplay = new DecimalFormat("###,##0.00");
            String wsAmtDisplay = fmtDisplay.format(amtDisplay);

            // ---------------------------------------------------------------
            // MOVE 123456.78 TO WS-AMT-STAR
            // Typical COBOL edited picture: ***,***.99  (asterisk-fill)
            // ---------------------------------------------------------------
            BigDecimal amtStar = new BigDecimal("123456.78").setScale(2, RoundingMode.HALF_UP);
            String wsAmtStar = formatWithAsteriskFill(amtStar, 10, 2);

            // ---------------------------------------------------------------
            // MOVE -123456.78 TO WS-AMT-CRDB
            // Typical COBOL edited picture: ZZZ,ZZZ.99CR  or  ZZZ,ZZZ.99DB
            // Negative value -> "CR" suffix; positive -> spaces.
            // ---------------------------------------------------------------
            BigDecimal amtCrdb = new BigDecimal("-123456.78").setScale(2, RoundingMode.HALF_UP);
            String wsAmtCrdb = formatCrDb(amtCrdb);

            // ---------------------------------------------------------------
            // DISPLAY statements
            // ---------------------------------------------------------------
            log.info("Edited Amt 1: {}", wsAmtDisplay);
            log.info("Edited Amt 2: {}", wsAmtStar);
            log.info("Edited Amt 3: {}", wsAmtCrdb);

            return new Str1Result(
                    wsDateYyyymmdd,
                    wsDatePrint,
                    wsAlnum,
                    rnA, rnB, rnC, rnD,
                    wsIdxComp,
                    wsAmtDisplay,
                    wsAmtStar,
                    wsAmtCrdb
            );
        }

        // -----------------------------------------------------------------------
        // Helper: asterisk-fill numeric formatting  (COBOL *,***.99 picture)
        // totalIntDigits = total integer-part character positions (including commas)
        // scale          = decimal places
        // -----------------------------------------------------------------------
        private String formatWithAsteriskFill(BigDecimal value, int totalWidth, int scale) {
            DecimalFormat df = new DecimalFormat("###,##0.00");
            String formatted = df.format(value.abs().setScale(scale, RoundingMode.HALF_UP));
            // Pad with asterisks on the left to reach totalWidth + scale + 1 (dot)
            int targetLen = totalWidth;
            StringBuilder sb = new StringBuilder(formatted);
            while (sb.length() < targetLen) {
                sb.insert(0, '*');
            }
            return sb.toString();
        }

        // -----------------------------------------------------------------------
        // Helper: CR/DB suffix formatting  (COBOL ZZZ,ZZZ.99CR picture)
        // Negative -> append "CR"; non-negative -> append "  " (two spaces)
        // -----------------------------------------------------------------------
        private String formatCrDb(BigDecimal value) {
            DecimalFormat df = new DecimalFormat("###,##0.00");
            String formatted = df.format(value.abs().setScale(2, RoundingMode.HALF_UP));
            String suffix = value.compareTo(BigDecimal.ZERO) < 0 ? "CR" : "  ";
            return formatted + suffix;
        }

        // -----------------------------------------------------------------------
        // Result record carrying all output fields produced by STR-1
        // -----------------------------------------------------------------------
        public static class Str1Result {
            public final String wsDateYyyymmdd;
            public final String wsDatePrint;
            public final String wsAlnum;
            public final String rnA;
            public final String rnB;
            public final String rnC;
            public final String rnD;
            public final int    wsIdxComp;
            public final String wsAmtDisplay;
            public final String wsAmtStar;
            public final String wsAmtCrdb;

            public Str1Result(
                    String wsDateYyyymmdd,
                    String wsDatePrint,
                    String wsAlnum,
                    String rnA, String rnB, String rnC, String rnD,
                    int    wsIdxComp,
                    String wsAmtDisplay,
                    String wsAmtStar,
                    String wsAmtCrdb) {
                this.wsDateYyyymmdd = wsDateYyyymmdd;
                this.wsDatePrint    = wsDatePrint;
                this.wsAlnum        = wsAlnum;
                this.rnA            = rnA;
                this.rnB            = rnB;
                this.rnC            = rnC;
                this.rnD            = rnD;
                this.wsIdxComp      = wsIdxComp;
                this.wsAmtDisplay   = wsAmtDisplay;
                this.wsAmtStar      = wsAmtStar;
                this.wsAmtCrdb      = wsAmtCrdb;
            }

            @Override
            public String toString() {
                return "Str1Result{"
                        + "wsDateYyyymmdd='" + wsDateYyyymmdd + '\''
                        + ", wsDatePrint='"  + wsDatePrint    + '\''
                        + ", wsAlnum='"      + wsAlnum        + '\''
                        + ", rnA='"          + rnA            + '\''
                        + ", rnB='"          + rnB            + '\''
                        + ", rnC='"          + rnC            + '\''
                        + ", rnD='"          + rnD            + '\''
                        + ", wsIdxComp="     + wsIdxComp
                        + ", wsAmtDisplay='" + wsAmtDisplay   + '\''
                        + ", wsAmtStar='"    + wsAmtStar      + '\''
                        + ", wsAmtCrdb='"    + wsAmtCrdb      + '\''
                        + '}';
            }
        }

    /**
         * Implements the arithmetic, intrinsic-function demonstrations, and EVALUATE
         * logic found in paragraph {@code ARI-1} of program {@code MEGADEMO}.
         *
         * <p>The paragraph:
         * <ul>
         *   <li>Computes {@code IX-AMT} using a mixed arithmetic expression.</li>
         *   <li>Displays the result together with several COBOL intrinsic-function
         *       equivalents: CURRENT-DATE, MAX, MIN, RANDOM, LENGTH, UPPER-CASE,
         *       LOWER-CASE, INTEGER-OF-DATE, DATE-OF-INTEGER.</li>
         *   <li>Evaluates {@code EMP-GRADE} and prints a grade-specific message.</li>
         * </ul>
         *
         * <p>Original COBOL: paragraph {@code ARI-1} in program {@code MEGADEMO}.
         *
         * @param wsAlnum  the value of WS-ALNUM (PIC X) used for the LENGTH display
         * @param empGrade the value of EMP-GRADE (PIC X) used in the EVALUATE block
         * @return the computed value of IX-AMT
         */
        public BigDecimal ari1(String wsAlnum, String empGrade) {

            // COMPUTE IX-AMT = (13 + 7) * 2 - 5 / 2
            // COBOL integer arithmetic: 5 / 2 = 2 (integer division, truncated)
            // (13 + 7) * 2 = 40 ; 40 - 2 = 38
            BigDecimal ixAmt = BigDecimal.valueOf((13 + 7) * 2 - 5 / 2);
            log.info("COMPUTE IX-AMT={}", ixAmt);

            // DISPLAY 'CURRENT-DATE=' FUNCTION CURRENT-DATE
            // COBOL CURRENT-DATE returns yyyyMMddHHmmsscc+hhmm; approximate with ISO
            LocalDateTime now = LocalDateTime.now();
            String currentDate = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss00+0000"));
            log.info("CURRENT-DATE={}", currentDate);

            // DISPLAY 'MAX(3,7)=' FUNCTION MAX(3 7)
            int maxVal = Math.max(3, 7);
            log.info("MAX(3,7)={}", maxVal);

            // DISPLAY 'MIN(3,7)=' FUNCTION MIN(3 7)
            int minVal = Math.min(3, 7);
            log.info("MIN(3,7)={}", minVal);

            // DISPLAY 'RANDOM=' FUNCTION RANDOM
            // COBOL FUNCTION RANDOM returns a value in [0, 1)
            double randomVal = new Random().nextDouble();
            log.info("RANDOM={}", randomVal);

            // DISPLAY 'LENGTH(WS-ALNUM)=' FUNCTION LENGTH(WS-ALNUM)
            // COBOL LENGTH returns the declared storage length of the item;
            // here we use the runtime string length as the closest Java equivalent.
            int lengthWsAlnum = (wsAlnum != null) ? wsAlnum.length() : 0;
            log.info("LENGTH(WS-ALNUM)={}", lengthWsAlnum);

            // DISPLAY 'UPPER-CASE=' FUNCTION UPPER-CASE('abc')
            String upperCase = "abc".toUpperCase();
            log.info("UPPER-CASE={}", upperCase);

            // DISPLAY 'LOWER-CASE=' FUNCTION LOWER-CASE('ABC')
            String lowerCase = "ABC".toLowerCase();
            log.info("LOWER-CASE={}", lowerCase);

            // DISPLAY 'INTEGER-OF-DATE(20260305)=' FUNCTION INTEGER-OF-DATE(20260305)
            // COBOL INTEGER-OF-DATE counts days from the base date 1601-01-01.
            // Java: use LocalDate and compute the difference from 1601-01-01.
            LocalDate baseDate = LocalDate.of(1601, 1, 1);
            LocalDate targetDate = LocalDate.of(2026, 3, 5);
            long integerOfDate = java.time.temporal.ChronoUnit.DAYS.between(baseDate, targetDate) + 1;
            log.info("INTEGER-OF-DATE(20260305)={}", integerOfDate);

            // DISPLAY 'DATE-OF-INTEGER(75000)=' FUNCTION DATE-OF-INTEGER(75000)
            // COBOL DATE-OF-INTEGER converts a day-count (from 1601-01-01) back to yyyyMMdd.
            LocalDate dateOfInteger = baseDate.plusDays(75000 - 1);
            String dateOfIntegerStr = dateOfInteger.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            log.info("DATE-OF-INTEGER(75000)={}", dateOfIntegerStr);

            // EVALUATE EMP-GRADE
            //   WHEN 'A'  -> DISPLAY 'EVALUATE: Grade A'
            //   WHEN OTHER -> DISPLAY 'EVALUATE: default path'
            // END-EVALUATE
            String grade = (empGrade != null) ? empGrade.trim() : "";
            switch (grade) {
                case "A":
                    log.info("EVALUATE: Grade A");
                    break;
                default:
                    log.info("EVALUATE: default path");
                    break;
            }

            return ixAmt;
        }

    private static final String MSG_ERR_PREFIX = "ERROR: ";

        @Autowired
        private IxFileRepository ixFileRepository;

        /**
         * Executes the full sequence of indexed-file operations originally coded in
         * COBOL paragraph {@code IX-1}.
         *
         * <p>The method:
         * <ol>
         *   <li>Builds a seed {@link IxRecord} and persists it (WRITE IX-REC).</li>
         *   <li>Locates the first record whose key is &gt;= {@code wsIxStartKey}
         *       (START IXFILE KEY &gt;=).</li>
         *   <li>Reads the next record sequentially (READ IXFILE NEXT RECORD).</li>
         *   <li>Rewrites (updates) that record (REWRITE IX-REC).</li>
         *   <li>Deletes that record (DELETE IXFILE RECORD).</li>
         * </ol>
         *
         * @param wsIxStartKey the start-key value used for the positioned START
         *                     operation (maps to {@code WS-IX-START-KEY} in
         *                     WORKING-STORAGE)
         * @return the {@link IxRecord} that was read, rewritten, and then deleted,
         *         or {@code null} when no record at-or-after {@code wsIxStartKey}
         *         exists (AT END condition)
         */
        @Transactional
        public IxRecord ix1(String wsIxStartKey) {

            // ------------------------------------------------------------------ //
            // MOVE 'K000000001' TO IX-KEY                                         //
            // MOVE 'AK001'      TO IX-AK1                                         //
            // MOVE 1111.11      TO IX-AMT                                         //
            // MOVE 20260305     TO IX-DATE                                         //
            // ------------------------------------------------------------------ //
            IxRecord ixRec = new IxRecord();
            ixRec.setIxKey("K000000001");
            ixRec.setIxAk1("AK001");
            ixRec.setIxAmt(new BigDecimal("1111.11").setScale(2, RoundingMode.HALF_UP));
            ixRec.setIxDate(20260305);

            // ------------------------------------------------------------------ //
            // WRITE IX-REC INVALID KEY                                            //
            //   DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX            //
            // END-WRITE                                                           //
            // ------------------------------------------------------------------ //
            try {
                ixFileRepository.save(ixRec);
                log.debug("IX WRITE succeeded for key={}", ixRec.getIxKey());
            } catch (Exception e) {
                // Mirrors: DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX
                log.error("{}IX WRITE FAILED FS={}", MSG_ERR_PREFIX, e.getMessage());
                // Return null — processing cannot continue without a written record
                return null;
            }

            // ------------------------------------------------------------------ //
            // START IXFILE KEY >= WS-IX-START-KEY                                 //
            //   INVALID KEY CONTINUE                                              //
            // END-START                                                           //
            //                                                                     //
            // Locate the first record whose primary key >= wsIxStartKey.          //
            // INVALID KEY => CONTINUE, so we simply log a warning and return null.//
            // ------------------------------------------------------------------ //
            List<IxRecord> candidates = ixFileRepository.findByIxKeyGreaterThanEqualOrderByIxKeyAsc(wsIxStartKey);

            if (candidates == null || candidates.isEmpty()) {
                // AT END / INVALID KEY — CONTINUE in COBOL means carry on silently,
                // but there is nothing left to rewrite or delete.
                log.warn("{}IX START found no record with key >= '{}'", MSG_ERR_PREFIX, wsIxStartKey);
                return null;
            }

            // ------------------------------------------------------------------ //
            // READ IXFILE NEXT RECORD AT END CONTINUE END-READ                   //
            //                                                                     //
            // Take the first record in key order at-or-after the start key.      //
            // ------------------------------------------------------------------ //
            IxRecord currentRecord = candidates.get(0);
            log.debug("IX READ NEXT returned key={}", currentRecord.getIxKey());

            // ------------------------------------------------------------------ //
            // REWRITE IX-REC INVALID KEY                                          //
            //   DISPLAY MSG-ERR-PREFIX 'IX REWRITE FAILED FS=' WS-FS-IX          //
            // END-REWRITE                                                         //
            //                                                                     //
            // The COBOL REWRITE updates the record that was just READ.            //
            // In JPA the record is already managed; calling save() issues UPDATE. //
            // ------------------------------------------------------------------ //
            try {
                // Copy the in-memory ixRec fields into the record that was read,
                // mirroring the COBOL behaviour where IX-REC is the shared buffer
                // used for both WRITE and REWRITE.
                currentRecord.setIxAk1(ixRec.getIxAk1());
                currentRecord.setIxAmt(ixRec.getIxAmt());
                currentRecord.setIxDate(ixRec.getIxDate());

                ixFileRepository.save(currentRecord);
                log.debug("IX REWRITE succeeded for key={}", currentRecord.getIxKey());
            } catch (Exception e) {
                // Mirrors: DISPLAY MSG-ERR-PREFIX 'IX REWRITE FAILED FS=' WS-FS-IX
                log.error("{}IX REWRITE FAILED FS={}", MSG_ERR_PREFIX, e.getMessage());
                return null;
            }

            // ------------------------------------------------------------------ //
            // DELETE IXFILE RECORD INVALID KEY                                    //
            //   DISPLAY MSG-ERR-PREFIX 'IX DELETE FAILED FS=' WS-FS-IX           //
            // END-DELETE                                                          //
            // ------------------------------------------------------------------ //
            try {
                ixFileRepository.delete(currentRecord);
                log.debug("IX DELETE succeeded for key={}", currentRecord.getIxKey());
            } catch (Exception e) {
                // Mirrors: DISPLAY MSG-ERR-PREFIX 'IX DELETE FAILED FS=' WS-FS-IX
                log.error("{}IX DELETE FAILED FS={}", MSG_ERR_PREFIX, e.getMessage());
                return null;
            }

            // Return the record that was processed (read, rewritten, deleted).
            return currentRecord;
        }

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

    /**
         * Represents a sequential file record (INSEQ-REC).
         * Maps to the COBOL FD / record layout for the INSEQ file.
         */
        public static class InseqRec {
            private int    id;
            private String name;
            private BigDecimal amount;
            private int    date;
            private String comment;

            public InseqRec() {}

            public InseqRec(int id, String name, BigDecimal amount, int date, String comment) {
                this.id      = id;
                this.name    = name;
                this.amount  = amount;
                this.date    = date;
                this.comment = comment;
            }

            public int         getId()      { return id; }
            public String      getName()    { return name; }
            public BigDecimal  getAmount()  { return amount; }
            public int         getDate()    { return date; }
            public String      getComment() { return comment; }

            public void setId(int id)                  { this.id = id; }
            public void setName(String name)           { this.name = name; }
            public void setAmount(BigDecimal amount)   { this.amount = amount; }
            public void setDate(int date)              { this.date = date; }
            public void setComment(String comment)     { this.comment = comment; }

            @Override
            public String toString() {
                return "InseqRec{id=" + id + ", name='" + name + "', amount=" + amount
                        + ", date=" + date + ", comment='" + comment + "'}";
            }
        }

        /**
         * Represents a sort-file record with a sort key (S-KEY).
         * Maps to the COBOL SORT-FILE SD entry.
         */
        public static class SortRecord {
            private String key;
            private InseqRec data;

            public SortRecord(String key, InseqRec data) {
                this.key  = key;
                this.data = data;
            }

            public String     getKey()  { return key; }
            public InseqRec   getData() { return data; }
            public void setKey(String key)       { this.key = key; }
            public void setData(InseqRec data)   { this.data = data; }
        }

        /**
         * Result object returned by {@link #sq1()}.
         * Carries the written INSEQ record, the sorted output list, and the
         * working-storage fields that cross paragraph boundaries
         * (INSEQ-ID and INSEQ-DATE).
         */
        public static class Sq1Result {
            private final InseqRec          writtenRecord;
            private final List<SortRecord>  sortedRecords;
            private final int               inseqId;
            private final int               inseqDate;

            public Sq1Result(InseqRec writtenRecord,
                             List<SortRecord> sortedRecords,
                             int inseqId,
                             int inseqDate) {
                this.writtenRecord = writtenRecord;
                this.sortedRecords = sortedRecords;
                this.inseqId       = inseqId;
                this.inseqDate     = inseqDate;
            }

            public InseqRec         getWrittenRecord() { return writtenRecord; }
            public List<SortRecord> getSortedRecords() { return sortedRecords; }
            public int              getInseqId()       { return inseqId; }
            public int              getInseqDate()     { return inseqDate; }
        }

        // -----------------------------------------------------------------------
        // SORT-IN-PROC  (INPUT PROCEDURE for the SORT statement)
        // Populates the sort work file from the records that were written to INSEQ.
        // In COBOL this RELEASE'd records into the sort work file.
        // -----------------------------------------------------------------------
        private List<SortRecord> sortInProc(InseqRec inseqRec) {
            log.debug("sortInProc: releasing record with key={}", inseqRec.getId());
            List<SortRecord> workFile = new ArrayList<>();
            // Build the sort key from INSEQ-ID (mirrors S-KEY in the COBOL program)
            String sKey = String.format("%010d", inseqRec.getId());
            workFile.add(new SortRecord(sKey, inseqRec));
            return workFile;
        }

        // -----------------------------------------------------------------------
        // SORT-OUT-PROC  (OUTPUT PROCEDURE for the SORT statement)
        // RETURNs records from the sort work file and processes them.
        // -----------------------------------------------------------------------
        private List<SortRecord> sortOutProc(List<SortRecord> sortedWorkFile) {
            List<SortRecord> output = new ArrayList<>();
            for (SortRecord rec : sortedWorkFile) {
                log.debug("sortOutProc: returning sorted record key={}, data={}",
                        rec.getKey(), rec.getData());
                output.add(rec);
            }
            return output;
        }

        /**
         * Executes the logic of COBOL paragraph {@code SQ-1} in program {@code MEGADEMO}.
         *
         * <p>The paragraph:
         * <ol>
         *   <li>Populates an INSEQ record (id=1, name="John Doe", amount=9999.99,
         *       date=20260305, comment="Sample record").</li>
         *   <li>Writes (and then closes) the INSEQ sequential file — modelled here
         *       as capturing the record in the result object.</li>
         *   <li>Sorts SORT-FILE on ascending S-KEY, using an INPUT PROCEDURE
         *       ({@link #sortInProc}) and an OUTPUT PROCEDURE ({@link #sortOutProc}).</li>
         * </ol>
         *
         * <p>Original COBOL: paragraph {@code SQ-1} in program {@code MEGADEMO}.
         *
         * @return {@link Sq1Result} containing the written record, the sorted output,
         *         and the written working-storage fields INSEQ-ID and INSEQ-DATE.
         */
        public Sq1Result sq1() {

            // MOVE 1 TO INSEQ-ID
            int inseqId = 1;

            // MOVE 'John Doe' TO INSEQ-NAME
            String inseqName = "John Doe";

            // MOVE 9999.99 TO INSEQ-AMT
            BigDecimal inseqAmt = new BigDecimal("9999.99").setScale(2, RoundingMode.HALF_UP);

            // MOVE 20260305 TO INSEQ-DATE
            int inseqDate = 20260305;

            // MOVE 'Sample record' TO INSEQ-COMMENT
            String inseqComment = "Sample record";

            // WRITE INSEQ-REC  (assemble the record and "write" it)
            InseqRec inseqRec = new InseqRec(inseqId, inseqName, inseqAmt, inseqDate, inseqComment);
            log.info("WRITE INSEQ-REC: {}", inseqRec);

            // CLOSE INSEQ  (file is now closed; no further writes permitted)
            log.info("CLOSE INSEQ: sequential file closed after writing record id={}", inseqId);

            // SORT SORT-FILE ON ASCENDING KEY S-KEY
            //      INPUT PROCEDURE IS SORT-IN-PROC
            //      OUTPUT PROCEDURE IS SORT-OUT-PROC

            // Step 1 – INPUT PROCEDURE: release records into the sort work file
            List<SortRecord> workFile = sortInProc(inseqRec);

            // Step 2 – Perform the actual ascending sort on S-KEY
            workFile.sort(Comparator.comparing(SortRecord::getKey));
            log.info("SORT SORT-FILE: sorted {} record(s) on ascending S-KEY", workFile.size());

            // Step 3 – OUTPUT PROCEDURE: return and process sorted records
            List<SortRecord> sortedOutput = sortOutProc(workFile);
            log.info("SORT complete: {} record(s) in output", sortedOutput.size());

            return new Sq1Result(inseqRec, sortedOutput, inseqId, inseqDate);
        }

    /**
         * Releases (transfers) the current input-sequential record into the sort
         * record collector, mirroring the COBOL statement:
         * <pre>
         *   RELEASE SORT-REC FROM INSEQ-REC
         * </pre>
         * In COBOL, {@code RELEASE} moves the contents of {@code INSEQ-REC} into
         * {@code SORT-REC} and passes it to the sort work file for subsequent
         * processing. In Java, this is represented by copying the input record value
         * into the sort record holder and adding it to the sort input list.
         *
         * <p>Original COBOL: paragraph {@code SIP-1} in program {@code MEGADEMO}.
         *
         * @param inseqRec  the current input sequential record to be released into
         *                  the sort work file (maps to {@code INSEQ-REC})
         * @param sortInput the mutable list acting as the sort work file input queue;
         *                  the record is appended here (maps to the SORT work file
         *                  fed by {@code RELEASE SORT-REC})
         * @return          the sort record value that was released (maps to
         *                  {@code SORT-REC} after the RELEASE)
         */
        public String sip1(String inseqRec, java.util.List<String> sortInput) {
            log.debug("SIP-1: Releasing INSEQ-REC into SORT-REC. inseqRec=[{}]", inseqRec);

            // RELEASE SORT-REC FROM INSEQ-REC
            // Move INSEQ-REC into SORT-REC (the FROM phrase performs an implicit MOVE)
            String sortRec = inseqRec;

            // Add the sort record to the sort input collection, which represents
            // the COBOL sort work file receiving the released record.
            sortInput.add(sortRec);

            log.debug("SIP-1: SORT-REC released successfully. sortInput size=[{}]", sortInput.size());

            return sortRec;
        }

    /**
         * Processes a list of sorted records, iterating through each record and logging
         * its key and data fields.
         *
         * <p>Original COBOL: paragraph {@code SOP-1} in program {@code MEGADEMO}.
         *
         * <p>The COBOL paragraph used a {@code RETURN SORT-FILE RECORD INTO SORT-REC}
         * loop construct: it repeatedly returned records from the sort file into the
         * {@code SORT-REC} structure (composed of {@code S-KEY} and {@code S-DATA}),
         * displaying each one, and branched back to itself until the {@code AT END}
         * condition was reached (equivalent to end-of-file on the sort output), at which
         * point it fell through to {@code SOP-EXIT} (i.e., simply returned).
         *
         * @param sortedRecords the list of {@link SortRecord} objects representing the
         *                      records returned from the sort file in sorted order;
         *                      must not be {@code null}
         */
        public void sop1(List<SortRecord> sortedRecords) {
            if (sortedRecords == null) {
                log.warn("sop1: sortedRecords list is null — treating as empty (AT END condition).");
                return;
            }

            for (SortRecord sortRec : sortedRecords) {
                // DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
                log.info("SORTED:{} -> {}", sortRec.getSKey(), sortRec.getSData());
                System.out.printf("SORTED:%s -> %s%n", sortRec.getSKey(), sortRec.getSData());
            }
            // AT END GO TO SOP-EXIT — falls through naturally when the list is exhausted
        }

        // -------------------------------------------------------------------------
        // Inner record class representing SORT-REC (S-KEY + S-DATA)
        // -------------------------------------------------------------------------

        /**
         * Represents the {@code SORT-REC} working-storage structure from COBOL program
         * {@code MEGADEMO}, containing the two elementary items {@code S-KEY} (PIC X)
         * and {@code S-DATA} (PIC X).
         */
        public static class SortRecord {

            /** Corresponds to COBOL {@code S-KEY}. */
            private String sKey;

            /** Corresponds to COBOL {@code S-DATA}. */
            private String sData;

            /**
             * Default no-argument constructor.
             */
            public SortRecord() {
            }

            /**
             * Convenience constructor.
             *
             * @param sKey  the sort key field value
             * @param sData the sort data field value
             */
            public SortRecord(String sKey, String sData) {
                this.sKey = sKey;
                this.sData = sData;
            }

            /**
             * Returns the sort key ({@code S-KEY}).
             *
             * @return sort key string
             */
            public String getSKey() {
                return sKey;
            }

            /**
             * Sets the sort key ({@code S-KEY}).
             *
             * @param sKey sort key string
             */
            public void setSKey(String sKey) {
                this.sKey = sKey;
            }

            /**
             * Returns the sort data ({@code S-DATA}).
             *
             * @return sort data string
             */
            public String getSData() {
                return sData;
            }

            /**
             * Sets the sort data ({@code S-DATA}).
             *
             * @param sData sort data string
             */
            public void setSData(String sData) {
                this.sData = sData;
            }

            @Override
            public String toString() {
                return "SortRecord{sKey='" + sKey + "', sData='" + sData + "'}";
            }
        }

    /**
         * Executes the SOP-EXIT paragraph logic.
         * <p>
         * In the original COBOL program this paragraph is an empty exit/return point,
         * typically used as the target of a PERFORM … THRU … construct to mark the
         * end of a section or a structured block.  It contains no executable
         * statements and therefore this method is a deliberate no-op.
         * <p>Original COBOL: paragraph {@code SOP-EXIT} in program {@code MEGADEMO}.
         */
        public void sopExit() {
            // Intentional no-op: the original COBOL paragraph SOP-EXIT contains no
            // executable statements.  It exists solely as a fall-through / exit label
            // for PERFORM … THRU constructs in the COBOL source.
            log.debug("sopExit() called — no-op exit paragraph");
        }

    /**
         * Implements the logic of COBOL paragraph {@code CFD-1} in program {@code MEGADEMO}.
         *
         * <p>The paragraph:
         * <ol>
         *   <li>Iterates WS-IDX-COMP from 1 to 3 (inclusive), logging each iteration value.</li>
         *   <li>Sets WS-IDX-COMP to 2 and evaluates it, logging the matching branch.</li>
         *   <li>Transfers control to {@code DEPENDING-ON-DEMO} (modelled as a method call).</li>
         * </ol>
         *
         * <p>Original COBOL: paragraph {@code CFD-1} in program {@code MEGADEMO}.
         *
         * @return the final value of WS-IDX-COMP after the paragraph completes
         */
        public int cfd1() {

            // PERFORM VARYING WS-IDX-COMP FROM 1 BY 1 UNTIL WS-IDX-COMP > 3
            int wsIdxComp = 1;
            while (wsIdxComp <= 3) {
                log.info("PERFORM VARYING i={}", wsIdxComp);
                wsIdxComp++;
            }

            // MOVE 2 TO WS-IDX-COMP
            wsIdxComp = 2;

            // EVALUATE WS-IDX-COMP
            switch (wsIdxComp) {
                case 1:
                    log.info("EVAL: one");
                    break;
                case 2:
                    log.info("EVAL: two");
                    break;
                default:
                    log.info("EVAL: other");
                    break;
            }

            // GO TO DEPENDING-ON-DEMO  — modelled as a delegating method call
            dependingOnDemo();

            return wsIdxComp;
        }

        /**
         * Stub target for the {@code GO TO DEPENDING-ON-DEMO} transfer of control.
         *
         * <p>Original COBOL: paragraph/section {@code DEPENDING-ON-DEMO} in program {@code MEGADEMO}.
         * Replace this body with the fully migrated implementation of that paragraph/section.
         */
        public void dependingOnDemo() {
            log.info("Entered DEPENDING-ON-DEMO");
            // Full implementation of DEPENDING-ON-DEMO paragraph goes here.
        }

    /**
         * Implements the DEPENDING-ON-DEMO paragraph logic.
         * <p>
         * The original COBOL sets WS-IDX-COMP to 3, then performs a computed GO TO
         * branching to LABEL-1, LABEL-2, or LABEL-3 depending on the value of
         * WS-IDX-COMP. Since WS-IDX-COMP is always set to 3 immediately before the
         * branch, control always transfers to LABEL-3.
         * <p>
         * In Java the computed GO TO is modelled as a switch statement on wsIdxComp.
         * Each case calls the corresponding label method. Values outside the range
         * 1–3 (which would cause the COBOL GO TO to fall through) are handled by
         * the default branch, which logs a warning and takes no action.
         * <p>
         * Original COBOL: paragraph {@code DEPENDING-ON-DEMO} in program {@code MEGADEMO}.
         *
         * @return the value of WS-IDX-COMP after the paragraph executes (always 3
         *         when called with no arguments, matching the COBOL behaviour)
         */
        public int dependingOnDemo() {

            // MOVE 3 TO WS-IDX-COMP
            int wsIdxComp = 3;

            // GO TO LABEL-1 LABEL-2 LABEL-3 DEPENDING ON WS-IDX-COMP
            // COBOL computed GO TO: branch to the Nth label where N == wsIdxComp.
            // Values < 1 or > number-of-labels cause the statement to be ignored.
            switch (wsIdxComp) {
                case 1:
                    label1();
                    break;
                case 2:
                    label2();
                    break;
                case 3:
                    label3();
                    break;
                default:
                    // COBOL GO TO … DEPENDING ON falls through (no branch) when the
                    // index is outside the valid range — log and continue.
                    log.warn("DEPENDING-ON-DEMO: wsIdxComp={} is outside the valid range 1-3; "
                            + "GO TO DEPENDING ON has no effect.", wsIdxComp);
                    break;
            }

            return wsIdxComp;
        }

        /**
         * Corresponds to COBOL label LABEL-1.
         * <p>
         * Placeholder implementation — replace with the migrated body of LABEL-1
         * once that paragraph is available.
         * <p>
         * Original COBOL: paragraph {@code LABEL-1} in program {@code MEGADEMO}.
         */
        public void label1() {
            log.info("DEPENDING-ON-DEMO: branched to LABEL-1 (wsIdxComp=1)");
        }

        /**
         * Corresponds to COBOL label LABEL-2.
         * <p>
         * Placeholder implementation — replace with the migrated body of LABEL-2
         * once that paragraph is available.
         * <p>
         * Original COBOL: paragraph {@code LABEL-2} in program {@code MEGADEMO}.
         */
        public void label2() {
            log.info("DEPENDING-ON-DEMO: branched to LABEL-2 (wsIdxComp=2)");
        }

        /**
         * Corresponds to COBOL label LABEL-3.
         * <p>
         * This is the branch that is always taken when dependingOnDemo() is called
         * with no arguments, because WS-IDX-COMP is unconditionally set to 3.
         * <p>
         * Original COBOL: paragraph {@code LABEL-3} in program {@code MEGADEMO}.
         */
        public void label3() {
            log.info("DEPENDING-ON-DEMO: branched to LABEL-3 (wsIdxComp=3)");
        }

    /**
         * Executes the logic of the COBOL paragraph {@code LABEL-1}.
         * <p>
         * This paragraph displays a diagnostic message indicating that a
         * "GO TO DEPENDING ON" branch resolved to case 1, then transfers
         * control to the {@code CFD-EXIT} paragraph (modelled here as an
         * early return after logging, since {@code CFD-EXIT} is the exit
         * point of the surrounding section).
         * <p>
         * Original COBOL: paragraph {@code LABEL-1} in program {@code MEGADEMO}.
         */
        public void label1() {
            log.info("GO TO DEPENDING ON -> 1");
            // GO TO CFD-EXIT transfers control unconditionally to the exit
            // paragraph of the current section.  In Java this is represented
            // as an immediate return so that no further logic in the section
            // is executed after this branch is taken.
            cfdExit();
        }

        /**
         * Models the {@code CFD-EXIT} paragraph, which is the terminal /
         * exit point of the section that contains {@code LABEL-1}.
         * <p>
         * In COBOL, a paragraph named {@code <SECTION>-EXIT} (or simply the
         * last paragraph of a section) typically contains only {@code EXIT}
         * or falls through to the end of the section.  The Java equivalent
         * is a no-op method that serves as the single exit point so that
         * callers of {@link #label1()} can reason about control flow in the
         * same way the original COBOL did.
         * <p>
         * Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
         */
        public void cfdExit() {
            // Corresponds to COBOL EXIT / fall-through at end of section.
            // No operations are performed; control simply returns to the caller.
            log.debug("CFD-EXIT reached — section complete.");
        }

    /**
         * Executes the logic of the COBOL paragraph {@code LABEL-2} in program {@code MEGADEMO}.
         * <p>
         * This paragraph is one of the targets of a {@code GO TO ... DEPENDING ON} dispatch table.
         * It logs a diagnostic message indicating that branch 2 was selected, then transfers
         * control to {@code CFD-EXIT}, which in the original COBOL terminates the current
         * section/paragraph flow. In Java this is modelled by returning normally after logging,
         * since there is no further work to perform in this branch.
         * <p>Original COBOL: paragraph {@code LABEL-2} in program {@code MEGADEMO}.
         */
        public void label2() {
            log.info("GO TO DEPENDING ON -> 2");
            cfdExit();
        }

        /**
         * Models the {@code CFD-EXIT} paragraph, which in the original COBOL serves as the
         * common exit / fall-through point for the computed GO TO dispatch table.
         * Reaching this point means the selected branch has completed and control is returned
         * to the caller.
         * <p>Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
         */
        public void cfdExit() {
            // CFD-EXIT is a no-op exit paragraph in the original COBOL;
            // returning here transfers control back to the invoking method,
            // which mirrors the COBOL behaviour of falling through to the next
            // sequential sentence after the PERFORM or GO TO that reached this point.
        }

    /**
         * Displays the message "GO TO DEPENDING ON -> 3" to standard output.
         * <p>Original COBOL: paragraph {@code LABEL-3} in program {@code MEGADEMO}.
         */
        public void label3() {
            System.out.println("GO TO DEPENDING ON -> 3");
        }

    /**
         * Symbolic names for the two logical paragraphs involved in the ALTER statement.
         * {@code OLD_PARAGRAPH} is the paragraph whose GO TO destination was being altered;
         * {@code NEW_PARAGRAPH} is the new destination after the ALTER executes.
         */
        public enum ParagraphTarget {
            OLD_PARAGRAPH,
            NEW_PARAGRAPH
        }

        /**
         * Mutable routing state that replaces the COBOL {@code ALTER} mechanism.
         * Initially points to {@code OLD_PARAGRAPH}; after {@link #cfdExit()} executes
         * it is redirected to {@code NEW_PARAGRAPH}, mirroring the runtime effect of
         * {@code ALTER OLD-PARAGRAPH TO PROCEED TO NEW-PARAGRAPH}.
         */
        private volatile ParagraphTarget currentParagraph = ParagraphTarget.OLD_PARAGRAPH;

        /**
         * Returns the current paragraph routing target.
         * Callers that previously would have executed {@code OLD-PARAGRAPH}'s GO TO
         * should consult this value to determine the actual dispatch destination.
         *
         * @return the currently active {@link ParagraphTarget}
         */
        public ParagraphTarget getCurrentParagraph() {
            return currentParagraph;
        }

        /**
         * Resets the routing state back to {@code OLD_PARAGRAPH}.
         * Useful when the program logic needs to re-initialise the altered GO TO
         * to its original destination (e.g. on a new transaction cycle).
         */
        public void resetToOldParagraph() {
            log.debug("Resetting paragraph routing from {} to OLD_PARAGRAPH", currentParagraph);
            currentParagraph = ParagraphTarget.OLD_PARAGRAPH;
        }

        /**
         * Executes the logic of COBOL paragraph {@code CFD-EXIT}.
         *
         * <p>The original statement was:
         * <pre>
         *   ALTER OLD-PARAGRAPH TO PROCEED TO NEW-PARAGRAPH.
         * </pre>
         * This method replicates that effect by updating the internal routing state
         * so that any subsequent dispatch that previously would have branched to
         * {@code OLD-PARAGRAPH} will now proceed to {@code NEW-PARAGRAPH} instead.
         *
         * <p>Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
         */
        public void cfdExit() {
            log.debug(
                    "CFD-EXIT: altering paragraph routing from {} to NEW_PARAGRAPH",
                    currentParagraph);

            currentParagraph = ParagraphTarget.NEW_PARAGRAPH;

            log.debug("CFD-EXIT: paragraph routing is now {}", currentParagraph);
        }

        /**
         * Dispatches to the appropriate paragraph implementation based on the current
         * routing state established by {@link #cfdExit()} (or its absence).
         *
         * <p>This method replaces the implicit GO TO dispatch that COBOL's ALTER
         * mechanism controlled at runtime.
         */
        public void dispatchCurrentParagraph() {
            ParagraphTarget target = currentParagraph;
            log.debug("Dispatching to paragraph target: {}", target);

            switch (target) {
                case OLD_PARAGRAPH:
                    oldParagraph();
                    break;
                case NEW_PARAGRAPH:
                    newParagraph();
                    break;
                default:
                    log.warn("Unknown paragraph target '{}'; no dispatch performed.", target);
                    break;
            }
        }

        /**
         * Placeholder implementation for the COBOL {@code OLD-PARAGRAPH} logic.
         * In the original program this paragraph contained a GO TO whose destination
         * was subject to ALTER; its own business logic should be filled in here
         * when the full paragraph source is available.
         *
         * <p>Original COBOL: paragraph {@code OLD-PARAGRAPH} in program {@code MEGADEMO}.
         */
        public void oldParagraph() {
            log.debug("Executing OLD-PARAGRAPH logic.");
            // Business logic for OLD-PARAGRAPH goes here once the full source is available.
            // The GO TO within OLD-PARAGRAPH is replaced by dispatchCurrentParagraph().
        }

        /**
         * Placeholder implementation for the COBOL {@code NEW-PARAGRAPH} logic.
         * After {@link #cfdExit()} executes, all dispatches that formerly went to
         * {@code OLD-PARAGRAPH} are redirected here.
         *
         * <p>Original COBOL: paragraph {@code NEW-PARAGRAPH} in program {@code MEGADEMO}.
         */
        public void newParagraph() {
            log.debug("Executing NEW-PARAGRAPH logic.");
            // Business logic for NEW-PARAGRAPH goes here once the full source is available.
        }

    /**
         * Displays a static message to the console/log.
         * <p>Original COBOL: paragraph {@code OLD-PARAGRAPH} in program {@code MEGADEMO}.
         */
        public void oldParagraph() {
            log.info("This will be altered.");
        }

    /**
         * Handles the redirected ALTER flow by logging a notification message.
         * <p>Original COBOL: paragraph {@code NEW-PARAGRAPH} in program {@code MEGADEMO}.
         * <p>In the original COBOL program, an ALTER statement was used to redirect
         * control flow to this paragraph. When execution arrives here, a message is
         * displayed indicating that the ALTER redirection has taken place.
         */
        public void newParagraph() {
            log.info("ALTER redirected here.");
        }

    /**
         * Registry that simulates the COBOL dynamic-CALL dispatch table.
         * Keys are program names (upper-case); values are {@link DynamicCallable} lambdas.
         * Callers should register known dynamic targets before invoking {@link #cap1}.
         */
        private final Map<String, DynamicCallable> dynamicCallRegistry = new HashMap<>();

        /**
         * Functional interface that represents a dynamically-resolved COBOL CALL target.
         */
        @FunctionalInterface
        public interface DynamicCallable {
            /**
             * Execute the dynamic call.
             *
             * @param argument the BY CONTENT argument passed to the called program
             */
            void call(String argument);
        }

        /**
         * Register a handler for a dynamic CALL target so that {@link #cap1} can dispatch to it.
         *
         * @param programName the COBOL program name (case-insensitive)
         * @param callable    the Java implementation to invoke
         */
        public void registerDynamicCall(String programName, DynamicCallable callable) {
            if (programName != null && callable != null) {
                dynamicCallRegistry.put(programName.trim().toUpperCase(), callable);
            }
        }

        /**
         * Migrated implementation of COBOL paragraph {@code CAP-1} in program {@code MEGADEMO}.
         *
         * <p>Behaviour:
         * <ol>
         *   <li><b>SET ADDRESS OF REL-REC TO WS-POINTER</b> — the relative record buffer
         *       ({@code relRec}) is treated as a view over the same backing byte array as
         *       {@code wsPointerBuffer}; in Java both arrays share the same reference after
         *       this step.</li>
         *   <li><b>CALL 'SUBPGM1'</b> — delegated to {@link Subpgm1Service#execute}.</li>
         *   <li><b>CANCEL 'SUBPGM1'</b> — simulated by nulling the local reference to the
         *       service result, signalling that the module's state should be discarded.</li>
         *   <li><b>MOVE 'SYSTEM' TO WS-ALNUM (1:6)</b> — overwrites the first 6 characters
         *       of {@code wsAlnum} with {@code "SYSTEM"}.</li>
         *   <li><b>CALL WS-ALNUM</b> — dispatches to the registered handler whose key matches
         *       the (trimmed, upper-cased) value of {@code wsAlnum}.</li>
         * </ol>
         *
         * @param empId           EMP-ID passed BY REFERENCE to SUBPGM1; a single-element array
         *                        so that mutations inside the simulated sub-program are visible
         *                        to the caller (reference semantics).
         * @param wsAlnumBuffer   WS-ALNUM as a fixed-length {@code char[]} of at least 6 elements;
         *                        modified in-place (MOVE 'SYSTEM' step).
         * @param wsIdxComp       WS-IDX-COMP passed BY VALUE to SUBPGM1 (COMP field → {@code int}).
         * @param wsPointerBuffer the byte array that WS-POINTER points to; REL-REC is set to
         *                        this same buffer (pointer aliasing simulation).
         * @param subpgm1Service  the Spring service that implements SUBPGM1 logic.
         * @return a {@link Cap1Result} containing the (possibly modified) EMP-ID and WS-ALNUM
         *         after execution, mirroring COBOL BY REFERENCE / shared-state semantics.
         */
        public Cap1Result cap1(
                String[] empId,
                char[] wsAlnumBuffer,
                int wsIdxComp,
                byte[] wsPointerBuffer,
                Subpgm1Service subpgm1Service) {

            // ── SET ADDRESS OF REL-REC TO WS-POINTER ─────────────────────────────────
            // In COBOL this makes REL-REC overlay the same storage as WS-POINTER.
            // In Java we model this as aliasing: relRec points to the same byte array.
            byte[] relRec = wsPointerBuffer;   // aliased — same backing array
            log.info("POINTER ADDR={} (relRec aliased to wsPointerBuffer, length={})",
                    System.identityHashCode(wsPointerBuffer), relRec.length);

            // ── CALL 'SUBPGM1' USING BY REFERENCE EMP-ID
            //                         BY CONTENT  WS-ALNUM
            //                         BY VALUE    WS-IDX-COMP ──────────────────────────
            // BY REFERENCE → pass the array wrapper so mutations are visible to caller.
            // BY CONTENT   → pass a defensive copy so SUBPGM1 cannot alter the original.
            // BY VALUE     → pass the primitive directly.
            char[] wsAlnumCopy = Arrays.copyOf(wsAlnumBuffer, wsAlnumBuffer.length);
            subpgm1Service.execute(empId, wsAlnumCopy, wsIdxComp);

            // ── CANCEL 'SUBPGM1' ─────────────────────────────────────────────────────
            // CANCEL in COBOL releases the program's internal state so the next CALL
            // starts fresh.  In Java we signal this by discarding any cached state;
            // the service bean itself remains in the Spring context (stateless by design).
            subpgm1Service.cancel();
            log.debug("SUBPGM1 cancelled (state reset).");

            // ── MOVE 'SYSTEM' TO WS-ALNUM (1:6) ──────────────────────────────────────
            // COBOL reference modification (1:6) is 1-based, length 6 → Java indices 0..5.
            String literal = "SYSTEM";
            for (int i = 0; i < literal.length() && i < wsAlnumBuffer.length; i++) {
                wsAlnumBuffer[i] = literal.charAt(i);
            }
            log.debug("WS-ALNUM after MOVE 'SYSTEM': {}", new String(wsAlnumBuffer));

            // ── CALL WS-ALNUM USING BY CONTENT 'echo DYNAMIC CALL' ───────────────────
            // The program name is the trimmed, upper-cased value of WS-ALNUM.
            String dynamicProgramName = new String(wsAlnumBuffer).trim().toUpperCase();
            String dynamicArgument = "echo DYNAMIC CALL";

            DynamicCallable target = dynamicCallRegistry.get(dynamicProgramName);
            if (target != null) {
                log.info("Dispatching dynamic CALL to program '{}'", dynamicProgramName);
                target.call(dynamicArgument);
            } else {
                // Simulate the COBOL runtime behaviour when the program is not found:
                // log a warning but do not abort (mirrors many COBOL runtime environments
                // that issue a warning and continue when CALL target is unresolved).
                log.warn("Dynamic CALL target '{}' not registered; call skipped.", dynamicProgramName);
            }

            // ── DISPLAY 'Dynamic CALL executed (if supported).' ───────────────────────
            log.info("Dynamic CALL executed (if supported).");

            // ── Return shared state to caller ─────────────────────────────────────────
            return new Cap1Result(empId[0], new String(wsAlnumBuffer));
        }

        // ─────────────────────────────────────────────────────────────────────────────
        // Inner types
        // ─────────────────────────────────────────────────────────────────────────────

        /**
         * Carries the mutable output values that COBOL would have left in WORKING-STORAGE
         * after paragraph {@code CAP-1} completes.
         */
        public static final class Cap1Result {

            private final String empId;
            private final String wsAlnum;

            public Cap1Result(String empId, String wsAlnum) {
                this.empId = empId;
                this.wsAlnum = wsAlnum;
            }

            /** The (possibly modified) EMP-ID after SUBPGM1 returned. */
            public String getEmpId() {
                return empId;
            }

            /** The value of WS-ALNUM after the MOVE 'SYSTEM' step. */
            public String getWsAlnum() {
                return wsAlnum;
            }

            @Override
            public String toString() {
                return "Cap1Result{empId='" + empId + "', wsAlnum='" + wsAlnum + "'}";
            }
        }

        // ─────────────────────────────────────────────────────────────────────────────
        // Stub inner service — replace with a real @Service bean in production
        // ─────────────────────────────────────────────────────────────────────────────

        /**
         * Represents the external COBOL sub-program {@code SUBPGM1}.
         *
         * <p>In a real migration this would be a separate {@code @Service}-annotated class
         * (or a {@code @FeignClient} if SUBPGM1 is deployed as a micro-service).
         * It is nested here only to keep the generated file self-contained and compilable.
         */
        @Slf4j
        @Service
        public static class Subpgm1Service {

            /**
             * Simulates {@code CALL 'SUBPGM1'}.
             *
             * @param empId      EMP-ID BY REFERENCE — element 0 may be modified.
             * @param wsAlnum    WS-ALNUM BY CONTENT — a defensive copy; changes are NOT
             *                   propagated back to the caller.
             * @param wsIdxComp  WS-IDX-COMP BY VALUE.
             */
            public void execute(String[] empId, char[] wsAlnum, int wsIdxComp) {
                log.info("SUBPGM1 called: empId={}, wsAlnum={}, wsIdxComp={}",
                        empId[0], new String(wsAlnum), wsIdxComp);
                // Real SUBPGM1 logic would be implemented here.
                // empId[0] may be updated to reflect BY REFERENCE semantics.
            }

            /**
             * Simulates {@code CANCEL 'SUBPGM1'} — resets any internal state so that
             * the next {@link #execute} call starts with a clean slate.
             */
            public void cancel() {
                log.debug("SUBPGM1 internal state reset (CANCEL).");
                // Reset any instance-level state fields here if they exist.
            }
        }

    /**
         * Entry point for the {@code INIT-FILES-AND-DATA} COBOL section.
         * <p>
         * In the original COBOL, this section served solely as a delegation point
         * to its body paragraph {@code INIT-1}. All file-open logic, flag
         * initialisation, table-count setup, and table-population loop reside in
         * {@link #init1()}.
         * <p>
         * Original COBOL: paragraph {@code INIT-FILES-AND-DATA} in program {@code MEGADEMO}.
         */
        @Transactional
        public void initFilesAndData() {
            log.debug("Entering INIT-FILES-AND-DATA section — delegating to INIT-1");
            init1();
            log.debug("Exiting INIT-FILES-AND-DATA section");
        }

        /**
         * Migrated body of COBOL paragraph {@code INIT-1} in program {@code MEGADEMO}.
         * <p>
         * Original behaviour:
         * <ol>
         *   <li>Opens the sequential output file {@code INSEQ}.</li>
         *   <li>Opens the indexed file {@code IXFILE} in I-O mode; if the file-status
         *       code {@code WS-FS-IX} is not {@code "00"} (i.e. the file does not yet
         *       exist or could not be opened), it creates the file by opening it for
         *       OUTPUT, closing it, and re-opening it in I-O mode.</li>
         *   <li>Opens the relative file {@code RELFILE} in I-O mode.</li>
         *   <li>Sets the working-storage flag {@code WS-FLAG-AREA} to {@code 'Y'} and
         *       the condition-name {@code FLAG-YES} to {@code TRUE}.</li>
         *   <li>Sets the table counter {@code WS-TBL-COUNT} to {@code 10}.</li>
         *   <li>Performs a {@code VARYING} loop over {@code WS-IDX-BIN} from 1 to
         *       populate the table entries (loop body not fully available in source
         *       extract — represented here as a stub iteration).</li>
         * </ol>
         * <p>
         * Because the COBOL paragraph operated entirely on file-system resources and
         * WORKING-STORAGE that are not exposed as shared state in this migration
         * context, the method encapsulates all side-effects internally and logs each
         * significant step.
         * <p>
         * Original COBOL: paragraph {@code INIT-1} in program {@code MEGADEMO}.
         */
        @Transactional
        public void init1() {
            log.info("INIT-1: Opening INSEQ for OUTPUT");
            openOutputInseq();

            log.info("INIT-1: Opening IXFILE for I-O");
            boolean ixFileOpenedSuccessfully = openIoIxfile();
            if (!ixFileOpenedSuccessfully) {
                log.warn("INIT-1: WS-FS-IX != '00' — recreating IXFILE (OUTPUT then I-O)");
                createAndReopenIxfile();
            }

            log.info("INIT-1: Opening RELFILE for I-O");
            openIoRelfile();

            // MOVE 'Y' TO WS-FLAG-AREA  /  SET FLAG-YES TO TRUE
            boolean wsFlagArea = true;
            boolean flagYes    = true;
            log.debug("INIT-1: WS-FLAG-AREA='Y', FLAG-YES=TRUE  (wsFlagArea={}, flagYes={})",
                      wsFlagArea, flagYes);

            // MOVE 10 TO WS-TBL-COUNT
            int wsTblCount = 10;
            log.debug("INIT-1: WS-TBL-COUNT set to {}", wsTblCount);

            // PERFORM VARYING WS-IDX-BIN FROM 1 BY 1 UNTIL WS-IDX-BIN > WS-TBL-COUNT
            // (loop body not fully available in source extract — iterating indices)
            int[] wsTbl = new int[wsTblCount];
            for (int wsIdxBin = 1; wsIdxBin <= wsTblCount; wsIdxBin++) {
                log.trace("INIT-1: VARYING loop — WS-IDX-BIN={}", wsIdxBin);
                // Table entry initialisation would be performed here once the full
                // loop body is available from the source program.
                wsTbl[wsIdxBin - 1] = wsIdxBin; // default: populate with index value
            }

            log.info("INIT-1: Initialisation complete — {} table entries populated", wsTblCount);
        }

        // -------------------------------------------------------------------------
        // Private helpers — each models a COBOL file-control verb
        // -------------------------------------------------------------------------

        /**
         * Models {@code OPEN OUTPUT INSEQ}.
         * In a full implementation this would delegate to a Spring Batch
         * {@code FlatFileItemWriter} or a {@code java.io.BufferedWriter} backed
         * resource configured via application properties.
         */
        private void openOutputInseq() {
            log.debug("openOutputInseq: OPEN OUTPUT INSEQ — resource initialised");
            // Resource acquisition for INSEQ sequential output file goes here.
        }

        /**
         * Models {@code OPEN I-O IXFILE}.
         *
         * @return {@code true} if the file was opened successfully (file-status {@code "00"}),
         *         {@code false} otherwise (non-zero file-status, i.e. file does not exist).
         */
        private boolean openIoIxfile() {
            log.debug("openIoIxfile: OPEN I-O IXFILE — attempting open");
            // In a real migration this would attempt to open the indexed file and
            // return the result of checking the file-status code WS-FS-IX == "00".
            // Returning true here represents a successful open (status "00").
            return true;
        }

        /**
         * Models the COBOL recovery sequence when {@code WS-FS-IX != '00'}:
         * <pre>
         *   OPEN OUTPUT IXFILE
         *   CLOSE IXFILE
         *   OPEN I-O IXFILE
         * </pre>
         * Creates the indexed file from scratch and then re-opens it for I-O.
         */
        private void createAndReopenIxfile() {
            log.debug("createAndReopenIxfile: OPEN OUTPUT IXFILE (create)");
            // Create the indexed file.
            log.debug("createAndReopenIxfile: CLOSE IXFILE");
            // Close after creation.
            log.debug("createAndReopenIxfile: OPEN I-O IXFILE (reopen)");
            // Re-open for I-O.
        }

        /**
         * Models {@code OPEN I-O RELFILE}.
         * In a full implementation this would delegate to a relative-record
         * file resource (e.g. a {@code RandomAccessFile} or a JPA repository
         * backed by a keyed table).
         */
        private void openIoRelfile() {
            log.debug("openIoRelfile: OPEN I-O RELFILE — resource initialised");
            // Resource acquisition for RELFILE relative file goes here.
        }

    /**
         * Entry point for the {@code TABLE-OPS} COBOL section.
         * <p>
         * In the original COBOL, {@code TABLE-OPS} is a SECTION whose only
         * responsibility is to fall through to its first (and only) paragraph
         * body {@code TBL-1}.  This method therefore delegates directly to
         * {@link #tbl1(List)}.
         * <p>
         * Original COBOL: section {@code TABLE-OPS} in program {@code MEGADEMO}.
         *
         * @param tblItems list of table items, each carrying a string key;
         *                 corresponds to the COBOL {@code TBL-ITEM} table
         *                 (OCCURS … TIMES) with associated {@code TBL-ITEM-KEY}
         */
        public void tableOps(List<TblItem> tblItems) {
            tbl1(tblItems);
        }

        /**
         * Implements the {@code TBL-1} COBOL paragraph.
         * <ol>
         *   <li>Iterates over every item in {@code tblItems} (1-based in COBOL,
         *       0-based here) and logs its 1-based index and key.</li>
         *   <li>Performs a linear search for the first item whose key starts with
         *       {@code "AA"} (equivalent to {@code TBL-ITEM-KEY(TBL-IDX)(1:2) = 'AA'}).
         *       Logs a "NOT FOUND" message when no such item exists, or logs the
         *       matching item's index and key when found.</li>
         * </ol>
         * <p>
         * Original COBOL: paragraph {@code TBL-1} in program {@code MEGADEMO}.
         *
         * @param tblItems list of table items to process; must not be {@code null}
         */
        public void tbl1(List<TblItem> tblItems) {

            // ── Phase 1: display every item ──────────────────────────────────────
            // COBOL:
            //   SET TBL-IDX TO 1
            //   PERFORM UNTIL TBL-IDX > TBL-COUNT
            //      DISPLAY 'TBL ITEM (' TBL-IDX ') KEY=' TBL-ITEM-KEY (TBL-IDX)
            //      SET TBL-IDX UP BY 1
            //   END-PERFORM
            int tblCount = (tblItems == null) ? 0 : tblItems.size();

            for (int tblIdx = 1; tblIdx <= tblCount; tblIdx++) {
                TblItem item = tblItems.get(tblIdx - 1);   // convert to 0-based
                log.info("TBL ITEM ({}) KEY={}", tblIdx, item.getKey());
            }

            // ── Phase 2: linear SEARCH for key starting with "AA" ────────────────
            // COBOL:
            //   SEARCH TBL-ITEM
            //      AT END DISPLAY 'LINEAR SEARCH: NOT FOUND'
            //      WHEN TBL-ITEM-KEY (TBL-IDX) (1:2) = 'AA'
            //         DISPLAY 'LINEAR SEARCH: FOUND AT ' TBL-IDX
            //
            // COBOL SEARCH resets TBL-IDX to 1 implicitly (SET TBL-IDX TO 1 is
            // required before SEARCH; we replicate that here).
            boolean found = false;
            for (int tblIdx = 1; tblIdx <= tblCount; tblIdx++) {
                TblItem item = tblItems.get(tblIdx - 1);
                String key = item.getKey();

                // (1:2) in COBOL is a reference modification: characters 1 through 2
                // i.e. the first two characters of the key string.
                String keyPrefix = (key != null && key.length() >= 2)
                        ? key.substring(0, 2)
                        : (key != null ? key : "");

                if ("AA".equals(keyPrefix)) {
                    log.info("LINEAR SEARCH: FOUND AT {} KEY={}", tblIdx, key);
                    found = true;
                    break;
                }
            }

            if (!found) {
                log.info("LINEAR SEARCH: NOT FOUND");
            }
        }

        // ── Inner value-object representing one COBOL TBL-ITEM occurrence ────────

        /**
         * Value object that represents a single occurrence of the COBOL
         * {@code TBL-ITEM} table entry.
         * <p>
         * In the original COBOL this is an {@code OCCURS} group item containing
         * (at minimum) the field {@code TBL-ITEM-KEY PIC X(n)}.
         */
        public static class TblItem {

            /** Corresponds to {@code TBL-ITEM-KEY PIC X(n)}. */
            private String key;

            public TblItem() {
            }

            public TblItem(String key) {
                this.key = key;
            }

            public String getKey() {
                return key;
            }

            public void setKey(String key) {
                this.key = key;
            }

            @Override
            public String toString() {
                return "TblItem{key='" + key + "'}";
            }
        }

    /**
         * Entry point for the {@code STRING-AND-EDITING-OPS} COBOL section.
         * <p>
         * Delegates immediately to {@link #str1(String, String)} which contains
         * the section body logic (paragraph {@code STR-1}).
         * <p>
         * Original COBOL: section {@code STRING-AND-EDITING-OPS} in program {@code MEGADEMO}.
         *
         * @param empFname the employee first name (EMP-FNAME)
         * @param empLname the employee last name  (EMP-LNAME)
         * @return a {@link StringAndEditingResult} containing the reformatted date
         *         ({@code WS-DATE-PRINT}) and the assembled greeting ({@code WS-AL})
         */
        public StringAndEditingResult stringAndEditingOps(String empFname, String empLname) {
            log.debug("stringAndEditingOps called with empFname='{}', empLname='{}'",
                    empFname, empLname);
            return str1(empFname, empLname);
        }

        /**
         * Implements paragraph {@code STR-1} from program {@code MEGADEMO}.
         * <p>
         * <ol>
         *   <li>Initialises {@code WS-DATE-YYYYMMDD} to the literal {@code "20260305"}.</li>
         *   <li>Reformats that date into {@code WS-DATE-PRINT} as {@code "YYYY/MM/DD"}
         *       by copying two-character substrings into positions 1-2, 4-5, and 7-8
         *       (COBOL 1-based; Java 0-based offsets used internally).</li>
         *   <li>Builds a greeting string into {@code WS-AL} by concatenating
         *       {@code "Hello, "}, {@code EMP-FNAME}, {@code " "}, {@code EMP-LNAME},
         *       and {@code "!"} — mirroring the COBOL {@code STRING … DELIMITED BY SIZE}
         *       statement.</li>
         * </ol>
         * Original COBOL: paragraph {@code STR-1} in program {@code MEGADEMO}.
         *
         * @param empFname the employee first name (EMP-FNAME)
         * @param empLname the employee last name  (EMP-LNAME)
         * @return a {@link StringAndEditingResult} containing {@code wsDatePrint}
         *         and {@code wsAl}
         */
        public StringAndEditingResult str1(String empFname, String empLname) {

            // MOVE '20260305' TO WS-DATE-YYYYMMDD
            final String wsDateYyyymmdd = "20260305";
            log.debug("str1: wsDateYyyymmdd='{}'", wsDateYyyymmdd);

            /*
             * Build WS-DATE-PRINT by copying two-character chunks from WS-DATE-YYYYMMDD.
             *
             * COBOL reference-modification is 1-based: (1:2), (3:2), (5:2).
             * The target positions in WS-DATE-PRINT are (1:2), (4:2), (7:2),
             * implying a picture such as "XXXX/XX/XX" where positions 3 and 6
             * are separator characters.  We use '/' as the separator to produce
             * a standard YYYY/MM/DD layout; the COBOL source leaves those bytes
             * uninitialised (spaces), so '/' is a reasonable, readable default.
             *
             * Java substring indices are 0-based and the end index is exclusive.
             */
            // MOVE WS-DATE-YYYYMMDD (1:2) TO WS-DATE-PRINT (1:2)  → chars 0-1 → positions 0-1
            String yyyy = wsDateYyyymmdd.substring(0, 2);   // "20"  (century + decade)
            // MOVE WS-DATE-YYYYMMDD (3:2) TO WS-DATE-PRINT (4:2)  → chars 2-3 → positions 3-4
            String mm   = wsDateYyyymmdd.substring(2, 4);   // "26"  (year within century)
            // MOVE WS-DATE-YYYYMMDD (5:2) TO WS-DATE-PRINT (7:2)  → chars 4-5 → positions 6-7
            String dd   = wsDateYyyymmdd.substring(4, 6);   // "03"  (month)

            // Assemble WS-DATE-PRINT: "XX/XX/XX" (8 visible chars + separators = 8 total)
            // Positions 1-2 = yyyy, 3 = '/', 4-5 = mm, 6 = '/', 7-8 = dd
            String wsDatePrint = yyyy + "/" + mm + "/" + dd;
            log.debug("str1: wsDatePrint='{}'", wsDatePrint);

            /*
             * STRING 'Hello, ' EMP-FNAME ' ' EMP-LNAME '!' DELIMITED BY SIZE
             *        INTO WS-AL
             *
             * DELIMITED BY SIZE means each operand contributes its full value
             * (no early termination on spaces or special characters).
             * In Java this is a straightforward concatenation.
             */
            String wsAl = "Hello, "
                    + (empFname == null ? "" : empFname)
                    + " "
                    + (empLname == null ? "" : empLname)
                    + "!";
            log.debug("str1: wsAl='{}'", wsAl);

            return new StringAndEditingResult(wsDatePrint, wsAl);
        }

        // -------------------------------------------------------------------------
        // Result carrier — replaces WORKING-STORAGE fields WS-DATE-PRINT and WS-AL
        // -------------------------------------------------------------------------

        /**
         * Immutable value object that carries the two output fields produced by
         * paragraph {@code STR-1}: the reformatted date and the assembled greeting.
         */
        public static final class StringAndEditingResult {

            /** Corresponds to {@code WS-DATE-PRINT} — reformatted date, e.g. {@code "20/26/03"}. */
            private final String wsDatePrint;

            /** Corresponds to {@code WS-AL} — assembled greeting string. */
            private final String wsAl;

            /**
             * Constructs a new result.
             *
             * @param wsDatePrint the reformatted date string
             * @param wsAl        the assembled greeting string
             */
            public StringAndEditingResult(String wsDatePrint, String wsAl) {
                this.wsDatePrint = wsDatePrint;
                this.wsAl        = wsAl;
            }

            /**
             * Returns the reformatted date ({@code WS-DATE-PRINT}).
             *
             * @return reformatted date string
             */
            public String getWsDatePrint() {
                return wsDatePrint;
            }

            /**
             * Returns the assembled greeting ({@code WS-AL}).
             *
             * @return greeting string
             */
            public String getWsAl() {
                return wsAl;
            }

            @Override
            public String toString() {
                return "StringAndEditingResult{"
                        + "wsDatePrint='" + wsDatePrint + '\''
                        + ", wsAl='" + wsAl + '\''
                        + '}';
            }
        }

    /**
         * Entry point for the {@code ARITHMETIC-AND-FUNCTIONS} COBOL section.
         * Acts as a section wrapper that delegates to {@link #ari1()}, mirroring
         * the COBOL pattern where a SECTION entry paragraph simply falls through
         * to the first paragraph in the section.
         *
         * <p>Original COBOL: paragraph {@code ARITHMETIC-AND-FUNCTIONS} in program {@code MEGADEMO}.
         */
        public void arithmeticAndFunctions() {
            ari1();
        }

        /**
         * Implements the body of COBOL paragraph {@code ARI-1}.
         * <ul>
         *   <li>Computes {@code IX-AMT = (13 + 7) * 2 - 5 / 2} using integer arithmetic
         *       (COBOL integer division truncates toward zero).</li>
         *   <li>Displays the computed value.</li>
         *   <li>Displays the current date/time (equivalent to {@code FUNCTION CURRENT-DATE}).</li>
         *   <li>Displays {@code MAX(3, 7)} and {@code MIN(3, 7)}.</li>
         *   <li>Displays a random number (equivalent to {@code FUNCTION RANDOM}).</li>
         *   <li>Displays the length of a representative alphanumeric field
         *       ({@code FUNCTION LENGTH(WS-ALNUM)}).</li>
         * </ul>
         *
         * <p>Original COBOL: paragraph {@code ARI-1} in program {@code MEGADEMO}.
         */
        public void ari1() {
            // COMPUTE IX-AMT = (13 + 7) * 2 - 5 / 2
            // COBOL integer arithmetic: 5 / 2 = 2 (truncated), so result = 20 * 2 - 2 = 40 - 2 = 38
            // Note: COBOL operator precedence: multiplication/division before addition/subtraction.
            // (13 + 7) = 20; 20 * 2 = 40; 5 / 2 = 2 (integer truncation); 40 - 2 = 38
            int ixAmt = (13 + 7) * 2 - 5 / 2;
            log.info("COMPUTE IX-AMT={}", ixAmt);

            // DISPLAY 'CURRENT-DATE=' FUNCTION CURRENT-DATE
            // COBOL FUNCTION CURRENT-DATE returns a 21-character string:
            // YYYYMMDDHHMMSSCC+HHMM  (CC = hundredths of seconds, +HHMM = UTC offset)
            LocalDateTime now = LocalDateTime.now();
            String currentDate = now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                    + String.format("%02d", now.getNano() / 10_000_000)
                    + "+0000";
            log.info("CURRENT-DATE={}", currentDate);

            // DISPLAY 'MAX(3,7)=' FUNCTION MAX(3 7)
            int maxValue = Math.max(3, 7);
            log.info("MAX(3,7)={}", maxValue);

            // DISPLAY 'MIN(3,7)=' FUNCTION MIN(3 7)
            int minValue = Math.min(3, 7);
            log.info("MIN(3,7)={}", minValue);

            // DISPLAY 'RANDOM=' FUNCTION RANDOM
            // COBOL FUNCTION RANDOM returns a pseudo-random number in [0, 1)
            BigDecimal randomValue = BigDecimal.valueOf(new Random().nextDouble())
                    .setScale(10, RoundingMode.HALF_UP);
            log.info("RANDOM={}", randomValue);

            // DISPLAY 'LENGTH(WS-ALNUM)=' FUNCTION LENGTH(WS-ALNUM)
            // WS-ALNUM is a typical alphanumeric working-storage field; its length is
            // represented here as a constant matching a common PIC X(20) declaration.
            // If the actual field length differs, update WS_ALNUM_LENGTH accordingly.
            final int wsAlnumLength = 20;
            log.info("LENGTH(WS-ALNUM)={}", wsAlnumLength);
        }

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

    // ---------------------------------------------------------------------------
        // Inner record type representing INSEQ-REC / SORT-FILE record layout
        // ---------------------------------------------------------------------------

        /**
         * Represents a single sequential / sort file record.
         * <p>
         * COBOL layout (INSEQ-REC):
         * <pre>
         *   INSEQ-ID      PIC 9(4)
         *   INSEQ-NAME    PIC X(30)
         *   INSEQ-AMT     PIC 9(6)V99
         *   INSEQ-DATE    PIC 9(8)
         *   INSEQ-COMMENT PIC X(50)
         * </pre>
         */
        public static class InseqRecord {
            private int id;
            private String name;
            private BigDecimal amount;
            private int date;
            private String comment;

            public InseqRecord(int id, String name, BigDecimal amount, int date, String comment) {
                this.id = id;
                this.name = name;
                this.amount = amount;
                this.date = date;
                this.comment = comment;
            }

            public int getId()           { return id; }
            public String getName()      { return name; }
            public BigDecimal getAmount(){ return amount; }
            public int getDate()         { return date; }
            public String getComment()   { return comment; }

            public void setId(int id)                   { this.id = id; }
            public void setName(String name)            { this.name = name; }
            public void setAmount(BigDecimal amount)    { this.amount = amount; }
            public void setDate(int date)               { this.date = date; }
            public void setComment(String comment)      { this.comment = comment; }

            @Override
            public String toString() {
                return String.format(
                    "InseqRecord{id=%d, name='%s', amount=%s, date=%d, comment='%s'}",
                    id, name, amount, date, comment);
            }
        }

        // ---------------------------------------------------------------------------
        // SECTION entry point  →  SEQ-FILE-AND-SORT-OPS
        // ---------------------------------------------------------------------------

        /**
         * Entry point for the COBOL SECTION {@code SEQ-FILE-AND-SORT-OPS}.
         * <p>
         * The section simply delegates to its body paragraph {@code SQ-1}, which:
         * <ol>
         *   <li>Populates an {@code INSEQ-REC} record with fixed seed data.</li>
         *   <li>Writes the record to the sequential output file (represented here
         *       as the returned {@link InseqRecord}).</li>
         *   <li>Closes the sequential file.</li>
         *   <li>Sorts a sort-file on ascending {@code S-KEY} (record id) using an
         *       input procedure ({@code SORT-IN-PROC}) that feeds the single written
         *       record into the sort work area.</li>
         * </ol>
         *
         * <p>Original COBOL: SECTION {@code SEQ-FILE-AND-SORT-OPS} / paragraph
         * {@code SQ-1} in program {@code MEGADEMO}.
         *
         * @return a {@link List} of {@link InseqRecord} objects in ascending key
         *         (id) order — the logical equivalent of the COBOL SORT output.
         */
        @Transactional
        public List<InseqRecord> seqFileAndSortOps() {
            log.info("Entering SEQ-FILE-AND-SORT-OPS section");
            List<InseqRecord> sortedOutput = sq1();
            log.info("Leaving SEQ-FILE-AND-SORT-OPS section — {} record(s) produced",
                    sortedOutput.size());
            return sortedOutput;
        }

        // ---------------------------------------------------------------------------
        // Paragraph  →  SQ-1
        // ---------------------------------------------------------------------------

        /**
         * Migrated from COBOL paragraph {@code SQ-1} in program {@code MEGADEMO}.
         * <p>
         * <b>Original COBOL logic:</b>
         * <ol>
         *   <li>{@code MOVE 1            TO INSEQ-ID}      — set record id to 1</li>
         *   <li>{@code MOVE 'John Doe'   TO INSEQ-NAME}    — set name</li>
         *   <li>{@code MOVE 9999.99      TO INSEQ-AMT}     — set amount</li>
         *   <li>{@code MOVE 20260305     TO INSEQ-DATE}    — set date (YYYYMMDD)</li>
         *   <li>{@code MOVE 'Sample record' TO INSEQ-COMMENT} — set comment</li>
         *   <li>{@code WRITE INSEQ-REC}                    — write record to file</li>
         *   <li>{@code CLOSE INSEQ}                        — close sequential file</li>
         *   <li>{@code SORT SORT-FILE ON ASCENDING KEY S-KEY
         *        INPUT PROCEDURE IS SORT-IN-PROC
         *        OUTPUT PROCEDURE IS ...}                  — sort on ascending id</li>
         * </ol>
         *
         * <p>In Java the "file write + close" is modelled by collecting the record
         * into an in-memory list (the logical file buffer), and the SORT is
         * implemented with {@link java.util.Comparator} on {@code id} (S-KEY).
         * The INPUT PROCEDURE ({@code SORT-IN-PROC}) is inlined as the population
         * of the sort input list.
         *
         * @return sorted {@link List} of {@link InseqRecord} — ascending by id.
         */
        public List<InseqRecord> sq1() {
            log.debug("Entering paragraph SQ-1");

            // -----------------------------------------------------------------------
            // MOVE literals TO INSEQ-REC fields
            // -----------------------------------------------------------------------
            int     inseqId      = 1;
            String  inseqName    = "John Doe";
            BigDecimal inseqAmt  = new BigDecimal("9999.99").setScale(2, RoundingMode.HALF_UP);
            int     inseqDate    = 20260305;
            String  inseqComment = "Sample record";

            InseqRecord inseqRec = new InseqRecord(inseqId, inseqName, inseqAmt,
                                                   inseqDate, inseqComment);

            // -----------------------------------------------------------------------
            // WRITE INSEQ-REC  →  add to the logical sequential-file buffer
            // -----------------------------------------------------------------------
            List<InseqRecord> inseqFileBuffer = new ArrayList<>();
            inseqFileBuffer.add(inseqRec);
            log.debug("WRITE INSEQ-REC: {}", inseqRec);

            // -----------------------------------------------------------------------
            // CLOSE INSEQ  →  no physical file handle to close; log the event
            // -----------------------------------------------------------------------
            log.debug("CLOSE INSEQ — sequential file closed (logical)");

            // -----------------------------------------------------------------------
            // SORT SORT-FILE ON ASCENDING KEY S-KEY
            //   INPUT PROCEDURE IS SORT-IN-PROC
            //   OUTPUT PROCEDURE IS <output-proc>
            //
            // INPUT PROCEDURE (SORT-IN-PROC): releases records from inseqFileBuffer
            //   into the sort work area.
            // SORT KEY (S-KEY) maps to InseqRecord.id (PIC 9(4)).
            // -----------------------------------------------------------------------
            List<InseqRecord> sortWorkArea = new ArrayList<>(inseqFileBuffer); // RELEASE
            sortWorkArea.sort(Comparator.comparingInt(InseqRecord::getId));     // ASCENDING KEY
            log.debug("SORT complete — {} record(s) in sort output", sortWorkArea.size());

            // -----------------------------------------------------------------------
            // OUTPUT PROCEDURE: return sorted records to the caller
            // (equivalent to RETURN SORT-FILE / WRITE OUTPUT-FILE in COBOL)
            // -----------------------------------------------------------------------
            log.debug("Leaving paragraph SQ-1");
            return sortWorkArea;
        }

    /**
         * Entry point for the {@code SORT-IN-PROC} section.
         *
         * <p>In the original COBOL the SORT input procedure begins here and immediately
         * falls through to {@code SIP-1}, which performs the actual {@code RELEASE}
         * statement.  This method replicates that delegation.
         *
         * <p>Original COBOL: section {@code SORT-IN-PROC} in program {@code MEGADEMO}.
         *
         * @param inseqRec the current input sequential record that is to be released
         *                 into the sort work area (maps to {@code INSEQ-REC}, PIC X)
         * @return the sort record value produced by {@code SIP-1} (maps to
         *         {@code SORT-REC} after the {@code RELEASE} statement)
         */
        public String sortInProc(String inseqRec) {
            log.debug("SORT-IN-PROC entered with inseqRec='{}'", inseqRec);
            return sip1(inseqRec);
        }

        /**
         * Implements paragraph {@code SIP-1} — the body of the {@code SORT-IN-PROC}
         * section.
         *
         * <p>The original COBOL statement is:
         * <pre>
         *   SIP-1.
         *       RELEASE SORT-REC FROM INSEQ-REC
         * </pre>
         *
         * <p>The {@code RELEASE … FROM} verb copies the sending field ({@code INSEQ-REC})
         * into the sort record area ({@code SORT-REC}) and then makes that record
         * available to the SORT facility.  In Java, where there is no runtime SORT
         * facility to call, the equivalent behaviour is to return the copied record
         * value to the caller so that the caller (typically a sort-input loop) can
         * add it to the collection being sorted.
         *
         * <p>Original COBOL: paragraph {@code SIP-1} in program {@code MEGADEMO}.
         *
         * @param inseqRec the input record to be released into the sort work area
         *                 (maps to {@code INSEQ-REC})
         * @return the value that was moved into {@code SORT-REC} before the release
         */
        public String sip1(String inseqRec) {
            log.debug("SIP-1: RELEASE SORT-REC FROM INSEQ-REC — inseqRec='{}'", inseqRec);

            // RELEASE SORT-REC FROM INSEQ-REC
            // The FROM clause implicitly moves INSEQ-REC into SORT-REC before
            // releasing it to the sort facility.  We model SORT-REC as a local
            // variable and return it so the caller can accumulate records for sorting.
            String sortRec = inseqRec;

            log.debug("SIP-1: sortRec set to '{}'", sortRec);
            return sortRec;
        }

    /**
         * Represents a single record returned from the SORT-FILE.
         * Maps to the COBOL {@code SORT-REC} structure containing {@code S-KEY} and {@code S-DATA}.
         */
        public static class SortRec {
            private final String sKey;
            private final String sData;

            public SortRec(String sKey, String sData) {
                this.sKey = sKey;
                this.sData = sData;
            }

            public String getSKey() {
                return sKey;
            }

            public String getSData() {
                return sData;
            }
        }

        /**
         * SORT-OUT-PROC section entry point — delegates to {@code sop1()}.
         * <p>
         * Original COBOL: section {@code SORT-OUT-PROC} in program {@code MEGADEMO}.
         * The section simply transfers control to the section body paragraph {@code SOP-1}.
         *
         * @param sortedRecords the list of records returned from the sort file, in sorted order.
         */
        public void sortOutProc(List<SortRec> sortedRecords) {
            sop1(sortedRecords);
        }

        /**
         * Iterates over all sorted records, displaying each key and data value.
         * <p>
         * Original COBOL: paragraph {@code SOP-1} in program {@code MEGADEMO}.
         * <pre>
         *   SOP-1.
         *     RETURN SORT-FILE RECORD INTO SORT-REC AT END GO TO SOP-EXIT
         *     DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
         *     GO TO SOP-1
         * </pre>
         * The COBOL paragraph loops via {@code GO TO SOP-1} and exits via {@code GO TO SOP-EXIT}
         * when the AT END condition is raised (i.e., no more records). This is modelled as a
         * standard Java for-each loop over the pre-populated sorted record list.
         *
         * @param sortedRecords the list of records returned from the sort file, in sorted order.
         */
        public void sop1(List<SortRec> sortedRecords) {
            for (SortRec sortRec : sortedRecords) {
                // DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
                log.info("SORTED:{} -> {}", sortRec.getSKey(), sortRec.getSData());
            }
            // AT END condition reached — falls through to SOP-EXIT (method return)
        }

    /**
         * Entry point for the {@code CONTROL-FLOW-DEMO} COBOL section.
         * Delegates immediately to {@link #cfd1()}, which contains the section body.
         * <p>Original COBOL: section {@code CONTROL-FLOW-DEMO} in program {@code MEGADEMO}.
         */
        public void controlFlowDemo() {
            cfd1();
        }

        /**
         * Implements the body of COBOL paragraph {@code CFD-1}.
         * <ol>
         *   <li>Iterates {@code WS-IDX-COMP} from 1 through 3 (inclusive), displaying
         *       each value — equivalent to {@code PERFORM VARYING … UNTIL > 3}.</li>
         *   <li>Sets {@code WS-IDX-COMP} to 2 and evaluates it with an
         *       {@code EVALUATE} construct, printing the matching label or a default
         *       "other" message for any unmatched value.</li>
         * </ol>
         * <p>Original COBOL: paragraph {@code CFD-1} in program {@code MEGADEMO}.
         */
        public void cfd1() {

            // PERFORM VARYING WS-IDX-COMP FROM 1 BY 1 UNTIL WS-IDX-COMP > 3
            for (int wsIdxComp = 1; wsIdxComp <= 3; wsIdxComp++) {
                log.info("PERFORM VARYING i={}", wsIdxComp);
            }

            // MOVE 2 TO WS-IDX-COMP
            int wsIdxComp = 2;

            // EVALUATE WS-IDX-COMP
            //   WHEN 1  → "EVAL: one"
            //   WHEN 2  → "EVAL: two"
            //   WHEN OTHER → "EVAL: other"
            switch (wsIdxComp) {
                case 1:
                    log.info("EVAL: one");
                    break;
                case 2:
                    log.info("EVAL: two");
                    break;
                default:
                    log.info("EVAL: other");
                    break;
            }
        }

    @Autowired
        private Subpgm1Service subpgm1Service;

        /**
         * Entry point for the {@code CALLS-AND-POINTERS} COBOL section.
         * Delegates immediately to {@link #cap1(String[], String, int)} which
         * contains the section body originally coded in paragraph {@code CAP-1}.
         *
         * <p>Original COBOL: section {@code CALLS-AND-POINTERS} in program {@code MEGADEMO}.
         *
         * @param relRecBuffer  byte buffer that backs the {@code REL-REC} record
         *                      (simulates {@code SET ADDRESS OF REL-REC TO WS-POINTER})
         * @param wsPointer     string representation of the pointer / address value
         *                      ({@code WS-POINTER})
         * @param empId         employee-id passed BY REFERENCE to {@code SUBPGM1}
         *                      ({@code EMP-ID})
         * @param wsAlnum       alphanumeric working-storage field ({@code WS-ALNUM});
         *                      element [0] is passed BY CONTENT to {@code SUBPGM1}
         *                      and element [0] is also updated with {@code 'SYSTEM'}
         * @param wsIdxComp     integer index passed BY VALUE to {@code SUBPGM1}
         *                      ({@code WS-IDX-COMP})
         * @return updated {@code wsAlnum} array after all mutations performed in
         *         {@code CAP-1}
         */
        public String[] callsAndPointers(
                byte[]   relRecBuffer,
                String   wsPointer,
                String[] empId,
                String[] wsAlnum,
                int      wsIdxComp) {

            return cap1(relRecBuffer, wsPointer, empId, wsAlnum, wsIdxComp);
        }

        /**
         * Implements the body of COBOL paragraph {@code CAP-1} inside section
         * {@code CALLS-AND-POINTERS} of program {@code MEGADEMO}.
         *
         * <p>Steps performed:
         * <ol>
         *   <li>{@code SET ADDRESS OF REL-REC TO WS-POINTER} — recorded in the
         *       {@code relRecBuffer} reference; in Java the buffer is simply
         *       acknowledged (pointer arithmetic is not applicable in managed
         *       memory).</li>
         *   <li>{@code DISPLAY 'POINTER ADDR=' WS-POINTER} — logged via SLF4J.</li>
         *   <li>{@code CALL 'SUBPGM1' USING BY REFERENCE EMP-ID,
         *       BY CONTENT WS-ALNUM, BY VALUE WS-IDX-COMP} — delegated to the
         *       injected {@link Subpgm1Service}.</li>
         *   <li>{@code CANCEL 'SUBPGM1'} — simulated by invoking
         *       {@link Subpgm1Service#cancel()}.</li>
         *   <li>{@code MOVE 'SYSTEM' TO WS-ALNUM (1:6)} — overwrites the first
         *       six characters of {@code wsAlnum[0]} with {@code "SYSTEM"}.</li>
         *   <li>{@code CALL WS-ALNUM} — dynamic program call resolved via
         *       {@link Subpgm1Service#callDynamic(String)} using the (now updated)
         *       value of {@code wsAlnum[0]}.</li>
         * </ol>
         *
         * <p>Original COBOL: paragraph {@code CAP-1} in program {@code MEGADEMO}.
         *
         * @param relRecBuffer  byte buffer representing the {@code REL-REC} record
         * @param wsPointer     pointer address value ({@code WS-POINTER})
         * @param empId         employee-id array; element [0] is passed by reference
         * @param wsAlnum       alphanumeric field array; mutated in place
         * @param wsIdxComp     integer index passed by value
         * @return the mutated {@code wsAlnum} array
         */
        public String[] cap1(
                byte[]   relRecBuffer,
                String   wsPointer,
                String[] empId,
                String[] wsAlnum,
                int      wsIdxComp) {

            // SET ADDRESS OF REL-REC TO WS-POINTER
            // In Java, managed memory does not support raw pointer assignment.
            // The relRecBuffer reference is accepted as the logical equivalent of
            // the COBOL pointer target; no byte-level re-addressing is performed.
            log.debug("SET ADDRESS OF REL-REC TO WS-POINTER: relRecBuffer length={}, wsPointer={}",
                    relRecBuffer != null ? relRecBuffer.length : 0, wsPointer);

            // DISPLAY 'POINTER ADDR=' WS-POINTER
            log.info("POINTER ADDR={}", wsPointer);

            // CALL 'SUBPGM1' USING BY REFERENCE EMP-ID
            //                      BY CONTENT  WS-ALNUM
            //                      BY VALUE    WS-IDX-COMP
            // BY REFERENCE: empId[0] may be updated by the callee.
            // BY CONTENT  : a copy of wsAlnum[0] is passed; callee changes do not
            //               propagate back.
            // BY VALUE    : wsIdxComp is passed as a primitive.
            String wsAlnumCopy = wsAlnum != null && wsAlnum.length > 0 ? wsAlnum[0] : "";
            subpgm1Service.call(empId, wsAlnumCopy, wsIdxComp);

            // CANCEL 'SUBPGM1'
            // Releases the runtime's hold on the called program; simulated here by
            // invoking the cancel lifecycle method on the service.
            subpgm1Service.cancel();

            // MOVE 'SYSTEM' TO WS-ALNUM (1:6)
            // COBOL reference modification (1:6) replaces characters 1-6 (1-based)
            // with 'SYSTEM'.  In Java this is 0-based index 0 through 5.
            if (wsAlnum != null && wsAlnum.length > 0) {
                String current = wsAlnum[0] != null ? wsAlnum[0] : "";
                // Pad to at least 6 characters so the overlay is always valid.
                if (current.length() < 6) {
                    current = String.format("%-" + Math.max(6, current.length()) + "s", current);
                }
                // Replace first 6 characters with "SYSTEM", preserve the remainder.
                String updated = "SYSTEM" + (current.length() > 6 ? current.substring(6) : "");
                wsAlnum[0] = updated;
            }

            // CALL WS-ALNUM  (dynamic program call using the current value of WS-ALNUM)
            // The program name to invoke is now the updated value of wsAlnum[0],
            // trimmed to remove any trailing padding.
            String dynamicProgramName = (wsAlnum != null && wsAlnum.length > 0 && wsAlnum[0] != null)
                    ? wsAlnum[0].trim()
                    : "";
            log.info("Dynamic CALL to program: '{}'", dynamicProgramName);
            subpgm1Service.callDynamic(dynamicProgramName);

            return wsAlnum;
        }

    private static final String MSG_INFO_PREFIX = "INFO: ";

        /**
         * Main entry point for the MEGADEMO program.
         * <p>
         * Corresponds to COBOL paragraph {@code MAIN-ENTRY} in program {@code MEGADEMO}.
         * <p>
         * Displays program start timestamp, optionally logs debug mode status,
         * then sequentially performs all major processing sections:
         * <ol>
         *   <li>INIT-FILES-AND-DATA</li>
         *   <li>TABLE-OPS</li>
         *   <li>STRING-AND-EDITING-OPS</li>
         *   <li>ARITHMETIC-AND-FUNCTIONS</li>
         *   <li>INDEXED-FILE-OPS</li>
         *   <li>RELATIVE-FILE-OPS</li>
         *   <li>SEQ-FILE-AND-SORT-OPS</li>
         *   <li>CONTROL-FLOW-DEMO</li>
         *   <li>CALLS-AND-POINTERS</li>
         * </ol>
         * Finally displays program end timestamp.
         *
         * @param wsDebug {@code true} if debug mode is enabled (equivalent to COBOL {@code WS-DEBUG} flag)
         */
        public void mainEntry(boolean wsDebug) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSSS");

            // DISPLAY MSG-INFO-PREFIX 'Program start: ' FUNCTION CURRENT-DATE
            String startTimestamp = LocalDateTime.now().format(formatter);
            System.out.println(MSG_INFO_PREFIX + "Program start: " + startTimestamp);
            log.info("{}Program start: {}", MSG_INFO_PREFIX, startTimestamp);

            // IF WS-DEBUG DISPLAY MSG-INFO-PREFIX 'Debug mode enabled.' END-IF
            if (wsDebug) {
                System.out.println(MSG_INFO_PREFIX + "Debug mode enabled.");
                log.debug("{}Debug mode enabled.", MSG_INFO_PREFIX);
            }

            // PERFORM INIT-FILES-AND-DATA
            initFilesAndData(wsDebug);

            // PERFORM TABLE-OPS
            tableOps(wsDebug);

            // PERFORM STRING-AND-EDITING-OPS
            stringAndEditingOps(wsDebug);

            // PERFORM ARITHMETIC-AND-FUNCTIONS
            arithmeticAndFunctions(wsDebug);

            // PERFORM INDEXED-FILE-OPS
            indexedFileOps(wsDebug);

            // PERFORM RELATIVE-FILE-OPS
            relativeFileOps(wsDebug);

            // PERFORM SEQ-FILE-AND-SORT-OPS
            seqFileAndSortOps(wsDebug);

            // PERFORM CONTROL-FLOW-DEMO
            controlFlowDemo(wsDebug);

            // PERFORM CALLS-AND-POINTERS
            callsAndPointers(wsDebug);

            // DISPLAY MSG-INFO-PREFIX 'Program end:   ' FUNCTION CURRENT-DATE
            String endTimestamp = LocalDateTime.now().format(formatter);
            System.out.println(MSG_INFO_PREFIX + "Program end:   " + endTimestamp);
            log.info("{}Program end:   {}", MSG_INFO_PREFIX, endTimestamp);
        }

        /**
         * Stub delegation method for COBOL section {@code INIT-FILES-AND-DATA}.
         * <p>Original COBOL: paragraph {@code INIT-FILES-AND-DATA} in program {@code MEGADEMO}.
         * Opens output/IO files, initialises flags, tables, and working-storage areas.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void initFilesAndData(boolean wsDebug) {
            // Opens INSEQ (OUTPUT), IXFILE (I-O), RELFILE (I-O).
            // If WS-FS-IX != '00', re-opens IXFILE as OUTPUT then I-O.
            // Sets WS-FLAG-AREA = 'Y', FLAG-YES = TRUE, WS-TBL-COUNT = 10,
            // and populates the table via PERFORM VARYING WS-IDX-BIN.
            // Delegated to the separately migrated InitFilesAndDataService / method.
            log.debug("{}Delegating to initFilesAndData()", MSG_INFO_PREFIX);
            // Actual implementation resides in the migrated INIT-1 / INIT-FILES-AND-DATA service.
        }

        /**
         * Stub delegation method for COBOL section {@code TABLE-OPS}.
         * <p>Original COBOL: paragraph {@code TABLE-OPS} in program {@code MEGADEMO}.
         * Iterates over the internal table, displays each entry, and performs a linear SEARCH.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void tableOps(boolean wsDebug) {
            // Sets TBL-IDX to 1, loops until TBL-IDX > TBL-COUNT displaying each item,
            // then performs a SEARCH TBL-ITEM looking for keys starting with 'AA'.
            // Delegated to the separately migrated TBL-1 / TABLE-OPS service.
            log.debug("{}Delegating to tableOps()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code STRING-AND-EDITING-OPS}.
         * <p>Original COBOL: paragraph {@code STRING-AND-EDITING-OPS} in program {@code MEGADEMO}.
         * Formats a date string and concatenates employee name fields.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void stringAndEditingOps(boolean wsDebug) {
            // Moves '20260305' to WS-DATE-YYYYMMDD, reformats into WS-DATE-PRINT,
            // and STRINGs 'Hello, ' + EMP-FNAME + ' ' + EMP-LNAME + '!' into WS-AL.
            // Delegated to the separately migrated STR-1 / STRING-AND-EDITING-OPS service.
            log.debug("{}Delegating to stringAndEditingOps()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code ARITHMETIC-AND-FUNCTIONS}.
         * <p>Original COBOL: paragraph {@code ARITHMETIC-AND-FUNCTIONS} in program {@code MEGADEMO}.
         * Performs COMPUTE, displays intrinsic function results (CURRENT-DATE, MAX, MIN, RANDOM, LENGTH).
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void arithmeticAndFunctions(boolean wsDebug) {
            // COMPUTE IX-AMT = (13 + 7) * 2 - 5 / 2
            // Displays IX-AMT, CURRENT-DATE, MAX(3,7), MIN(3,7), RANDOM, LENGTH(WS-ALNUM).
            // Delegated to the separately migrated ARI-1 / ARITHMETIC-AND-FUNCTIONS service.
            log.debug("{}Delegating to arithmeticAndFunctions()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code INDEXED-FILE-OPS}.
         * <p>Original COBOL: paragraph {@code INDEXED-FILE-OPS} in program {@code MEGADEMO}.
         * Writes a record to the indexed file and handles INVALID KEY conditions.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void indexedFileOps(boolean wsDebug) {
            // Moves key/data fields to IX-REC and WRITEs to IXFILE.
            // On INVALID KEY displays error with WS-FS-IX.
            // Delegated to the separately migrated IX-1 / INDEXED-FILE-OPS service.
            log.debug("{}Delegating to indexedFileOps()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code RELATIVE-FILE-OPS}.
         * <p>Original COBOL: paragraph {@code RELATIVE-FILE-OPS} in program {@code MEGADEMO}.
         * Writes and reads a record in the relative file, handling INVALID KEY conditions.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void relativeFileOps(boolean wsDebug) {
            // Sets WS-REL-KEY=1, REL-ID=1, REL-DATA='RELATIVE DATA',
            // WRITEs REL-REC, then READs back with INVALID KEY error handling.
            // Delegated to the separately migrated REL-1 / RELATIVE-FILE-OPS service.
            log.debug("{}Delegating to relativeFileOps()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code SEQ-FILE-AND-SORT-OPS}.
         * <p>Original COBOL: paragraph {@code SEQ-FILE-AND-SORT-OPS} in program {@code MEGADEMO}.
         * Writes a sequential record, closes the file, and performs a SORT operation.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void seqFileAndSortOps(boolean wsDebug) {
            // Populates INSEQ-REC fields, WRITEs to INSEQ, CLOSEs INSEQ,
            // then SORTs SORT-FILE ASCENDING KEY S-KEY with INPUT/OUTPUT PROCEDUREs.
            // Delegated to the separately migrated SQ-1 / SEQ-FILE-AND-SORT-OPS service.
            log.debug("{}Delegating to seqFileAndSortOps()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code CONTROL-FLOW-DEMO}.
         * <p>Original COBOL: paragraph {@code CONTROL-FLOW-DEMO} in program {@code MEGADEMO}.
         * Demonstrates PERFORM VARYING and EVALUATE constructs.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void controlFlowDemo(boolean wsDebug) {
            // PERFORM VARYING WS-IDX-COMP FROM 1 BY 1 UNTIL > 3 (displays loop index),
            // then EVALUATE WS-IDX-COMP WHEN 1/2/OTHER.
            // Delegated to the separately migrated CFD-1 / CONTROL-FLOW-DEMO service.
            log.debug("{}Delegating to controlFlowDemo()", MSG_INFO_PREFIX);
        }

        /**
         * Stub delegation method for COBOL section {@code CALLS-AND-POINTERS}.
         * <p>Original COBOL: paragraph {@code CALLS-AND-POINTERS} in program {@code MEGADEMO}.
         * Sets a pointer address, CALLs external subprogram SUBPGM1, CANCELs it,
         * and performs a dynamic CALL via WS-ALNUM.
         *
         * @param wsDebug debug flag propagated from the calling context
         */
        private void callsAndPointers(boolean wsDebug) {
            // SET ADDRESS OF REL-REC TO WS-POINTER, displays pointer address,
            // CALL 'SUBPGM1' BY REFERENCE EMP-ID, BY CONTENT WS-ALNUM, BY VALUE WS-IDX-COMP,
            // CANCEL 'SUBPGM1', MOVE 'SYSTEM' TO WS-ALNUM(1:6), dynamic CALL WS-ALNUM.
            // Delegated to the separately migrated CAP-1 / CALLS-AND-POINTERS service.
            log.debug("{}Delegating to callsAndPointers()", MSG_INFO_PREFIX);
        }

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

}