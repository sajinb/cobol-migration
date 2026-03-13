```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph INIT-1.
 *
 * <p>Initialises the sequential, indexed, and relative files for I/O, then
 * populates two in-memory table structures:
 * <ol>
 *   <li>A general-purpose table (WS-TBL) of 10 entries whose keys are built
 *       by concatenating the literal "KEY" with the 1-based index, and whose
 *       values are the index multiplied by 13.37.</li>
 *   <li>A named-record area (RN-A … RN-D) initialised with fixed string
 *       literals.</li>
 *   <li>A second item table (TBL-ITEM) of 5 slots, with the first two slots
 *       pre-loaded with hard-coded key/amount pairs.</li>
 * </ol>
 *
 * <p>Corresponds to the COBOL logic:
 * <pre>
 *   OPEN OUTPUT INSEQ
 *   OPEN I-O IXFILE / RELFILE
 *   IF WS-FS-IX NOT = '00' → re-create IXFILE
 *   MOVE / PERFORM / STRING / COMPUTE to fill WS-TBL (10 rows)
 *   Initialise RN-A..D and TBL-ITEM (2 pre-loaded rows)
 * </pre>
 *
 * @param wsFileStatusIx  the file-status code for IXFILE (WS-FS-IX); when not
 *                        "00" the indexed file is recreated before being
 *                        opened for I-O.
 * @return an {@link InitResult} value object that carries every piece of state
 *         that was written by this paragraph and is needed by subsequent
 *         paragraphs.
 */
@Transactional
public InitResult init1(String wsFileStatusIx) {

    // ------------------------------------------------------------------
    // File-open simulation
    // OPEN OUTPUT INSEQ  → truncate / create the sequential output stream
    // OPEN I-O IXFILE    → open indexed file for read-write
    // OPEN I-O RELFILE   → open relative file for read-write
    // ------------------------------------------------------------------
    inseqFileService.openOutput();
    ixFileService.openIO();
    relFileService.openIO();

    // IF WS-FS-IX NOT = '00'
    //    OPEN OUTPUT IXFILE   (recreate / initialise the file)
    //    CLOSE IXFILE
    //    OPEN I-O IXFILE
    if (!"00".equals(wsFileStatusIx)) {
        ixFileService.openOutput();   // recreate
        ixFileService.close();
        ixFileService.openIO();       // reopen for I-O
    }

    // ------------------------------------------------------------------
    // WS-FLAG-AREA = 'Y'  /  FLAG-YES = TRUE
    // ------------------------------------------------------------------
    String wsFlagArea = "Y";
    boolean flagYes   = true;

    // ------------------------------------------------------------------
    // Build WS-TBL: 10 rows
    //   WS-TBL-KEY(i) = "KEY" + i
    //   WS-TBL-VAL(i) = i * 13.37
    // ------------------------------------------------------------------
    int wsTblCount = 10;
    String[]     wsTblKey = new String[wsTblCount];
    BigDecimal[] wsTblVal = new BigDecimal[wsTblCount];

    for (int wsIdxBin = 1; wsIdxBin <= wsTblCount; wsIdxBin++) {
        int tableIndex = wsIdxBin - 1;          // 0-based Java array

        // STRING 'KEY' DELIMITED BY SIZE
        //        WS-IDX-BIN DELIMITED BY SIZE
        //        INTO WS-TBL-KEY(WS-TBL-IDX)
        wsTblKey[tableIndex] = "KEY" + wsIdxBin;

        // COMPUTE WS-TBL-VAL(WS-TBL-IDX) = WS-IDX-BIN * 13.37
        wsTblVal[tableIndex] = BigDecimal.valueOf(wsIdxBin)
                .multiply(new BigDecimal("13.37"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ------------------------------------------------------------------
    // Named-record area literals
    // ------------------------------------------------------------------
    String rnA = "ABC";
    String rnB = "DEF";
    String rnC = "GHI";
    String rnD = "JKL";

    // ------------------------------------------------------------------
    // TBL-ITEM: 5 slots, first two pre-loaded
    // ------------------------------------------------------------------
    int tblCount = 5;
    String[]     tblItemKey = new String[tblCount];
    BigDecimal[] tblItemAmt = new BigDecimal[tblCount];

    tblItemKey[0] = "AA00000001";
    tblItemAmt[0] = new BigDecimal("100.50");

    tblItemKey[1] = "AA00000002";
    tblItemAmt[1] = new BigDecimal("200.75");

    // ------------------------------------------------------------------
    // Return all written state to the caller
    // ------------------------------------------------------------------
    return InitResult.builder()
            .wsFlagArea(wsFlagArea)
            .flagYes(flagYes)
            .wsTblCount(wsTblCount)
            .wsTblKey(wsTblKey)
            .wsTblVal(wsTblVal)
            .rnA(rnA)
            .rnB(rnB)
            .rnC(rnC)
            .rnD(rnD)
            .tblCount(tblCount)
            .tblItemKey(tblItemKey)
            .tblItemAmt(tblItemAmt)
            .build();
}
```