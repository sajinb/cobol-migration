```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph SQ-1.
 *
 * <p>Original behaviour:
 * <ol>
 *   <li>Populates a sequential-file record (INSEQ-REC) with fixed seed values:
 *       id=1, name="John Doe", amount=9999.99, date=20260305,
 *       comment="Sample record".</li>
 *   <li>Writes the record to the INSEQ sequential file and closes it.</li>
 *   <li>Sorts SORT-FILE on ascending S-KEY, feeding records via
 *       SORT-IN-PROC and consuming sorted output via SORT-OUT-PROC.</li>
 * </ol>
 *
 * <p>Migration notes:
 * <ul>
 *   <li>File I/O is replaced by a call to {@code inseqFileService} (write + close).</li>
 *   <li>The SORT verb is replaced by an in-memory sort delegated to
 *       {@code sortInProc()} / {@code sortOutProc()} helper methods that
 *       correspond to the original INPUT/OUTPUT PROCEDURE sections.</li>
 *   <li>INSEQ-ID and INSEQ-DATE are returned as part of {@link InseqRecord}
 *       because they cross paragraph boundaries (written here, potentially
 *       read elsewhere).</li>
 * </ul>
 *
 * @return the {@link InseqRecord} that was written to the sequential file,
 *         exposing the values of INSEQ-ID and INSEQ-DATE for downstream use.
 */
@Transactional
public InseqRecord sq1() {

    // --- Populate INSEQ-REC fields (MOVE statements) ---
    InseqRecord inseqRec = new InseqRecord();
    inseqRec.setInseqId(1);                                  // MOVE 1          TO INSEQ-ID
    inseqRec.setInseqName("John Doe");                       // MOVE 'John Doe' TO INSEQ-NAME
    inseqRec.setInseqAmt(new BigDecimal("9999.99"));         // MOVE 9999.99    TO INSEQ-AMT
    inseqRec.setInseqDate(20260305);                         // MOVE 20260305   TO INSEQ-DATE
    inseqRec.setInseqComment("Sample record");               // MOVE 'Sample record' TO INSEQ-COMMENT

    // --- WRITE INSEQ-REC ---
    inseqFileService.writeRecord(inseqRec);

    // --- CLOSE INSEQ ---
    inseqFileService.closeFile();

    // --- SORT SORT-FILE ON ASCENDING KEY S-KEY
    //         INPUT  PROCEDURE IS SORT-IN-PROC
    //         OUTPUT PROCEDURE IS SORT-OUT-PROC ---
    //
    // 1. Collect records to be sorted via the input procedure.
    List<SortRecord> recordsToSort = sortInProc();

    // 2. Sort ascending on S-KEY (mirrors "ON ASCENDING KEY S-KEY").
    List<SortRecord> sortedRecords = recordsToSort.stream()
            .sorted(Comparator.comparing(SortRecord::getSKey))
            .collect(Collectors.toList());

    // 3. Deliver sorted records via the output procedure.
    sortOutProc(sortedRecords);

    // Return the written record so callers can access INSEQ-ID / INSEQ-DATE.
    return inseqRec;
}
```