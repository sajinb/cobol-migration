```java
/**
 * Migrated from COBOL paragraph: REL-1 (Program: MEGADEMO)
 *
 * <p>Performs relative file operations:
 * <ol>
 *   <li>Sets the relative key (WS-REL-KEY) to 1 and the record ID (REL-ID) to 1.</li>
 *   <li>Writes a relative record with data "RELATIVE DATA" to the relative file (RELFILE),
 *       logging an error message if the write fails (INVALID KEY condition).</li>
 *   <li>Reads the relative record back from RELFILE into REL-REC,
 *       logging an error message if the read fails (INVALID KEY condition).</li>
 * </ol>
 *
 * <p>Corresponds to COBOL EXIT SECTION at the end of REL-1,
 * transitioning into SEQ-FILE-AND-SORT-OPS SECTION.
 *
 * @param msgErrPrefix  The error message prefix (MSG-ERR-PREFIX) used in error display statements.
 * @return              A {@link RelFileResult} containing the relative key, record ID,
 *                      record data, and any file-status error message encountered.
 */
public RelFileResult rel1(String msgErrPrefix) {

    // MOVE 1 TO WS-REL-KEY
    BigDecimal wsRelKey = BigDecimal.ONE;

    // MOVE 1 TO REL-ID
    int relId = 1;

    // MOVE 'RELATIVE DATA' TO REL-DATA
    String relData = "RELATIVE DATA";

    // Construct the record to write
    RelRecord relRec = new RelRecord();
    relRec.setRelId(relId);
    relRec.setRelData(relData);

    String wsfsRel = "";

    // WRITE REL-REC INVALID KEY DISPLAY ... END-WRITE
    try {
        relFileRepository.writeByKey(wsRelKey, relRec);
    } catch (InvalidKeyException | DataAccessException e) {
        wsfsRel = e.getMessage() != null ? e.getMessage() : "UNKNOWN";
        log.error("{} REL WRITE FAILED FS={}", msgErrPrefix, wsfsRel);
    }

    // READ RELFILE RECORD INTO REL-REC INVALID KEY DISPLAY ... END-READ
    RelRecord readRelRec = null;
    try {
        readRelRec = relFileRepository.readByKey(wsRelKey)
                .orElseThrow(() -> new InvalidKeyException("Record not found for key: " + wsRelKey));
    } catch (InvalidKeyException | DataAccessException e) {
        wsfsRel = e.getMessage() != null ? e.getMessage() : "UNKNOWN";
        log.error("{} REL READ FAILED FS={}", msgErrPrefix, wsfsRel);
    }

    // EXIT SECTION — return collected state to caller
    return new RelFileResult(wsRelKey, relId, relData, readRelRec, wsfsRel);
}
```