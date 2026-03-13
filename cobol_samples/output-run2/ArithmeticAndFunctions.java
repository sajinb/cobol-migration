package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * Contains the migration of the {@code ARITHMETIC-AND-FUNCTIONS} section and
 * its body paragraph {@code ARI-1}.
 */
@Slf4j
@Service
public class MegademoService {

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
}