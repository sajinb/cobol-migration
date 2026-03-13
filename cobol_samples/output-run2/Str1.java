package com.migration.megademo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

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
}