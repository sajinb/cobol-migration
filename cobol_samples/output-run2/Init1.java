package com.migration.megademo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

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
}