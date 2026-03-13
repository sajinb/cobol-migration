```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph MAIN-ENTRY.
 *
 * <p>This is the main entry point of the MEGADEMO program. It:
 * <ol>
 *   <li>Displays a program-start banner with the current timestamp.</li>
 *   <li>Optionally displays a debug-mode notice when debug mode is enabled.</li>
 *   <li>Orchestrates the full program flow by sequentially invoking each
 *       major functional section:
 *       <ul>
 *         <li>File and data initialisation</li>
 *         <li>Table operations</li>
 *         <li>String and editing operations</li>
 *         <li>Arithmetic and intrinsic-function demonstrations</li>
 *         <li>Indexed file operations</li>
 *         <li>Relative file operations</li>
 *         <li>Sequential file and sort operations</li>
 *         <li>Control-flow demonstrations</li>
 *         <li>External CALL and pointer demonstrations</li>
 *       </ul>
 *   </li>
 *   <li>Displays a program-end banner with the current timestamp.</li>
 * </ol>
 *
 * <p>Original COBOL paragraph: {@code MAIN-ENTRY} in program {@code MEGADEMO}.
 *
 * @param debugMode {@code true} when the program is running in debug mode
 *                  (equivalent to the COBOL {@code WS-DEBUG} flag).
 */
public void mainEntry(boolean debugMode) {

    String msgInfoPrefix = "INFO: ";

    // DISPLAY MSG-INFO-PREFIX 'Program start: ' FUNCTION CURRENT-DATE
    log.info("{} Program start: {}", msgInfoPrefix,
             java.time.LocalDateTime.now()
                 .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));

    // IF WS-DEBUG
    //    DISPLAY MSG-INFO-PREFIX 'Debug mode enabled.'
    // END-IF
    if (debugMode) {
        log.info("{} Debug mode enabled.", msgInfoPrefix);
    }

    // PERFORM INIT-FILES-AND-DATA
    initFilesAndData();

    // PERFORM TABLE-OPS
    tableOps();

    // PERFORM STRING-AND-EDITING-OPS
    stringAndEditingOps();

    // PERFORM ARITHMETIC-AND-FUNCTIONS
    arithmeticAndFunctions();

    // PERFORM INDEXED-FILE-OPS
    indexedFileOps();

    // PERFORM RELATIVE-FILE-OPS
    relativeFileOps();

    // PERFORM SEQ-FILE-AND-SORT-OPS
    seqFileAndSortOps();

    // PERFORM CONTROL-FLOW-DEMO
    controlFlowDemo();

    // PERFORM CALLS-AND-POINTERS
    callsAndPointers();

    // DISPLAY MSG-INFO-PREFIX 'Program end:   ' FUNCTION CURRENT-DATE
    log.info("{} Program end:   {}", msgInfoPrefix,
             java.time.LocalDateTime.now()
                 .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
}
```