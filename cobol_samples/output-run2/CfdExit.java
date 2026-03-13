package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Migration of COBOL program {@code MEGADEMO} paragraph {@code CFD-EXIT}.
 *
 * <p>The original COBOL paragraph contained an {@code ALTER} statement:
 * <pre>
 *   ALTER OLD-PARAGRAPH TO PROCEED TO NEW-PARAGRAPH.
 * </pre>
 * The {@code ALTER} verb was a dynamic control-flow modifier that changed the
 * destination of a {@code GO TO} in another paragraph at runtime. This construct
 * has no direct equivalent in structured Java. The migration models it as a
 * routing/dispatch mechanism: a mutable state variable ({@code currentParagraph})
 * tracks which logical branch should execute next, and callers consult that value
 * to decide which method to invoke — effectively replacing the ALTER/GO TO pattern
 * with a strategy-style dispatch.
 *
 * <p>Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Symbolic names for the two logical paragraphs involved in the ALTER statement.
     * {@code OLD_PARAGRAPH} is the paragraph whose GO TO destination was being altered;
     * {@code NEW_PARAGRAPH} is the new destination after the ALTER executes.
     */
    public enum ParagraphTarget {
        OLD_PARAGRAPH,
        NEW_PARAGRAPH
    }

    /**
     * Mutable routing state that replaces the COBOL {@code ALTER} mechanism.
     * Initially points to {@code OLD_PARAGRAPH}; after {@link #cfdExit()} executes
     * it is redirected to {@code NEW_PARAGRAPH}, mirroring the runtime effect of
     * {@code ALTER OLD-PARAGRAPH TO PROCEED TO NEW-PARAGRAPH}.
     */
    private volatile ParagraphTarget currentParagraph = ParagraphTarget.OLD_PARAGRAPH;

    /**
     * Returns the current paragraph routing target.
     * Callers that previously would have executed {@code OLD-PARAGRAPH}'s GO TO
     * should consult this value to determine the actual dispatch destination.
     *
     * @return the currently active {@link ParagraphTarget}
     */
    public ParagraphTarget getCurrentParagraph() {
        return currentParagraph;
    }

    /**
     * Resets the routing state back to {@code OLD_PARAGRAPH}.
     * Useful when the program logic needs to re-initialise the altered GO TO
     * to its original destination (e.g. on a new transaction cycle).
     */
    public void resetToOldParagraph() {
        log.debug("Resetting paragraph routing from {} to OLD_PARAGRAPH", currentParagraph);
        currentParagraph = ParagraphTarget.OLD_PARAGRAPH;
    }

    /**
     * Executes the logic of COBOL paragraph {@code CFD-EXIT}.
     *
     * <p>The original statement was:
     * <pre>
     *   ALTER OLD-PARAGRAPH TO PROCEED TO NEW-PARAGRAPH.
     * </pre>
     * This method replicates that effect by updating the internal routing state
     * so that any subsequent dispatch that previously would have branched to
     * {@code OLD-PARAGRAPH} will now proceed to {@code NEW-PARAGRAPH} instead.
     *
     * <p>Original COBOL: paragraph {@code CFD-EXIT} in program {@code MEGADEMO}.
     */
    public void cfdExit() {
        log.debug(
                "CFD-EXIT: altering paragraph routing from {} to NEW_PARAGRAPH",
                currentParagraph);

        currentParagraph = ParagraphTarget.NEW_PARAGRAPH;

        log.debug("CFD-EXIT: paragraph routing is now {}", currentParagraph);
    }

    /**
     * Dispatches to the appropriate paragraph implementation based on the current
     * routing state established by {@link #cfdExit()} (or its absence).
     *
     * <p>This method replaces the implicit GO TO dispatch that COBOL's ALTER
     * mechanism controlled at runtime.
     */
    public void dispatchCurrentParagraph() {
        ParagraphTarget target = currentParagraph;
        log.debug("Dispatching to paragraph target: {}", target);

        switch (target) {
            case OLD_PARAGRAPH:
                oldParagraph();
                break;
            case NEW_PARAGRAPH:
                newParagraph();
                break;
            default:
                log.warn("Unknown paragraph target '{}'; no dispatch performed.", target);
                break;
        }
    }

    /**
     * Placeholder implementation for the COBOL {@code OLD-PARAGRAPH} logic.
     * In the original program this paragraph contained a GO TO whose destination
     * was subject to ALTER; its own business logic should be filled in here
     * when the full paragraph source is available.
     *
     * <p>Original COBOL: paragraph {@code OLD-PARAGRAPH} in program {@code MEGADEMO}.
     */
    public void oldParagraph() {
        log.debug("Executing OLD-PARAGRAPH logic.");
        // Business logic for OLD-PARAGRAPH goes here once the full source is available.
        // The GO TO within OLD-PARAGRAPH is replaced by dispatchCurrentParagraph().
    }

    /**
     * Placeholder implementation for the COBOL {@code NEW-PARAGRAPH} logic.
     * After {@link #cfdExit()} executes, all dispatches that formerly went to
     * {@code OLD-PARAGRAPH} are redirected here.
     *
     * <p>Original COBOL: paragraph {@code NEW-PARAGRAPH} in program {@code MEGADEMO}.
     */
    public void newParagraph() {
        log.debug("Executing NEW-PARAGRAPH logic.");
        // Business logic for NEW-PARAGRAPH goes here once the full source is available.
    }
}