       01  EMPLOYEE-RECORD.
           05  EMPLOYEE-ID             PIC 9(8).
           05  FIRST-NAME              PIC X(20).
           05  LAST-NAME               PIC X(30).
           05  DEPARTMENT              PIC X(4).
           05  SALARY                  PIC S9(7)V99 COMP-3.
           05  HIRE-DATE               PIC 9(8).
           05  PHONE-NUMBERS           OCCURS 3 TIMES.
               10  PHONE-TYPE          PIC X(10).
               10  PHONE-NUMBER        PIC X(15).
           05  SKILLS                  OCCURS 10 TIMES.
               10  SKILL-NAME          PIC X(20).
               10  SKILL-LEVEL         PIC 9(1).

