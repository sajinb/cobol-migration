```java
/**
 * Migrates COBOL paragraph LABEL-1 from program MEGADEMO.
 *
 * <p>Original behaviour:
 * <ol>
 *   <li>Displays the literal message "GO TO DEPENDING ON -> 1" to standard output.</li>
 *   <li>Unconditionally transfers control to paragraph CFD-EXIT via a GO TO statement.</li>
 * </ol>
 *
 * <p>Migration notes:
 * <ul>
 *   <li>The {@code DISPLAY} statement is mapped to a {@code log.info()} call (or
 *       {@code System.out.println} if a logger is not available in context).</li>
 *   <li>The unconditional {@code GO TO CFD-EXIT} is modelled by invoking the
 *       {@code cfdExit()} method and returning immediately, which faithfully
 *       replicates the "fall-through to exit" semantics of the original paragraph.</li>
 * </ul>
 */
public void label1() {
    // DISPLAY 'GO TO DEPENDING ON -> 1'
    log.info("GO TO DEPENDING ON -> 1");

    // GO TO CFD-EXIT  — unconditional branch; delegate and return immediately
    cfdExit();
}
```