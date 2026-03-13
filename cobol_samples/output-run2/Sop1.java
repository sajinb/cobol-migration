package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 *
 * <p>This service provides functionality equivalent to the COBOL sort-file processing
 * paragraphs {@code SOP-1} and {@code SOP-EXIT}.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Processes a list of sorted records, iterating through each record and logging
     * its key and data fields.
     *
     * <p>Original COBOL: paragraph {@code SOP-1} in program {@code MEGADEMO}.
     *
     * <p>The COBOL paragraph used a {@code RETURN SORT-FILE RECORD INTO SORT-REC}
     * loop construct: it repeatedly returned records from the sort file into the
     * {@code SORT-REC} structure (composed of {@code S-KEY} and {@code S-DATA}),
     * displaying each one, and branched back to itself until the {@code AT END}
     * condition was reached (equivalent to end-of-file on the sort output), at which
     * point it fell through to {@code SOP-EXIT} (i.e., simply returned).
     *
     * @param sortedRecords the list of {@link SortRecord} objects representing the
     *                      records returned from the sort file in sorted order;
     *                      must not be {@code null}
     */
    public void sop1(List<SortRecord> sortedRecords) {
        if (sortedRecords == null) {
            log.warn("sop1: sortedRecords list is null — treating as empty (AT END condition).");
            return;
        }

        for (SortRecord sortRec : sortedRecords) {
            // DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
            log.info("SORTED:{} -> {}", sortRec.getSKey(), sortRec.getSData());
            System.out.printf("SORTED:%s -> %s%n", sortRec.getSKey(), sortRec.getSData());
        }
        // AT END GO TO SOP-EXIT — falls through naturally when the list is exhausted
    }

    // -------------------------------------------------------------------------
    // Inner record class representing SORT-REC (S-KEY + S-DATA)
    // -------------------------------------------------------------------------

    /**
     * Represents the {@code SORT-REC} working-storage structure from COBOL program
     * {@code MEGADEMO}, containing the two elementary items {@code S-KEY} (PIC X)
     * and {@code S-DATA} (PIC X).
     */
    public static class SortRecord {

        /** Corresponds to COBOL {@code S-KEY}. */
        private String sKey;

        /** Corresponds to COBOL {@code S-DATA}. */
        private String sData;

        /**
         * Default no-argument constructor.
         */
        public SortRecord() {
        }

        /**
         * Convenience constructor.
         *
         * @param sKey  the sort key field value
         * @param sData the sort data field value
         */
        public SortRecord(String sKey, String sData) {
            this.sKey = sKey;
            this.sData = sData;
        }

        /**
         * Returns the sort key ({@code S-KEY}).
         *
         * @return sort key string
         */
        public String getSKey() {
            return sKey;
        }

        /**
         * Sets the sort key ({@code S-KEY}).
         *
         * @param sKey sort key string
         */
        public void setSKey(String sKey) {
            this.sKey = sKey;
        }

        /**
         * Returns the sort data ({@code S-DATA}).
         *
         * @return sort data string
         */
        public String getSData() {
            return sData;
        }

        /**
         * Sets the sort data ({@code S-DATA}).
         *
         * @param sData sort data string
         */
        public void setSData(String sData) {
            this.sData = sData;
        }

        @Override
        public String toString() {
            return "SortRecord{sKey='" + sKey + "', sData='" + sData + "'}";
        }
    }
}