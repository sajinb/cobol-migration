package com.migration.megademo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MegademoService {

    /**
     * Handles the redirected ALTER flow by logging a notification message.
     * <p>Original COBOL: paragraph {@code NEW-PARAGRAPH} in program {@code MEGADEMO}.
     * <p>In the original COBOL program, an ALTER statement was used to redirect
     * control flow to this paragraph. When execution arrives here, a message is
     * displayed indicating that the ALTER redirection has taken place.
     */
    public void newParagraph() {
        log.info("ALTER redirected here.");
    }
}