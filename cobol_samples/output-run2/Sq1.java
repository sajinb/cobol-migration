package com.migration.megademo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

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
}