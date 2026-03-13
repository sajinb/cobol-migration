package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Displays the message "GO TO DEPENDING ON -> 3" to standard output.
     * <p>Original COBOL: paragraph {@code LABEL-3} in program {@code MEGADEMO}.
     */
    public void label3() {
        System.out.println("GO TO DEPENDING ON -> 3");
    }
}