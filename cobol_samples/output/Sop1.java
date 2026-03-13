```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph SOP-1.
 *
 * <p>Original behaviour: Iterates over all records returned from the SORT-FILE
 * (a COBOL sort/merge intermediate file) by repeatedly issuing RETURN statements.
 * For each record retrieved it displays the sort key (S-KEY) and associated data
 * (S-DATA) to standard output.  Processing stops when the AT END condition is
 * raised (i.e. no more sorted records remain).
 *
 * <p>In Java the SORT-FILE / RETURN loop is modelled as an iteration over a
 * pre-sorted list of {@link SortRecord} objects that the caller has already
 * produced (equivalent to the COBOL SORT verb populating the sort file).
 * Each element is printed in the same "SORTED: <key> -> <data>" format.
 *
 * @param sortedRecords the fully sorted list of records to process
 *                      (replaces the SORT-FILE RETURN loop)
 */
public void sop1(List<SortRecord> sortedRecords) {

    if (sortedRecords == null || sortedRecords.isEmpty()) {
        // AT END condition on the very first RETURN — jump straight to SOP-EXIT
        sopExit();
        return;
    }

    for (SortRecord sortRec : sortedRecords) {

        // DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
        System.out.printf("SORTED:%s -> %s%n",
                formatSKey(sortRec.getSKey()),
                formatSData(sortRec.getSData()));
    }

    // Fall-through after AT END — equivalent to GO TO SOP-EXIT
    sopExit();
}

/**
 * Migrated from COBOL paragraph SOP-EXIT (stub).
 * Represents the exit / end-of-paragraph target reached when the SORT-FILE
 * is exhausted.
 */
private void sopExit() {
    // No-op exit point — control returns naturally to the caller.
}

/**
 * Formats S-KEY to match the PIC X display width expected by the DISPLAY verb.
 * Adjust padding/trimming to match the actual PIC clause of S-KEY.
 */
private String formatSKey(String sKey) {
    return sKey == null ? "" : sKey;
}

/**
 * Formats S-DATA to match the PIC X display width expected by the DISPLAY verb.
 * Adjust padding/trimming to match the actual PIC clause of S-DATA.
 */
private String formatSData(String sData) {
    return sData == null ? "" : sData;
}
```