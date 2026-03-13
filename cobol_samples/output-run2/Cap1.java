package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 *
 * <p>This service encapsulates the logic of paragraph {@code CAP-1}, which:
 * <ul>
 *   <li>Sets a pointer address to a relative record (simulated via a byte-array reference).</li>
 *   <li>Calls external sub-program {@code SUBPGM1} passing EMP-ID by reference,
 *       WS-ALNUM by content, and WS-IDX-COMP by value.</li>
 *   <li>Cancels (unloads) {@code SUBPGM1} after the call.</li>
 *   <li>Moves the literal {@code 'SYSTEM'} into the first 6 characters of WS-ALNUM.</li>
 *   <li>Performs a dynamic CALL using the (now-modified) WS-ALNUM as the program name,
 *       passing the literal {@code 'echo DYNAMIC CALL'} by content.</li>
 *   <li>Displays a confirmation message after the dynamic call.</li>
 * </ul>
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Registry that simulates the COBOL dynamic-CALL dispatch table.
     * Keys are program names (upper-case); values are {@link DynamicCallable} lambdas.
     * Callers should register known dynamic targets before invoking {@link #cap1}.
     */
    private final Map<String, DynamicCallable> dynamicCallRegistry = new HashMap<>();

    /**
     * Functional interface that represents a dynamically-resolved COBOL CALL target.
     */
    @FunctionalInterface
    public interface DynamicCallable {
        /**
         * Execute the dynamic call.
         *
         * @param argument the BY CONTENT argument passed to the called program
         */
        void call(String argument);
    }

    /**
     * Register a handler for a dynamic CALL target so that {@link #cap1} can dispatch to it.
     *
     * @param programName the COBOL program name (case-insensitive)
     * @param callable    the Java implementation to invoke
     */
    public void registerDynamicCall(String programName, DynamicCallable callable) {
        if (programName != null && callable != null) {
            dynamicCallRegistry.put(programName.trim().toUpperCase(), callable);
        }
    }

    /**
     * Migrated implementation of COBOL paragraph {@code CAP-1} in program {@code MEGADEMO}.
     *
     * <p>Behaviour:
     * <ol>
     *   <li><b>SET ADDRESS OF REL-REC TO WS-POINTER</b> — the relative record buffer
     *       ({@code relRec}) is treated as a view over the same backing byte array as
     *       {@code wsPointerBuffer}; in Java both arrays share the same reference after
     *       this step.</li>
     *   <li><b>CALL 'SUBPGM1'</b> — delegated to {@link Subpgm1Service#execute}.</li>
     *   <li><b>CANCEL 'SUBPGM1'</b> — simulated by nulling the local reference to the
     *       service result, signalling that the module's state should be discarded.</li>
     *   <li><b>MOVE 'SYSTEM' TO WS-ALNUM (1:6)</b> — overwrites the first 6 characters
     *       of {@code wsAlnum} with {@code "SYSTEM"}.</li>
     *   <li><b>CALL WS-ALNUM</b> — dispatches to the registered handler whose key matches
     *       the (trimmed, upper-cased) value of {@code wsAlnum}.</li>
     * </ol>
     *
     * @param empId           EMP-ID passed BY REFERENCE to SUBPGM1; a single-element array
     *                        so that mutations inside the simulated sub-program are visible
     *                        to the caller (reference semantics).
     * @param wsAlnumBuffer   WS-ALNUM as a fixed-length {@code char[]} of at least 6 elements;
     *                        modified in-place (MOVE 'SYSTEM' step).
     * @param wsIdxComp       WS-IDX-COMP passed BY VALUE to SUBPGM1 (COMP field → {@code int}).
     * @param wsPointerBuffer the byte array that WS-POINTER points to; REL-REC is set to
     *                        this same buffer (pointer aliasing simulation).
     * @param subpgm1Service  the Spring service that implements SUBPGM1 logic.
     * @return a {@link Cap1Result} containing the (possibly modified) EMP-ID and WS-ALNUM
     *         after execution, mirroring COBOL BY REFERENCE / shared-state semantics.
     */
    public Cap1Result cap1(
            String[] empId,
            char[] wsAlnumBuffer,
            int wsIdxComp,
            byte[] wsPointerBuffer,
            Subpgm1Service subpgm1Service) {

        // ── SET ADDRESS OF REL-REC TO WS-POINTER ─────────────────────────────────
        // In COBOL this makes REL-REC overlay the same storage as WS-POINTER.
        // In Java we model this as aliasing: relRec points to the same byte array.
        byte[] relRec = wsPointerBuffer;   // aliased — same backing array
        log.info("POINTER ADDR={} (relRec aliased to wsPointerBuffer, length={})",
                System.identityHashCode(wsPointerBuffer), relRec.length);

        // ── CALL 'SUBPGM1' USING BY REFERENCE EMP-ID
        //                         BY CONTENT  WS-ALNUM
        //                         BY VALUE    WS-IDX-COMP ──────────────────────────
        // BY REFERENCE → pass the array wrapper so mutations are visible to caller.
        // BY CONTENT   → pass a defensive copy so SUBPGM1 cannot alter the original.
        // BY VALUE     → pass the primitive directly.
        char[] wsAlnumCopy = Arrays.copyOf(wsAlnumBuffer, wsAlnumBuffer.length);
        subpgm1Service.execute(empId, wsAlnumCopy, wsIdxComp);

        // ── CANCEL 'SUBPGM1' ─────────────────────────────────────────────────────
        // CANCEL in COBOL releases the program's internal state so the next CALL
        // starts fresh.  In Java we signal this by discarding any cached state;
        // the service bean itself remains in the Spring context (stateless by design).
        subpgm1Service.cancel();
        log.debug("SUBPGM1 cancelled (state reset).");

        // ── MOVE 'SYSTEM' TO WS-ALNUM (1:6) ──────────────────────────────────────
        // COBOL reference modification (1:6) is 1-based, length 6 → Java indices 0..5.
        String literal = "SYSTEM";
        for (int i = 0; i < literal.length() && i < wsAlnumBuffer.length; i++) {
            wsAlnumBuffer[i] = literal.charAt(i);
        }
        log.debug("WS-ALNUM after MOVE 'SYSTEM': {}", new String(wsAlnumBuffer));

        // ── CALL WS-ALNUM USING BY CONTENT 'echo DYNAMIC CALL' ───────────────────
        // The program name is the trimmed, upper-cased value of WS-ALNUM.
        String dynamicProgramName = new String(wsAlnumBuffer).trim().toUpperCase();
        String dynamicArgument = "echo DYNAMIC CALL";

        DynamicCallable target = dynamicCallRegistry.get(dynamicProgramName);
        if (target != null) {
            log.info("Dispatching dynamic CALL to program '{}'", dynamicProgramName);
            target.call(dynamicArgument);
        } else {
            // Simulate the COBOL runtime behaviour when the program is not found:
            // log a warning but do not abort (mirrors many COBOL runtime environments
            // that issue a warning and continue when CALL target is unresolved).
            log.warn("Dynamic CALL target '{}' not registered; call skipped.", dynamicProgramName);
        }

        // ── DISPLAY 'Dynamic CALL executed (if supported).' ───────────────────────
        log.info("Dynamic CALL executed (if supported).");

        // ── Return shared state to caller ─────────────────────────────────────────
        return new Cap1Result(empId[0], new String(wsAlnumBuffer));
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Inner types
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Carries the mutable output values that COBOL would have left in WORKING-STORAGE
     * after paragraph {@code CAP-1} completes.
     */
    public static final class Cap1Result {

        private final String empId;
        private final String wsAlnum;

        public Cap1Result(String empId, String wsAlnum) {
            this.empId = empId;
            this.wsAlnum = wsAlnum;
        }

        /** The (possibly modified) EMP-ID after SUBPGM1 returned. */
        public String getEmpId() {
            return empId;
        }

        /** The value of WS-ALNUM after the MOVE 'SYSTEM' step. */
        public String getWsAlnum() {
            return wsAlnum;
        }

        @Override
        public String toString() {
            return "Cap1Result{empId='" + empId + "', wsAlnum='" + wsAlnum + "'}";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Stub inner service — replace with a real @Service bean in production
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Represents the external COBOL sub-program {@code SUBPGM1}.
     *
     * <p>In a real migration this would be a separate {@code @Service}-annotated class
     * (or a {@code @FeignClient} if SUBPGM1 is deployed as a micro-service).
     * It is nested here only to keep the generated file self-contained and compilable.
     */
    @Slf4j
    @Service
    public static class Subpgm1Service {

        /**
         * Simulates {@code CALL 'SUBPGM1'}.
         *
         * @param empId      EMP-ID BY REFERENCE — element 0 may be modified.
         * @param wsAlnum    WS-ALNUM BY CONTENT — a defensive copy; changes are NOT
         *                   propagated back to the caller.
         * @param wsIdxComp  WS-IDX-COMP BY VALUE.
         */
        public void execute(String[] empId, char[] wsAlnum, int wsIdxComp) {
            log.info("SUBPGM1 called: empId={}, wsAlnum={}, wsIdxComp={}",
                    empId[0], new String(wsAlnum), wsIdxComp);
            // Real SUBPGM1 logic would be implemented here.
            // empId[0] may be updated to reflect BY REFERENCE semantics.
        }

        /**
         * Simulates {@code CANCEL 'SUBPGM1'} — resets any internal state so that
         * the next {@link #execute} call starts with a clean slate.
         */
        public void cancel() {
            log.debug("SUBPGM1 internal state reset (CANCEL).");
            // Reset any instance-level state fields here if they exist.
        }
    }
}