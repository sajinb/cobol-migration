```java
/**
 * Migrates the COBOL paragraph DEPENDING-ON-DEMO from program MEGADEMO.
 *
 * <p>Original logic:
 * <pre>
 * DEPENDING-ON-DEMO.
 *     MOVE 3 TO WS-IDX-COMP
 *     GO TO LABEL-1 LABEL-2 LABEL-3 DEPENDING ON WS-IDX-COMP.
 * </pre>
 *
 * The paragraph sets WS-IDX-COMP to 3 and then performs a computed GO TO,
 * branching to one of three labels (LABEL-1, LABEL-2, LABEL-3) based on the
 * value of WS-IDX-COMP. Since WS-IDX-COMP is always 3 here, control always
 * transfers to LABEL-3. In COBOL, a "GO TO ... DEPENDING ON" with index N
 * branches to the Nth label in the list (1-based). If the index is out of
 * range (< 1 or > number of labels) execution falls through; here N=3 maps
 * to LABEL-3.
 *
 * <p>In Java this is modelled as a switch/case dispatch. Each label is
 * represented by a call to the corresponding service method. The method
 * returns the label name that was dispatched to, allowing callers to
 * understand which branch was taken.
 *
 * @return the name of the label that was branched to ("LABEL-1", "LABEL-2",
 *         "LABEL-3"), or {@code "FALL-THROUGH"} if wsIdxComp is out of range.
 */
public String dependingOnDemo() {

    // MOVE 3 TO WS-IDX-COMP
    int wsIdxComp = 3;

    // GO TO LABEL-1 LABEL-2 LABEL-3 DEPENDING ON WS-IDX-COMP
    // COBOL computed GO TO is 1-based: index 1 → LABEL-1, 2 → LABEL-2, 3 → LABEL-3
    switch (wsIdxComp) {
        case 1:
            label1();
            return "LABEL-1";

        case 2:
            label2();
            return "LABEL-2";

        case 3:
            label3();
            return "LABEL-3";

        default:
            // COBOL falls through silently when index is out of range
            return "FALL-THROUGH";
    }
}
```