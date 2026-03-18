IDENTIFICATION DIVISION.
       PROGRAM-ID. PAY-LOOP.
       
       DATA DIVISION.
       WORKING-STORAGE SECTION.
       01  WS-COUNTERS.
           05  WS-ITERATION       PIC 9(1) VALUE 1.
           05  WS-MAX-EMPLOYEES   PIC 9(1) VALUE 3.
       
       01  WS-EMPLOYEE-DATA.
           05  WS-EMP-NAME        PIC X(20).
           05  WS-HOURS           PIC 9(2)V99.
           05  WS-RATE            PIC 9(3)V99.
           05  WS-GROSS           PIC 9(5)V99.
           05  WS-DISPLAY-PAY     PIC $ZZ,ZZ9.99.

       PROCEDURE DIVISION.
       0100-MAIN.
           DISPLAY "--- STARTING BATCH PROCESSING ---"
           
           PERFORM 0200-PROCESS-EMPLOYEE 
               UNTIL WS-ITERATION > WS-MAX-EMPLOYEES
               
           DISPLAY "--- PROCESSING COMPLETE ---"
           STOP RUN.

       0200-PROCESS-EMPLOYEE.
           DISPLAY "PROCESSING RECORD #" WS-ITERATION
      * In a real app, you'd read from a file here. 
      * For this sample, we'll just hardcode some values based on the loop count.
           IF WS-ITERATION = 1
               MOVE "ALICE SMITH" TO WS-EMP-NAME
               MOVE 40.00 TO WS-HOURS
               MOVE 30.00 TO WS-RATE
           ELSE IF WS-ITERATION = 2
               MOVE "BOB JONES"   TO WS-EMP-NAME
               MOVE 35.00 TO WS-HOURS
               MOVE 20.00 TO WS-RATE
           ELSE
               MOVE "CHARLIE BROWN" TO WS-EMP-NAME
               MOVE 45.00 TO WS-HOURS
               MOVE 25.00 TO WS-RATE
           END-IF

           MULTIPLY WS-HOURS BY WS-RATE GIVING WS-GROSS
           MOVE WS-GROSS TO WS-DISPLAY-PAY
           
           DISPLAY "NAME: " WS-EMP-NAME " | PAY: " WS-DISPLAY-PAY
           
           ADD 1 TO WS-ITERATION.