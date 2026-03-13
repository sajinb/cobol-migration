package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service class migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * Contains logic for the {@code STRING-AND-EDITING-OPS} section and its
 * subordinate paragraph {@code STR-1}, which performs date reformatting and
 * string concatenation operations.
 */
@Slf4j
@Service
public class MegademoService {

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
}