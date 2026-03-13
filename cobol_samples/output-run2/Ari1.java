package com.migration.megademo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

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
}