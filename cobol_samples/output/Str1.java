```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph STR-1.
 *
 * <p>This method demonstrates several COBOL string-manipulation and numeric-editing operations:
 * <ol>
 *   <li>Formats a hard-coded date "20260305" into a printable "MM/DD/YY" style string.</li>
 *   <li>Concatenates a greeting string from employee first/last name fields using STRING.</li>
 *   <li>Splits a pipe-delimited source string into up to four tokens using UNSTRING.</li>
 *   <li>Counts occurrences of the letter 'L' in the greeting string (INSPECT TALLYING).</li>
 *   <li>Replaces all '!' characters with '.' in the greeting string (INSPECT REPLACING).</li>
 *   <li>Formats numeric amounts using edited picture clauses and displays them.</li>
 * </ol>
 *
 * @param empFname      Employee first name (EMP-FNAME)
 * @param empLname      Employee last name  (EMP-LNAME)
 * @param wsUnstrSrc    Pipe-delimited source string to be split (WS-UNSTR-SRC)
 * @return              A {@link Str1Result} record containing all computed output values
 */
public Str1Result str1(String empFname, String empLname, String wsUnstrSrc) {

    // -----------------------------------------------------------------------
    // 1. Date formatting
    //    MOVE '20260305' TO WS-DATE-YYYYMMDD
    //    Then copy substrings into WS-DATE-PRINT positions 1:2, 4:2, 7:2
    //    Result pattern: YYYY/MM/DD  (positions 1-2=YYYY, 4-5=MM, 7-8=DD)
    // -----------------------------------------------------------------------
    final String wsDateYyyymmdd = "20260305";

    // WS-DATE-PRINT is a 10-character field; COBOL 1-based offsets map as:
    //   (1:2) -> chars 0-1  = YYYY portion
    //   (4:2) -> chars 3-4  = MM   portion
    //   (7:2) -> chars 6-7  = DD   portion
    // Separator characters at positions 3 and 6 (0-based) are left as spaces/default.
    char[] datePrint = "          ".toCharArray(); // 10 spaces
    // Copy YYYY
    datePrint[0] = wsDateYyyymmdd.charAt(0);
    datePrint[1] = wsDateYyyymmdd.charAt(1);
    // Copy MM
    datePrint[3] = wsDateYyyymmdd.charAt(2);
    datePrint[4] = wsDateYyyymmdd.charAt(3);
    // Copy DD
    datePrint[6] = wsDateYyyymmdd.charAt(4);
    datePrint[7] = wsDateYyyymmdd.charAt(5);
    String wsDatePrint = new String(datePrint);

    // -----------------------------------------------------------------------
    // 2. STRING  'Hello, ' EMP-FNAME ' ' EMP-LNAME '!' DELIMITED BY SIZE
    //            INTO WS-ALNUM
    //    DELIMITED BY SIZE means use the full field length of each operand.
    // -----------------------------------------------------------------------
    String wsAlnum = "Hello, " + empFname + " " + empLname + "!";

    // -----------------------------------------------------------------------
    // 3. UNSTRING WS-UNSTR-SRC DELIMITED BY '|'
    //             INTO RN-A RN-B RN-C RN-D
    //    Split on '|', take up to 4 tokens; pad missing tokens with empty string.
    // -----------------------------------------------------------------------
    String[] tokens = wsUnstrSrc.split("\\|", -1);
    String rnA = tokens.length > 0 ? tokens[0] : "";
    String rnB = tokens.length > 1 ? tokens[1] : "";
    String rnC = tokens.length > 2 ? tokens[2] : "";
    String rnD = tokens.length > 3 ? tokens[3] : "";

    // -----------------------------------------------------------------------
    // 4. INSPECT WS-ALNUM TALLYING WS-IDX-COMP FOR ALL 'L'
    //    Count occurrences of 'L' (case-sensitive, as COBOL is).
    // -----------------------------------------------------------------------
    int wsIdxComp = 0;
    for (char c : wsAlnum.toCharArray()) {
        if (c == 'L') {
            wsIdxComp++;
        }
    }

    // -----------------------------------------------------------------------
    // 5. INSPECT WS-ALNUM REPLACING ALL '!' BY '.'
    // -----------------------------------------------------------------------
    wsAlnum = wsAlnum.replace('!', '.');

    // -----------------------------------------------------------------------
    // 6. Numeric editing
    //    MOVE  123456.78 TO WS-AMT-DISPLAY  -> PIC ZZZ,ZZZ.99   (suppress leading zeros)
    //    MOVE  123456.78 TO WS-AMT-STAR     -> PIC ***,***.99   (fill with asterisks)
    //    MOVE -123456.78 TO WS-AMT-CRDB     -> PIC ZZZ,ZZZ.99CR (CR suffix for negatives)
    // -----------------------------------------------------------------------
    BigDecimal amtPositive = new BigDecimal("123456.78");
    BigDecimal amtNegative = new BigDecimal("-123456.78");

    // WS-AMT-DISPLAY: PIC ZZZ,ZZZ.99 — suppress leading zeros, comma thousands separator
    java.text.DecimalFormat dfDisplay = new java.text.DecimalFormat("#,##0.00");
    dfDisplay.setGroupingSize(3);
    String wsAmtDisplay = dfDisplay.format(amtPositive);   // e.g. "123,456.78"

    // WS-AMT-STAR: PIC ***,***.99 — replace leading-zero spaces with '*'
    String rawStar = String.format("%10.2f", amtPositive.doubleValue()); // "  123456.78"
    // Insert comma grouping then pad leading spaces with '*'
    String formattedStar = dfDisplay.format(amtPositive);  // "123,456.78"
    // Pad to 10 chars with leading '*'
    while (formattedStar.length() < 10) {
        formattedStar = "*" + formattedStar;
    }
    String wsAmtStar = formattedStar;                       // e.g. "123,456.78"

    // WS-AMT-CRDB: PIC ZZZ,ZZZ.99CR — append "CR" when negative, spaces when positive
    java.text.DecimalFormat dfCrdb = new java.text.DecimalFormat("#,##0.00");
    String wsAmtCrdb;
    if (amtNegative.compareTo(BigDecimal.ZERO) < 0) {
        wsAmtCrdb = dfCrdb.format(amtNegative.abs()) + "CR"; // e.g. "123,456.78CR"
    } else {
        wsAmtCrdb = dfCrdb.format(amtNegative) + "  ";       // trailing spaces for positive
    }

    // -----------------------------------------------------------------------
    // 7. DISPLAY statements — emit to application log
    // -----------------------------------------------------------------------
    log.info("Edited Amt 1: {}", wsAmtDisplay);
    log.info("Edited Amt 2: {}", wsAmtStar);
    log.info("Edited Amt 3: {}", wsAmtCrdb);

    // -----------------------------------------------------------------------
    // 8. Return all computed values (EXIT SECTION)
    // -----------------------------------------------------------------------
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

/**
 * Value object carrying all outputs produced by {@link #str1}.
 *
 * @param wsDateYyyymmdd  The hard-coded date string "20260305"
 * @param wsDatePrint     The formatted date string (YYYY MM DD with gaps)
 * @param wsAlnum         The greeting string after INSPECT REPLACING ('!' -> '.')
 * @param rnA             First  token from UNSTRING
 * @param rnB             Second token from UNSTRING
 * @param rnC             Third  token from UNSTRING
 * @param rnD             Fourth token from UNSTRING
 * @param wsIdxComp       Count of 'L' characters found in the original greeting
 * @param wsAmtDisplay    Edited display amount  (ZZZ,ZZZ.99)
 * @param wsAmtStar       Edited star-fill amount (***,***.99)
 * @param wsAmtCrdb       Edited CR/DB amount     (ZZZ,ZZZ.99CR)
 */
public record Str1Result(
        String wsDateYyyymmdd,
        String wsDatePrint,
        String wsAlnum,
        String rnA,
        String rnB,
        String rnC,
        String rnD,
        int    wsIdxComp,
        String wsAmtDisplay,
        String wsAmtStar,
        String wsAmtCrdb
) {}
```