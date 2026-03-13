package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Terminates the current program unit and returns control to the caller.
     * In COBOL, {@code GOBACK} causes the program to stop executing and return
     * to whatever invoked it (the operating system, a calling program, or the
     * CICS/batch runtime). In a Spring Boot context this is modelled as a normal
     * method return; any cleanup or shutdown signalling required by the broader
     * application should be handled by the caller.
     *
     * <p>Original COBOL: paragraph {@code GOBACK} in program {@code MEGADEMO}.
     */
    public void goBack() {
        log.debug("GOBACK reached — returning control to caller.");
        // GOBACK in COBOL simply returns to the invoking environment.
        // In Java this is represented by a plain method return.
    }
}