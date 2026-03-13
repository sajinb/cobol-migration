```java
/**
 * Migrated from COBOL program: MEGADEMO, paragraph: LABEL-3.
 *
 * <p>Original behaviour: This paragraph represents the third branch target of a
 * GO TO ... DEPENDING ON construct. When control reaches this label the program
 * simply displays the diagnostic message "GO TO DEPENDING ON -> 3", indicating
 * that the computed GO TO resolved to case 3.
 *
 * <p>No working-storage variables are read or written, and no external calls or
 * DB2 access are performed.
 */
public void label3() {
    System.out.println("GO TO DEPENDING ON -> 3");
}
```