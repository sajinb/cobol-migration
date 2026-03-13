package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 *
 * <p>This class contains the migration of the {@code SORT-IN-PROC} section and its
 * subordinate paragraph {@code SIP-1}.
 *
 * <p>{@code SORT-IN-PROC} is a COBOL SORT input procedure section entry point that
 * feeds records into a SORT operation by releasing each input sequential record
 * ({@code INSEQ-REC}) into the sort work file ({@code SORT-REC}) via the COBOL
 * {@code RELEASE} verb.  In Java this is modelled as a simple delegation from the
 * section entry-point method to the section-body method.
 */
@Slf4j
@Service
public class MegademoService {

    /**
     * Entry point for the {@code SORT-IN-PROC} section.
     *
     * <p>In the original COBOL the SORT input procedure begins here and immediately
     * falls through to {@code SIP-1}, which performs the actual {@code RELEASE}
     * statement.  This method replicates that delegation.
     *
     * <p>Original COBOL: section {@code SORT-IN-PROC} in program {@code MEGADEMO}.
     *
     * @param inseqRec the current input sequential record that is to be released
     *                 into the sort work area (maps to {@code INSEQ-REC}, PIC X)
     * @return the sort record value produced by {@code SIP-1} (maps to
     *         {@code SORT-REC} after the {@code RELEASE} statement)
     */
    public String sortInProc(String inseqRec) {
        log.debug("SORT-IN-PROC entered with inseqRec='{}'", inseqRec);
        return sip1(inseqRec);
    }

    /**
     * Implements paragraph {@code SIP-1} — the body of the {@code SORT-IN-PROC}
     * section.
     *
     * <p>The original COBOL statement is:
     * <pre>
     *   SIP-1.
     *       RELEASE SORT-REC FROM INSEQ-REC
     * </pre>
     *
     * <p>The {@code RELEASE … FROM} verb copies the sending field ({@code INSEQ-REC})
     * into the sort record area ({@code SORT-REC}) and then makes that record
     * available to the SORT facility.  In Java, where there is no runtime SORT
     * facility to call, the equivalent behaviour is to return the copied record
     * value to the caller so that the caller (typically a sort-input loop) can
     * add it to the collection being sorted.
     *
     * <p>Original COBOL: paragraph {@code SIP-1} in program {@code MEGADEMO}.
     *
     * @param inseqRec the input record to be released into the sort work area
     *                 (maps to {@code INSEQ-REC})
     * @return the value that was moved into {@code SORT-REC} before the release
     */
    public String sip1(String inseqRec) {
        log.debug("SIP-1: RELEASE SORT-REC FROM INSEQ-REC — inseqRec='{}'", inseqRec);

        // RELEASE SORT-REC FROM INSEQ-REC
        // The FROM clause implicitly moves INSEQ-REC into SORT-REC before
        // releasing it to the sort facility.  We model SORT-REC as a local
        // variable and return it so the caller can accumulate records for sorting.
        String sortRec = inseqRec;

        log.debug("SIP-1: sortRec set to '{}'", sortRec);
        return sortRec;
    }
}