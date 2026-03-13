```java
/**
 * Migrated from COBOL program MEGADEMO, paragraph TBL-1.
 *
 * <p>This method demonstrates table (array) iteration and search operations:
 * <ol>
 *   <li>Iterates over all table items from index 1 to {@code tblCount}, logging each
 *       item's key.</li>
 *   <li>Performs a <em>linear search</em> (SEARCH) over the table items, looking for
 *       the first entry whose key starts with "AA" (first two characters). Logs the
 *       found index or a "not found" message.</li>
 *   <li>Resets {@code tblCount} to 5, then performs a <em>binary search</em>
 *       (SEARCH ALL) looking for the exact key "AA00000002". Logs the found index
 *       when matched.</li>
 * </ol>
 *
 * <p>COBOL SEARCH (linear) relies on the internal SET/index mechanism; the binary
 * SEARCH ALL assumes the table is sorted ascending by key. Both behaviours are
 * replicated below using standard Java list operations.
 *
 * @param tblItems  list of table-item keys (PIC X(10) equivalent), 1-based in COBOL
 *                  but represented here as a 0-based {@link java.util.List}
 * @param tblCount  the current number of valid entries in the table
 * @return          the updated {@code tblCount} value (set to 5 by this paragraph)
 */
public int tbl1(java.util.List<String> tblItems, int tblCount) {

    // -----------------------------------------------------------------------
    // Step 1 – Sequential display of all table items (PERFORM UNTIL loop)
    // SET TBL-IDX TO 1 … PERFORM UNTIL TBL-IDX > TBL-COUNT
    // -----------------------------------------------------------------------
    for (int tblIdx = 1; tblIdx <= tblCount; tblIdx++) {
        String key = tblItems.get(tblIdx - 1); // convert 1-based COBOL index to 0-based
        System.out.println("TBL ITEM (" + tblIdx + ") KEY=" + key);
    }

    // -----------------------------------------------------------------------
    // Step 2 – Linear search (SEARCH TBL-ITEM)
    // WHEN TBL-ITEM-KEY (TBL-IDX) (1:2) = 'AA'
    // Note: COBOL SEARCH starts from the current TBL-IDX value; after the
    // PERFORM loop above TBL-IDX = tblCount + 1, so we reset to 1 to mirror
    // the typical usage pattern (COBOL SEARCH begins at the SET index).
    // -----------------------------------------------------------------------
    boolean linearFound = false;
    for (int tblIdx = 1; tblIdx <= tblItems.size(); tblIdx++) {
        String key = tblItems.get(tblIdx - 1);
        // (1:2) = first two characters of the key
        if (key != null && key.length() >= 2 && key.substring(0, 2).equals("AA")) {
            System.out.println("LINEAR SEARCH FOUND INDEX=" + tblIdx);
            linearFound = true;
            break;
        }
    }
    if (!linearFound) {
        System.out.println("LINEAR SEARCH: NOT FOUND");
    }

    // -----------------------------------------------------------------------
    // Step 3 – Reset tblCount to 5 (MOVE 5 TO TBL-COUNT)
    // -----------------------------------------------------------------------
    tblCount = 5;

    // -----------------------------------------------------------------------
    // Step 4 – Binary search (SEARCH ALL TBL-ITEM)
    // WHEN TBL-ITEM-KEY (TBL-IDX) = 'AA00000002'
    // SEARCH ALL requires the table to be sorted; we use Collections.binarySearch.
    // -----------------------------------------------------------------------
    java.util.List<String> sortedView = new java.util.ArrayList<>(tblItems.subList(0, tblCount));
    java.util.Collections.sort(sortedView); // ensure sorted order as COBOL SEARCH ALL requires

    int binaryResult = java.util.Collections.binarySearch(sortedView, "AA00000002");
    if (binaryResult >= 0) {
        // Convert back to 1-based COBOL-style index
        int foundIdx = binaryResult + 1;
        System.out.println("BINARY SEARCH FOUND IDX=" + foundIdx);
    }
    // Note: COBOL SEARCH ALL with no AT END clause produces no output when not found.

    // -----------------------------------------------------------------------
    // Return the updated tblCount (was written by this paragraph: MOVE 5 TO TBL-COUNT)
    // -----------------------------------------------------------------------
    return tblCount;
}
```