package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * This class contains the migration of the {@code TABLE-OPS} section and its
 * body paragraph {@code TBL-1}, which iterate over a table of keyed items,
 * display each entry, and perform a linear search for items whose key begins
 * with {@code "AA"}.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Entry point for the {@code TABLE-OPS} COBOL section.
     * <p>
     * In the original COBOL, {@code TABLE-OPS} is a SECTION whose only
     * responsibility is to fall through to its first (and only) paragraph
     * body {@code TBL-1}.  This method therefore delegates directly to
     * {@link #tbl1(List)}.
     * <p>
     * Original COBOL: section {@code TABLE-OPS} in program {@code MEGADEMO}.
     *
     * @param tblItems list of table items, each carrying a string key;
     *                 corresponds to the COBOL {@code TBL-ITEM} table
     *                 (OCCURS … TIMES) with associated {@code TBL-ITEM-KEY}
     */
    public void tableOps(List<TblItem> tblItems) {
        tbl1(tblItems);
    }

    /**
     * Implements the {@code TBL-1} COBOL paragraph.
     * <ol>
     *   <li>Iterates over every item in {@code tblItems} (1-based in COBOL,
     *       0-based here) and logs its 1-based index and key.</li>
     *   <li>Performs a linear search for the first item whose key starts with
     *       {@code "AA"} (equivalent to {@code TBL-ITEM-KEY(TBL-IDX)(1:2) = 'AA'}).
     *       Logs a "NOT FOUND" message when no such item exists, or logs the
     *       matching item's index and key when found.</li>
     * </ol>
     * <p>
     * Original COBOL: paragraph {@code TBL-1} in program {@code MEGADEMO}.
     *
     * @param tblItems list of table items to process; must not be {@code null}
     */
    public void tbl1(List<TblItem> tblItems) {

        // ── Phase 1: display every item ──────────────────────────────────────
        // COBOL:
        //   SET TBL-IDX TO 1
        //   PERFORM UNTIL TBL-IDX > TBL-COUNT
        //      DISPLAY 'TBL ITEM (' TBL-IDX ') KEY=' TBL-ITEM-KEY (TBL-IDX)
        //      SET TBL-IDX UP BY 1
        //   END-PERFORM
        int tblCount = (tblItems == null) ? 0 : tblItems.size();

        for (int tblIdx = 1; tblIdx <= tblCount; tblIdx++) {
            TblItem item = tblItems.get(tblIdx - 1);   // convert to 0-based
            log.info("TBL ITEM ({}) KEY={}", tblIdx, item.getKey());
        }

        // ── Phase 2: linear SEARCH for key starting with "AA" ────────────────
        // COBOL:
        //   SEARCH TBL-ITEM
        //      AT END DISPLAY 'LINEAR SEARCH: NOT FOUND'
        //      WHEN TBL-ITEM-KEY (TBL-IDX) (1:2) = 'AA'
        //         DISPLAY 'LINEAR SEARCH: FOUND AT ' TBL-IDX
        //
        // COBOL SEARCH resets TBL-IDX to 1 implicitly (SET TBL-IDX TO 1 is
        // required before SEARCH; we replicate that here).
        boolean found = false;
        for (int tblIdx = 1; tblIdx <= tblCount; tblIdx++) {
            TblItem item = tblItems.get(tblIdx - 1);
            String key = item.getKey();

            // (1:2) in COBOL is a reference modification: characters 1 through 2
            // i.e. the first two characters of the key string.
            String keyPrefix = (key != null && key.length() >= 2)
                    ? key.substring(0, 2)
                    : (key != null ? key : "");

            if ("AA".equals(keyPrefix)) {
                log.info("LINEAR SEARCH: FOUND AT {} KEY={}", tblIdx, key);
                found = true;
                break;
            }
        }

        if (!found) {
            log.info("LINEAR SEARCH: NOT FOUND");
        }
    }

    // ── Inner value-object representing one COBOL TBL-ITEM occurrence ────────

    /**
     * Value object that represents a single occurrence of the COBOL
     * {@code TBL-ITEM} table entry.
     * <p>
     * In the original COBOL this is an {@code OCCURS} group item containing
     * (at minimum) the field {@code TBL-ITEM-KEY PIC X(n)}.
     */
    public static class TblItem {

        /** Corresponds to {@code TBL-ITEM-KEY PIC X(n)}. */
        private String key;

        public TblItem() {
        }

        public TblItem(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        @Override
        public String toString() {
            return "TblItem{key='" + key + "'}";
        }
    }
}