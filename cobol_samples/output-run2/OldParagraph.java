package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Displays a static message to the console/log.
     * <p>Original COBOL: paragraph {@code OLD-PARAGRAPH} in program {@code MEGADEMO}.
     */
    public void oldParagraph() {
        log.info("This will be altered.");
    }
}