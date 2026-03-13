package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Executes the logic of the COBOL paragraph {@code LABEL-1}.
     * <p>
     * This paragraph displays a diagnostic message indicating that a
     * "GO TO DEPENDING ON" branch resolved to case 1, then transfers
     * control to the {@code CFD-EXIT} paragraph (modelled here as an
     * early return after logging, since {@code CFD-EXIT} is the exit
     * point of the surrounding section).
     * <p>
     * Original COBOL: paragraph {@code LABEL-1} in program {@code MEGADEMO}.
     */
    public void label1() {
        log.info("GO TO DEPENDING ON -> 1");
        // GO TO CFD-EXIT transfers control unconditionally to the exit
        // paragraph of the current section.  In Java this is represented
        // as an immediate return so that no further logic in the section
        // is executed after this branch is taken.
        cfdExit();
    }

    /**
     * Models the {@code CFD-EXIT} paragraph, which is the terminal /
     * exit point of the section that contains {@code LABEL-1}.
     * <p>
     * In COBOL, a paragraph named {@code <SECTION>-EXIT} (or simply the
     * last paragraph of a section) typically contains only {@code EXIT}
     * or falls through to the end of the section.  The Java equivalent
     * is a no-op method that serves as the single exit point so that
     * callers of {@link #label1()} can reason about control flow in the
     * same way the original COBOL did.
     * <p>
     * Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
     */
    public void cfdExit() {
        // Corresponds to COBOL EXIT / fall-through at end of section.
        // No operations are performed; control simply returns to the caller.
        log.debug("CFD-EXIT reached — section complete.");
    }
}