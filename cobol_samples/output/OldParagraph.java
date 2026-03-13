```java
/**
 * Migrated from COBOL program: MEGADEMO
 * Paragraph: OLD-PARAGRAPH
 *
 * Original behaviour: Displays the message "This will be altered."
 * to standard output. This paragraph contained no data item dependencies,
 * no PERFORM calls, no external program calls, and no DB2 table access.
 *
 * Note: In the original COBOL source this paragraph was named OLD-PARAGRAPH,
 * suggesting it may have been subject to an ALTER statement at runtime that
 * redirected control flow to a different paragraph. No such ALTER target was
 * present in the supplied context, so the literal display logic is preserved
 * as-is. If an ALTER target is identified during further analysis, this method
 * should be updated accordingly.
 */
public void oldParagraph() {
    System.out.println("This will be altered.");
}
```