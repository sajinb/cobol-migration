package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Executes the logic of the COBOL paragraph {@code LABEL-2} in program {@code MEGADEMO}.
     * <p>
     * This paragraph is one of the targets of a {@code GO TO ... DEPENDING ON} dispatch table.
     * It logs a diagnostic message indicating that branch 2 was selected, then transfers
     * control to {@code CFD-EXIT}, which in the original COBOL terminates the current
     * section/paragraph flow. In Java this is modelled by returning normally after logging,
     * since there is no further work to perform in this branch.
     * <p>Original COBOL: paragraph {@code LABEL-2} in program {@code MEGADEMO}.
     */
    public void label2() {
        log.info("GO TO DEPENDING ON -> 2");
        cfdExit();
    }

    /**
     * Models the {@code CFD-EXIT} paragraph, which in the original COBOL serves as the
     * common exit / fall-through point for the computed GO TO dispatch table.
     * Reaching this point means the selected branch has completed and control is returned
     * to the caller.
     * <p>Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
     */
    public void cfdExit() {
        // CFD-EXIT is a no-op exit paragraph in the original COBOL;
        // returning here transfers control back to the invoking method,
        // which mirrors the COBOL behaviour of falling through to the next
        // sequential sentence after the PERFORM or GO TO that reached this point.
    }
}