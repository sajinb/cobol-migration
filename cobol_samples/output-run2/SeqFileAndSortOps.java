package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * This class handles sequential file and sort operations originally
 * implemented in the COBOL SECTION {@code SEQ-FILE-AND-SORT-OPS} and
 * its body paragraph {@code SQ-1}.
 */
@Slf4j
@Service
public class MegademoService {

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
}