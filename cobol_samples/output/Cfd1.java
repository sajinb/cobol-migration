```java
/**
 * Migrates COBOL paragraph CFD-1 from program MEGADEMO.
 *
 * <p>Business Logic:
 * <ol>
 *   <li>Iterates a counter (WS-IDX-COMP) from 1 to 3 (inclusive), logging each iteration value.</li>
 *   <li>Sets the counter to 2 and evaluates it against known values (1, 2, or other),
 *       logging a corresponding label.</li>
 *   <li>Transfers control to the DEPENDING-ON-DEMO paragraph (modelled as a method call).</li>
 * </ol>
 *
 * <p>Original COBOL paragraph: CFD-1
 * <p>Original COBOL program  : MEGADEMO
 *
 * @return the final value of WS-IDX-COMP after processing
 */
public int cfd1() {

    // PERFORM VARYING WS-IDX-COMP FROM 1 BY 1 UNTIL WS-IDX-COMP > 3
    int wsIdxComp;
    for (wsIdxComp = 1; wsIdxComp <= 3; wsIdxComp++) {
        System.out.println("PERFORM VARYING i=" + wsIdxComp);
    }
    // After the loop WS-IDX-COMP holds the value that broke the condition (4),
    // but COBOL then immediately overwrites it with MOVE 2.

    // MOVE 2 TO WS-IDX-COMP
    wsIdxComp = 2;

    // EVALUATE WS-IDX-COMP
    switch (wsIdxComp) {
        case 1:
            System.out.println("EVAL: one");
            break;
        case 2:
            System.out.println("EVAL: two");
            break;
        default:
            System.out.println("EVAL: other");
            break;
    }

    // GO TO DEPENDING-ON-DEMO  — transfer of control to the next paragraph
    dependingOnDemo();

    return wsIdxComp;
}
```