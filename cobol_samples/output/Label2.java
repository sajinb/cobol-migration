```java
/**
 * Migrates COBOL paragraph LABEL-2 from program MEGADEMO.
 *
 * <p>Original behaviour:
 * <ol>
 *   <li>Displays the literal message "GO TO DEPENDING ON -> 2" to standard output.</li>
 *   <li>Unconditionally transfers control to paragraph CFD-EXIT via a GO TO statement.</li>
 * </ol>
 *
 * <p>Migration notes:
 * <ul>
 *   <li>The {@code DISPLAY} statement is mapped to a {@code log.info()} call (and a
 *       {@code System.out.println} for fidelity with the original console output).</li>
 *   <li>The {@code GO TO CFD-EXIT} is modelled as a call to {@link #cfdExit()}, which
 *       must exist in the same service class.  Because COBOL GO TO transfers control
 *       without returning, execution of this method ends immediately after that call —
 *       a {@code return} statement is placed after it to make the intent explicit.</li>
 * </ul>
 */
public void label2() {
    // DISPLAY 'GO TO DEPENDING ON -> 2'
    System.out.println("GO TO DEPENDING ON -> 2");
    log.info("GO TO DEPENDING ON -> 2");

    // GO TO CFD-EXIT  — unconditional branch; delegate and return immediately
    cfdExit();
}
```