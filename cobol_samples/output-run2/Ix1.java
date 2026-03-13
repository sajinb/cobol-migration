package com.migration.megademo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.migration.megademo.entity.IxRecord;
import com.migration.megademo.repository.IxFileRepository;

/**
 * Spring Boot service migrated from COBOL program {@code MEGADEMO}.
 *
 * <p>This service encapsulates the logic originally contained in the COBOL
 * indexed-file paragraph {@code IX-1}, which performs a sequence of VSAM/indexed
 * file operations:
 * <ol>
 *   <li>Populate an index record with fixed seed values and write it.</li>
 *   <li>Position the file cursor at or after a start key (START ... KEY >=).</li>
 *   <li>Read the next sequential record.</li>
 *   <li>Rewrite (update) the current record.</li>
 *   <li>Delete the current record.</li>
 * </ol>
 *
 * <p>VSAM file-status codes are mapped to Spring Data / JPA exception handling.
 * The {@code INVALID KEY} / {@code AT END} branches that previously issued
 * {@code DISPLAY} statements are translated to {@code log.error} / {@code log.warn}
 * calls so that the same diagnostic information is preserved in application logs.
 *
 * <p>Original COBOL: paragraph {@code IX-1} in program {@code MEGADEMO}.
 */
@Slf4j
@Service
public class MegademoService {

    private static final String MSG_ERR_PREFIX = "ERROR: ";

    @Autowired
    private IxFileRepository ixFileRepository;

    /**
     * Executes the full sequence of indexed-file operations originally coded in
     * COBOL paragraph {@code IX-1}.
     *
     * <p>The method:
     * <ol>
     *   <li>Builds a seed {@link IxRecord} and persists it (WRITE IX-REC).</li>
     *   <li>Locates the first record whose key is &gt;= {@code wsIxStartKey}
     *       (START IXFILE KEY &gt;=).</li>
     *   <li>Reads the next record sequentially (READ IXFILE NEXT RECORD).</li>
     *   <li>Rewrites (updates) that record (REWRITE IX-REC).</li>
     *   <li>Deletes that record (DELETE IXFILE RECORD).</li>
     * </ol>
     *
     * @param wsIxStartKey the start-key value used for the positioned START
     *                     operation (maps to {@code WS-IX-START-KEY} in
     *                     WORKING-STORAGE)
     * @return the {@link IxRecord} that was read, rewritten, and then deleted,
     *         or {@code null} when no record at-or-after {@code wsIxStartKey}
     *         exists (AT END condition)
     */
    @Transactional
    public IxRecord ix1(String wsIxStartKey) {

        // ------------------------------------------------------------------ //
        // MOVE 'K000000001' TO IX-KEY                                         //
        // MOVE 'AK001'      TO IX-AK1                                         //
        // MOVE 1111.11      TO IX-AMT                                         //
        // MOVE 20260305     TO IX-DATE                                         //
        // ------------------------------------------------------------------ //
        IxRecord ixRec = new IxRecord();
        ixRec.setIxKey("K000000001");
        ixRec.setIxAk1("AK001");
        ixRec.setIxAmt(new BigDecimal("1111.11").setScale(2, RoundingMode.HALF_UP));
        ixRec.setIxDate(20260305);

        // ------------------------------------------------------------------ //
        // WRITE IX-REC INVALID KEY                                            //
        //   DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX            //
        // END-WRITE                                                           //
        // ------------------------------------------------------------------ //
        try {
            ixFileRepository.save(ixRec);
            log.debug("IX WRITE succeeded for key={}", ixRec.getIxKey());
        } catch (Exception e) {
            // Mirrors: DISPLAY MSG-ERR-PREFIX 'IX WRITE FAILED FS=' WS-FS-IX
            log.error("{}IX WRITE FAILED FS={}", MSG_ERR_PREFIX, e.getMessage());
            // Return null — processing cannot continue without a written record
            return null;
        }

        // ------------------------------------------------------------------ //
        // START IXFILE KEY >= WS-IX-START-KEY                                 //
        //   INVALID KEY CONTINUE                                              //
        // END-START                                                           //
        //                                                                     //
        // Locate the first record whose primary key >= wsIxStartKey.          //
        // INVALID KEY => CONTINUE, so we simply log a warning and return null.//
        // ------------------------------------------------------------------ //
        List<IxRecord> candidates = ixFileRepository.findByIxKeyGreaterThanEqualOrderByIxKeyAsc(wsIxStartKey);

        if (candidates == null || candidates.isEmpty()) {
            // AT END / INVALID KEY — CONTINUE in COBOL means carry on silently,
            // but there is nothing left to rewrite or delete.
            log.warn("{}IX START found no record with key >= '{}'", MSG_ERR_PREFIX, wsIxStartKey);
            return null;
        }

        // ------------------------------------------------------------------ //
        // READ IXFILE NEXT RECORD AT END CONTINUE END-READ                   //
        //                                                                     //
        // Take the first record in key order at-or-after the start key.      //
        // ------------------------------------------------------------------ //
        IxRecord currentRecord = candidates.get(0);
        log.debug("IX READ NEXT returned key={}", currentRecord.getIxKey());

        // ------------------------------------------------------------------ //
        // REWRITE IX-REC INVALID KEY                                          //
        //   DISPLAY MSG-ERR-PREFIX 'IX REWRITE FAILED FS=' WS-FS-IX          //
        // END-REWRITE                                                         //
        //                                                                     //
        // The COBOL REWRITE updates the record that was just READ.            //
        // In JPA the record is already managed; calling save() issues UPDATE. //
        // ------------------------------------------------------------------ //
        try {
            // Copy the in-memory ixRec fields into the record that was read,
            // mirroring the COBOL behaviour where IX-REC is the shared buffer
            // used for both WRITE and REWRITE.
            currentRecord.setIxAk1(ixRec.getIxAk1());
            currentRecord.setIxAmt(ixRec.getIxAmt());
            currentRecord.setIxDate(ixRec.getIxDate());

            ixFileRepository.save(currentRecord);
            log.debug("IX REWRITE succeeded for key={}", currentRecord.getIxKey());
        } catch (Exception e) {
            // Mirrors: DISPLAY MSG-ERR-PREFIX 'IX REWRITE FAILED FS=' WS-FS-IX
            log.error("{}IX REWRITE FAILED FS={}", MSG_ERR_PREFIX, e.getMessage());
            return null;
        }

        // ------------------------------------------------------------------ //
        // DELETE IXFILE RECORD INVALID KEY                                    //
        //   DISPLAY MSG-ERR-PREFIX 'IX DELETE FAILED FS=' WS-FS-IX           //
        // END-DELETE                                                          //
        // ------------------------------------------------------------------ //
        try {
            ixFileRepository.delete(currentRecord);
            log.debug("IX DELETE succeeded for key={}", currentRecord.getIxKey());
        } catch (Exception e) {
            // Mirrors: DISPLAY MSG-ERR-PREFIX 'IX DELETE FAILED FS=' WS-FS-IX
            log.error("{}IX DELETE FAILED FS={}", MSG_ERR_PREFIX, e.getMessage());
            return null;
        }

        // Return the record that was processed (read, rewritten, deleted).
        return currentRecord;
    }
}