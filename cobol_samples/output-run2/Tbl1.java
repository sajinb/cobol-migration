package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * Contains logic migrated from paragraph {@code TBL-1}, which:
 * <ul>
 *   <li>Iterates over a table of items, displaying each key.</li>
 *   <li>Performs a linear (SEARCH) scan to find the first item whose key starts with "AA".</li>
 *   <li>Resets the table count to 5 and performs a binary (SEARCH ALL) scan
 *       for the item whose key equals "AA00000002".</li>
 * </ul>
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Represents a single table item, analogous to the COBOL {@code TBL-ITEM} group entry.
     */
    public static class TblItem {
        private final String key;

        public TblItem(String key) {
            if (key == null) {
                throw new IllegalArgumentException("TblItem key must not be null");
            }
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    /**
     * Holds the result of the {@code tbl1} operation so that mutations to
     * {@code tblCount} (WORKING-STORAGE) are visible to the caller.
     */
    public static class Tbl1Result {
        private int tblCount;

        public Tbl1Result(int tblCount) {
            this.tblCount = tblCount;
        }

        public int getTblCount() {
            return tblCount;
        }

        public void setTblCount(int tblCount) {
            this.tblCount = tblCount;
        }
    }

    /**
     * Migrated from COBOL paragraph {@code TBL-1} in program {@code MEGADEMO}.
     *
     * <p>Behaviour:
     * <ol>
     *   <li>Iterates from index 1 to {@code tblCount} (inclusive), logging each item's key.</li>
     *   <li>Performs a linear search (COBOL {@code SEARCH}) over the full array,
     *       logging the first item whose key starts with {@code "AA"}.</li>
     *   <li>Sets {@code tblCount} to 5, then performs a binary search
     *       (COBOL {@code SEARCH ALL}) over the first 5 elements for the key
     *       {@code "AA00000002"}, logging the result.</li>
     * </ol>
     *
     * <p>Original COBOL: paragraph {@code TBL-1} in program {@code MEGADEMO}.
     *
     * @param tblItems array of {@link TblItem} objects (COBOL {@code TBL-ITEM OCCURS})
     * @param tblCount number of valid entries currently in the table (COBOL {@code TBL-COUNT})
     * @return a {@link Tbl1Result} containing the updated {@code tblCount} value
     */
    public Tbl1Result tbl1(TblItem[] tblItems, int tblCount) {

        // ----------------------------------------------------------------
        // 1.  SET TBL-IDX TO 1
        //     PERFORM UNTIL TBL-IDX > TBL-COUNT
        //        DISPLAY 'TBL ITEM (' TBL-IDX ') KEY=' TBL-ITEM-KEY (TBL-IDX)
        //        SET TBL-IDX UP BY 1
        //     END-PERFORM
        // COBOL indices are 1-based; Java arrays are 0-based.
        // ----------------------------------------------------------------
        int tblIdx = 1;
        while (tblIdx <= tblCount) {
            // Guard against an array that is shorter than tblCount claims.
            if (tblIdx - 1 < tblItems.length) {
                String key = tblItems[tblIdx - 1].getKey();
                log.info("TBL ITEM ({}) KEY={}", tblIdx, key);
            }
            tblIdx++;
        }

        // ----------------------------------------------------------------
        // 2.  SEARCH TBL-ITEM  (linear search — COBOL resets TBL-IDX to 1
        //     implicitly at the start of SEARCH and increments it each cycle)
        //     AT END DISPLAY 'LINEAR SEARCH: NOT FOUND'
        //     WHEN TBL-ITEM-KEY (TBL-IDX) (1:2) = 'AA'
        //          DISPLAY 'LINEAR SEARCH FOUND INDEX=' TBL-IDX
        //     END-SEARCH
        //
        //  COBOL SEARCH starts from the current value of TBL-IDX (which is
        //  tblCount+1 after the loop above).  To match standard COBOL SEARCH
        //  semantics the index is reset to 1 at the beginning of SEARCH.
        // ----------------------------------------------------------------
        tblIdx = 1;
        boolean linearFound = false;
        for (int i = 0; i < tblItems.length; i++) {
            tblIdx = i + 1;                          // keep COBOL 1-based index in sync
            String key = tblItems[i].getKey();
            // COBOL reference modification (1:2) — substring starting at position 1, length 2
            String keyPrefix = key.length() >= 2 ? key.substring(0, 2) : key;
            if ("AA".equals(keyPrefix)) {
                log.info("LINEAR SEARCH FOUND INDEX={}", tblIdx);
                linearFound = true;
                break;
            }
        }
        if (!linearFound) {
            log.info("LINEAR SEARCH: NOT FOUND");
        }

        // ----------------------------------------------------------------
        // 3.  MOVE 5 TO TBL-COUNT
        //     SET TBL-IDX TO 1
        //     SEARCH ALL TBL-ITEM
        //        WHEN TBL-ITEM-KEY (TBL-IDX) = 'AA00000002'
        //             DISPLAY 'BINARY SEARCH FOUND IDX=' TBL-IDX
        //     END-SEARCH
        //
        //  COBOL SEARCH ALL performs a binary search on a sorted table.
        //  The effective table size is now tblCount = 5.
        //  Java's Arrays.binarySearch is used on the sub-array [0..4].
        // ----------------------------------------------------------------
        tblCount = 5;
        tblIdx = 1;

        final String binarySearchTarget = "AA00000002";

        // Build a sub-list of the first tblCount elements for the binary search.
        int effectiveSize = Math.min(tblCount, tblItems.length);
        String[] keys = new String[effectiveSize];
        for (int i = 0; i < effectiveSize; i++) {
            keys[i] = tblItems[i].getKey();
        }

        // Arrays.binarySearch requires the array to be sorted (matches COBOL SEARCH ALL
        // requirement that the table is sorted on the key).
        int binaryResult = Arrays.binarySearch(keys, binarySearchTarget);
        if (binaryResult >= 0) {
            // Convert 0-based Java index back to 1-based COBOL index.
            tblIdx = binaryResult + 1;
            log.info("BINARY SEARCH FOUND IDX={}", tblIdx);
        }
        // COBOL SEARCH ALL has no explicit AT END clause in this paragraph,
        // so no "not found" message is emitted for the binary search.

        return new Tbl1Result(tblCount);
    }
}