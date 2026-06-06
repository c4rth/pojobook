       01  EMPLOYEE-RECORD.
           05  EMPLOYEE-ID             PIC 9(8).
           05  EMPLOYEE-NAME.
               10  FIRST-NAME          PIC X(20).
               10  MIDDLE-INITIAL      PIC X.
               10  LAST-NAME           PIC X(30).
           05  DATE-OF-BIRTH           PIC 9(8).
           05  HIRE-DATE               PIC 9(8).
           05  DEPARTMENT              PIC X(20).
           05  JOB-TITLE               PIC X(30).
           05  SALARY                  PIC S9(7)V99 COMP-3.
           05  BONUS                   PIC S9(5)V99 COMP-3.
           05  EMPLOYMENT-STATUS       PIC X.
               88  ACTIVE              VALUE 'A'.
               88  INACTIVE            VALUE 'I'.
               88  TERMINATED          VALUE 'T'.
           05  PHONE-NUMBER            PIC X(15).
           05  EMAIL                   PIC X(50).

