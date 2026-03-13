```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph ARI-1.
 *
 * <p>Demonstrates a variety of COBOL intrinsic functions and an EVALUATE statement:
 * <ul>
 *   <li>COMPUTE: arithmetic expression (13 + 7) * 2 - 5 / 2 (integer arithmetic)</li>
 *   <li>CURRENT-DATE: current date/time</li>
 *   <li>MAX / MIN of two literals</li>
 *   <li>RANDOM: a pseudo-random number</li>
 *   <li>LENGTH of a working-storage alphanumeric field (passed as parameter)</li>
 *   <li>UPPER-CASE / LOWER-CASE string conversion</li>
 *   <li>INTEGER-OF-DATE: converts a Gregorian date (YYYYMMDD) to an integer day count</li>
 *   <li>DATE-OF-INTEGER: converts an integer day count back to a Gregorian date (YYYYMMDD)</li>
 *   <li>EVALUATE on EMP-GRADE with WHEN 'A' and WHEN OTHER branches</li>
 * </ul>
 *
 * <p>COBOL integer arithmetic note: "5 / 2" in COBOL integer context yields 2 (truncated),
 * so the full expression (13 + 7) * 2 - 5 / 2 = 40 - 2 = 38.
 *
 * @param wsAlnum  the WS-ALNUM working-storage field (used for LENGTH intrinsic)
 * @param empGrade the EMP-GRADE working-storage field (used in EVALUATE)
 * @return the computed IX-AMT value
 */
public int ari1(String wsAlnum, String empGrade) {

    // COMPUTE IX-AMT = (13 + 7) * 2 - 5 / 2
    // COBOL integer division truncates toward zero: 5 / 2 = 2
    int ixAmt = (13 + 7) * 2 - (5 / 2);   // = 40 - 2 = 38
    System.out.println("COMPUTE IX-AMT=" + ixAmt);

    // DISPLAY 'CURRENT-DATE=' FUNCTION CURRENT-DATE
    // COBOL CURRENT-DATE returns a 21-char string: YYYYMMDDHHmmsscc+HHmm
    java.time.ZonedDateTime now = java.time.ZonedDateTime.now();
    java.time.format.DateTimeFormatter cobolDateFmt =
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss00xxxx");
    String currentDate = now.format(cobolDateFmt);
    System.out.println("CURRENT-DATE=" + currentDate);

    // DISPLAY 'MAX(3,7)=' FUNCTION MAX(3 7)
    int maxVal = Math.max(3, 7);
    System.out.println("MAX(3,7)=" + maxVal);

    // DISPLAY 'MIN(3,7)=' FUNCTION MIN(3 7)
    int minVal = Math.min(3, 7);
    System.out.println("MIN(3,7)=" + minVal);

    // DISPLAY 'RANDOM=' FUNCTION RANDOM
    // COBOL RANDOM returns a value in [0, 1)
    double randomVal = Math.random();
    System.out.println("RANDOM=" + randomVal);

    // DISPLAY 'LENGTH(WS-ALNUM)=' FUNCTION LENGTH(WS-ALNUM)
    // COBOL LENGTH returns the declared storage length of the field.
    // Since the declared size is not available at runtime in Java, we use the
    // actual string length as the closest equivalent.
    int lengthWsAlnum = (wsAlnum != null) ? wsAlnum.length() : 0;
    System.out.println("LENGTH(WS-ALNUM)=" + lengthWsAlnum);

    // DISPLAY 'UPPER-CASE=' FUNCTION UPPER-CASE('abc')
    String upperCase = "abc".toUpperCase();
    System.out.println("UPPER-CASE=" + upperCase);

    // DISPLAY 'LOWER-CASE=' FUNCTION LOWER-CASE('ABC')
    String lowerCase = "ABC".toLowerCase();
    System.out.println("LOWER-CASE=" + lowerCase);

    // DISPLAY 'INTEGER-OF-DATE(20260305)=' FUNCTION INTEGER-OF-DATE(20260305)
    // COBOL INTEGER-OF-DATE counts days from the base date 1601-01-01.
    // Java LocalDate epoch is 1970-01-01; we adjust accordingly.
    java.time.LocalDate cobolBaseDate = java.time.LocalDate.of(1601, 1, 1);
    java.time.LocalDate targetDate    = java.time.LocalDate.of(2026, 3, 5);
    long integerOfDate = java.time.temporal.ChronoUnit.DAYS.between(cobolBaseDate, targetDate) + 1;
    System.out.println("INTEGER-OF-DATE(20260305)=" + integerOfDate);

    // DISPLAY 'DATE-OF-INTEGER(75000)=' FUNCTION DATE-OF-INTEGER(75000)
    // Reverse: add (n-1) days to the COBOL base date 1601-01-01
    java.time.LocalDate dateOfInteger = cobolBaseDate.plusDays(75000L - 1);
    // Format as YYYYMMDD to match COBOL output
    String dateOfIntegerStr = dateOfInteger.format(
            java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
    System.out.println("DATE-OF-INTEGER(75000)=" + dateOfIntegerStr);

    // EVALUATE EMP-GRADE
    //   WHEN 'A'  -> DISPLAY 'EVALUATE: Grade A'
    //   WHEN OTHER -> DISPLAY 'EVALUATE: default path'
    // END-EVALUATE
    if ("A".equals(empGrade)) {
        System.out.println("EVALUATE: Grade A");
    } else {
        System.out.println("EVALUATE: default path");
    }

    // EXIT SECTION — return the computed IX-AMT value
    return ixAmt;
}
```