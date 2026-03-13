package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 * <p>
 * This class contains the logic for the {@code SORT-OUT-PROC} section and its
 * body paragraph {@code SOP-1}, which iterates over a sorted file's records,
 * printing each key/data pair until the end-of-file condition is reached.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Represents a single record returned from the SORT-FILE.
     * Maps to the COBOL {@code SORT-REC} structure containing {@code S-KEY} and {@code S-DATA}.
     */
    public static class SortRec {
        private final String sKey;
        private final String sData;

        public SortRec(String sKey, String sData) {
            this.sKey = sKey;
            this.sData = sData;
        }

        public String getSKey() {
            return sKey;
        }

        public String getSData() {
            return sData;
        }
    }

    /**
     * SORT-OUT-PROC section entry point — delegates to {@code sop1()}.
     * <p>
     * Original COBOL: section {@code SORT-OUT-PROC} in program {@code MEGADEMO}.
     * The section simply transfers control to the section body paragraph {@code SOP-1}.
     *
     * @param sortedRecords the list of records returned from the sort file, in sorted order.
     */
    public void sortOutProc(List<SortRec> sortedRecords) {
        sop1(sortedRecords);
    }

    /**
     * Iterates over all sorted records, displaying each key and data value.
     * <p>
     * Original COBOL: paragraph {@code SOP-1} in program {@code MEGADEMO}.
     * <pre>
     *   SOP-1.
     *     RETURN SORT-FILE RECORD INTO SORT-REC AT END GO TO SOP-EXIT
     *     DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
     *     GO TO SOP-1
     * </pre>
     * The COBOL paragraph loops via {@code GO TO SOP-1} and exits via {@code GO TO SOP-EXIT}
     * when the AT END condition is raised (i.e., no more records). This is modelled as a
     * standard Java for-each loop over the pre-populated sorted record list.
     *
     * @param sortedRecords the list of records returned from the sort file, in sorted order.
     */
    public void sop1(List<SortRec> sortedRecords) {
        for (SortRec sortRec : sortedRecords) {
            // DISPLAY 'SORTED:' S-KEY ' -> ' S-DATA
            log.info("SORTED:{} -> {}", sortRec.getSKey(), sortRec.getSData());
        }
        // AT END condition reached — falls through to SOP-EXIT (method return)
    }
}