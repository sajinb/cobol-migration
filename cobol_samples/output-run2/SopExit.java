package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Executes the SOP-EXIT paragraph logic.
     * <p>
     * In the original COBOL program this paragraph is an empty exit/return point,
     * typically used as the target of a PERFORM … THRU … construct to mark the
     * end of a section or a structured block.  It contains no executable
     * statements and therefore this method is a deliberate no-op.
     * <p>Original COBOL: paragraph {@code SOP-EXIT} in program {@code MEGADEMO}.
     */
    public void sopExit() {
        // Intentional no-op: the original COBOL paragraph SOP-EXIT contains no
        // executable statements.  It exists solely as a fall-through / exit label
        // for PERFORM … THRU constructs in the COBOL source.
        log.debug("sopExit() called — no-op exit paragraph");
    }
}