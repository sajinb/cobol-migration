package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service migrated from COBOL program {@code MEGADEMO}.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Implements the logic of COBOL paragraph {@code CFD-1} in program {@code MEGADEMO}.
     *
     * <p>The paragraph:
     * <ol>
     *   <li>Iterates WS-IDX-COMP from 1 to 3 (inclusive), logging each iteration value.</li>
     *   <li>Sets WS-IDX-COMP to 2 and evaluates it, logging the matching branch.</li>
     *   <li>Transfers control to {@code DEPENDING-ON-DEMO} (modelled as a method call).</li>
     * </ol>
     *
     * <p>Original COBOL: paragraph {@code CFD-1} in program {@code MEGADEMO}.
     *
     * @return the final value of WS-IDX-COMP after the paragraph completes
     */
    public int cfd1() {

        // PERFORM VARYING WS-IDX-COMP FROM 1 BY 1 UNTIL WS-IDX-COMP > 3
        int wsIdxComp = 1;
        while (wsIdxComp <= 3) {
            log.info("PERFORM VARYING i={}", wsIdxComp);
            wsIdxComp++;
        }

        // MOVE 2 TO WS-IDX-COMP
        wsIdxComp = 2;

        // EVALUATE WS-IDX-COMP
        switch (wsIdxComp) {
            case 1:
                log.info("EVAL: one");
                break;
            case 2:
                log.info("EVAL: two");
                break;
            default:
                log.info("EVAL: other");
                break;
        }

        // GO TO DEPENDING-ON-DEMO  — modelled as a delegating method call
        dependingOnDemo();

        return wsIdxComp;
    }

    /**
     * Stub target for the {@code GO TO DEPENDING-ON-DEMO} transfer of control.
     *
     * <p>Original COBOL: paragraph/section {@code DEPENDING-ON-DEMO} in program {@code MEGADEMO}.
     * Replace this body with the fully migrated implementation of that paragraph/section.
     */
    public void dependingOnDemo() {
        log.info("Entered DEPENDING-ON-DEMO");
        // Full implementation of DEPENDING-ON-DEMO paragraph goes here.
    }
}