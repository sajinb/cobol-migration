package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Implements the DEPENDING-ON-DEMO paragraph logic.
     * <p>
     * The original COBOL sets WS-IDX-COMP to 3, then performs a computed GO TO
     * branching to LABEL-1, LABEL-2, or LABEL-3 depending on the value of
     * WS-IDX-COMP. Since WS-IDX-COMP is always set to 3 immediately before the
     * branch, control always transfers to LABEL-3.
     * <p>
     * In Java the computed GO TO is modelled as a switch statement on wsIdxComp.
     * Each case calls the corresponding label method. Values outside the range
     * 1–3 (which would cause the COBOL GO TO to fall through) are handled by
     * the default branch, which logs a warning and takes no action.
     * <p>
     * Original COBOL: paragraph {@code DEPENDING-ON-DEMO} in program {@code MEGADEMO}.
     *
     * @return the value of WS-IDX-COMP after the paragraph executes (always 3
     *         when called with no arguments, matching the COBOL behaviour)
     */
    public int dependingOnDemo() {

        // MOVE 3 TO WS-IDX-COMP
        int wsIdxComp = 3;

        // GO TO LABEL-1 LABEL-2 LABEL-3 DEPENDING ON WS-IDX-COMP
        // COBOL computed GO TO: branch to the Nth label where N == wsIdxComp.
        // Values < 1 or > number-of-labels cause the statement to be ignored.
        switch (wsIdxComp) {
            case 1:
                label1();
                break;
            case 2:
                label2();
                break;
            case 3:
                label3();
                break;
            default:
                // COBOL GO TO … DEPENDING ON falls through (no branch) when the
                // index is outside the valid range — log and continue.
                log.warn("DEPENDING-ON-DEMO: wsIdxComp={} is outside the valid range 1-3; "
                        + "GO TO DEPENDING ON has no effect.", wsIdxComp);
                break;
        }

        return wsIdxComp;
    }

    /**
     * Corresponds to COBOL label LABEL-1.
     * <p>
     * Placeholder implementation — replace with the migrated body of LABEL-1
     * once that paragraph is available.
     * <p>
     * Original COBOL: paragraph {@code LABEL-1} in program {@code MEGADEMO}.
     */
    public void label1() {
        log.info("DEPENDING-ON-DEMO: branched to LABEL-1 (wsIdxComp=1)");
    }

    /**
     * Corresponds to COBOL label LABEL-2.
     * <p>
     * Placeholder implementation — replace with the migrated body of LABEL-2
     * once that paragraph is available.
     * <p>
     * Original COBOL: paragraph {@code LABEL-2} in program {@code MEGADEMO}.
     */
    public void label2() {
        log.info("DEPENDING-ON-DEMO: branched to LABEL-2 (wsIdxComp=2)");
    }

    /**
     * Corresponds to COBOL label LABEL-3.
     * <p>
     * This is the branch that is always taken when dependingOnDemo() is called
     * with no arguments, because WS-IDX-COMP is unconditionally set to 3.
     * <p>
     * Original COBOL: paragraph {@code LABEL-3} in program {@code MEGADEMO}.
     */
    public void label3() {
        log.info("DEPENDING-ON-DEMO: branched to LABEL-3 (wsIdxComp=3)");
    }
}