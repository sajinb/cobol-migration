package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * This service handles the {@code CALLS-AND-POINTERS} section, which acts as
 * an entry point that delegates to the {@code CAP-1} paragraph body.
 */
@Slf4j
@Service
public class MegademoService {

    @Autowired
    private Subpgm1Service subpgm1Service;

    /**
     * Entry point for the {@code CALLS-AND-POINTERS} COBOL section.
     * Delegates immediately to {@link #cap1(String[], String, int)} which
     * contains the section body originally coded in paragraph {@code CAP-1}.
     *
     * <p>Original COBOL: section {@code CALLS-AND-POINTERS} in program {@code MEGADEMO}.
     *
     * @param relRecBuffer  byte buffer that backs the {@code REL-REC} record
     *                      (simulates {@code SET ADDRESS OF REL-REC TO WS-POINTER})
     * @param wsPointer     string representation of the pointer / address value
     *                      ({@code WS-POINTER})
     * @param empId         employee-id passed BY REFERENCE to {@code SUBPGM1}
     *                      ({@code EMP-ID})
     * @param wsAlnum       alphanumeric working-storage field ({@code WS-ALNUM});
     *                      element [0] is passed BY CONTENT to {@code SUBPGM1}
     *                      and element [0] is also updated with {@code 'SYSTEM'}
     * @param wsIdxComp     integer index passed BY VALUE to {@code SUBPGM1}
     *                      ({@code WS-IDX-COMP})
     * @return updated {@code wsAlnum} array after all mutations performed in
     *         {@code CAP-1}
     */
    public String[] callsAndPointers(
            byte[]   relRecBuffer,
            String   wsPointer,
            String[] empId,
            String[] wsAlnum,
            int      wsIdxComp) {

        return cap1(relRecBuffer, wsPointer, empId, wsAlnum, wsIdxComp);
    }

    /**
     * Implements the body of COBOL paragraph {@code CAP-1} inside section
     * {@code CALLS-AND-POINTERS} of program {@code MEGADEMO}.
     *
     * <p>Steps performed:
     * <ol>
     *   <li>{@code SET ADDRESS OF REL-REC TO WS-POINTER} — recorded in the
     *       {@code relRecBuffer} reference; in Java the buffer is simply
     *       acknowledged (pointer arithmetic is not applicable in managed
     *       memory).</li>
     *   <li>{@code DISPLAY 'POINTER ADDR=' WS-POINTER} — logged via SLF4J.</li>
     *   <li>{@code CALL 'SUBPGM1' USING BY REFERENCE EMP-ID,
     *       BY CONTENT WS-ALNUM, BY VALUE WS-IDX-COMP} — delegated to the
     *       injected {@link Subpgm1Service}.</li>
     *   <li>{@code CANCEL 'SUBPGM1'} — simulated by invoking
     *       {@link Subpgm1Service#cancel()}.</li>
     *   <li>{@code MOVE 'SYSTEM' TO WS-ALNUM (1:6)} — overwrites the first
     *       six characters of {@code wsAlnum[0]} with {@code "SYSTEM"}.</li>
     *   <li>{@code CALL WS-ALNUM} — dynamic program call resolved via
     *       {@link Subpgm1Service#callDynamic(String)} using the (now updated)
     *       value of {@code wsAlnum[0]}.</li>
     * </ol>
     *
     * <p>Original COBOL: paragraph {@code CAP-1} in program {@code MEGADEMO}.
     *
     * @param relRecBuffer  byte buffer representing the {@code REL-REC} record
     * @param wsPointer     pointer address value ({@code WS-POINTER})
     * @param empId         employee-id array; element [0] is passed by reference
     * @param wsAlnum       alphanumeric field array; mutated in place
     * @param wsIdxComp     integer index passed by value
     * @return the mutated {@code wsAlnum} array
     */
    public String[] cap1(
            byte[]   relRecBuffer,
            String   wsPointer,
            String[] empId,
            String[] wsAlnum,
            int      wsIdxComp) {

        // SET ADDRESS OF REL-REC TO WS-POINTER
        // In Java, managed memory does not support raw pointer assignment.
        // The relRecBuffer reference is accepted as the logical equivalent of
        // the COBOL pointer target; no byte-level re-addressing is performed.
        log.debug("SET ADDRESS OF REL-REC TO WS-POINTER: relRecBuffer length={}, wsPointer={}",
                relRecBuffer != null ? relRecBuffer.length : 0, wsPointer);

        // DISPLAY 'POINTER ADDR=' WS-POINTER
        log.info("POINTER ADDR={}", wsPointer);

        // CALL 'SUBPGM1' USING BY REFERENCE EMP-ID
        //                      BY CONTENT  WS-ALNUM
        //                      BY VALUE    WS-IDX-COMP
        // BY REFERENCE: empId[0] may be updated by the callee.
        // BY CONTENT  : a copy of wsAlnum[0] is passed; callee changes do not
        //               propagate back.
        // BY VALUE    : wsIdxComp is passed as a primitive.
        String wsAlnumCopy = wsAlnum != null && wsAlnum.length > 0 ? wsAlnum[0] : "";
        subpgm1Service.call(empId, wsAlnumCopy, wsIdxComp);

        // CANCEL 'SUBPGM1'
        // Releases the runtime's hold on the called program; simulated here by
        // invoking the cancel lifecycle method on the service.
        subpgm1Service.cancel();

        // MOVE 'SYSTEM' TO WS-ALNUM (1:6)
        // COBOL reference modification (1:6) replaces characters 1-6 (1-based)
        // with 'SYSTEM'.  In Java this is 0-based index 0 through 5.
        if (wsAlnum != null && wsAlnum.length > 0) {
            String current = wsAlnum[0] != null ? wsAlnum[0] : "";
            // Pad to at least 6 characters so the overlay is always valid.
            if (current.length() < 6) {
                current = String.format("%-" + Math.max(6, current.length()) + "s", current);
            }
            // Replace first 6 characters with "SYSTEM", preserve the remainder.
            String updated = "SYSTEM" + (current.length() > 6 ? current.substring(6) : "");
            wsAlnum[0] = updated;
        }

        // CALL WS-ALNUM  (dynamic program call using the current value of WS-ALNUM)
        // The program name to invoke is now the updated value of wsAlnum[0],
        // trimmed to remove any trailing padding.
        String dynamicProgramName = (wsAlnum != null && wsAlnum.length > 0 && wsAlnum[0] != null)
                ? wsAlnum[0].trim()
                : "";
        log.info("Dynamic CALL to program: '{}'", dynamicProgramName);
        subpgm1Service.callDynamic(dynamicProgramName);

        return wsAlnum;
    }
}