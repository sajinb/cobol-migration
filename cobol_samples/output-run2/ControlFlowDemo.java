package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * Contains the migration of the {@code CONTROL-FLOW-DEMO} section entry point
 * and its body paragraph {@code CFD-1}.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Entry point for the {@code CONTROL-FLOW-DEMO} COBOL section.
     * Delegates immediately to {@link #cfd1()}, which contains the section body.
     * <p>Original COBOL: section {@code CONTROL-FLOW-DEMO} in program {@code MEGADEMO}.
     */
    public void controlFlowDemo() {
        cfd1();
    }

    /**
     * Implements the body of COBOL paragraph {@code CFD-1}.
     * <ol>
     *   <li>Iterates {@code WS-IDX-COMP} from 1 through 3 (inclusive), displaying
     *       each value — equivalent to {@code PERFORM VARYING … UNTIL > 3}.</li>
     *   <li>Sets {@code WS-IDX-COMP} to 2 and evaluates it with an
     *       {@code EVALUATE} construct, printing the matching label or a default
     *       "other" message for any unmatched value.</li>
     * </ol>
     * <p>Original COBOL: paragraph {@code CFD-1} in program {@code MEGADEMO}.
     */
    public void cfd1() {

        // PERFORM VARYING WS-IDX-COMP FROM 1 BY 1 UNTIL WS-IDX-COMP > 3
        for (int wsIdxComp = 1; wsIdxComp <= 3; wsIdxComp++) {
            log.info("PERFORM VARYING i={}", wsIdxComp);
        }

        // MOVE 2 TO WS-IDX-COMP
        int wsIdxComp = 2;

        // EVALUATE WS-IDX-COMP
        //   WHEN 1  → "EVAL: one"
        //   WHEN 2  → "EVAL: two"
        //   WHEN OTHER → "EVAL: other"
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
    }
}