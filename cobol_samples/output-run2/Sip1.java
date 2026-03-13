package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 *
 * <p>This service contains the logic originally implemented in the COBOL paragraph
 * {@code SIP-1}, which releases (writes) a sort record from the input sequential
 * record into the sort work file. In the Java equivalent, this is modelled as
 * passing the input record string to the sort collector (a {@link java.util.List})
 * that accumulates records before sorting — mirroring the COBOL SORT/RELEASE
 * mechanism.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Releases (transfers) the current input-sequential record into the sort
     * record collector, mirroring the COBOL statement:
     * <pre>
     *   RELEASE SORT-REC FROM INSEQ-REC
     * </pre>
     * In COBOL, {@code RELEASE} moves the contents of {@code INSEQ-REC} into
     * {@code SORT-REC} and passes it to the sort work file for subsequent
     * processing. In Java, this is represented by copying the input record value
     * into the sort record holder and adding it to the sort input list.
     *
     * <p>Original COBOL: paragraph {@code SIP-1} in program {@code MEGADEMO}.
     *
     * @param inseqRec  the current input sequential record to be released into
     *                  the sort work file (maps to {@code INSEQ-REC})
     * @param sortInput the mutable list acting as the sort work file input queue;
     *                  the record is appended here (maps to the SORT work file
     *                  fed by {@code RELEASE SORT-REC})
     * @return          the sort record value that was released (maps to
     *                  {@code SORT-REC} after the RELEASE)
     */
    public String sip1(String inseqRec, java.util.List<String> sortInput) {
        log.debug("SIP-1: Releasing INSEQ-REC into SORT-REC. inseqRec=[{}]", inseqRec);

        // RELEASE SORT-REC FROM INSEQ-REC
        // Move INSEQ-REC into SORT-REC (the FROM phrase performs an implicit MOVE)
        String sortRec = inseqRec;

        // Add the sort record to the sort input collection, which represents
        // the COBOL sort work file receiving the released record.
        sortInput.add(sortRec);

        log.debug("SIP-1: SORT-REC released successfully. sortInput size=[{}]", sortInput.size());

        return sortRec;
    }
}