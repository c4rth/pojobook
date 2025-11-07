       01  BANKING-TRANSACTION.
           05  TRANSACTION-ID          PIC 9(12).
           05  ACCOUNT-NUMBER          PIC 9(10).
           05  TRANSACTION-TYPE        PIC X.
               88  DEPOSIT             VALUE 'D'.
               88  WITHDRAWAL          VALUE 'W'.
               88  TRANSFER            VALUE 'T'.
               88  PAYMENT             VALUE 'P'.
           05  TRANSACTION-DATE        PIC 9(8).
           05  TRANSACTION-TIME        PIC 9(6).
           05  AMOUNT                  PIC S9(11)V99 COMP-3.
           05  BALANCE-BEFORE          PIC S9(11)V99 COMP-3.
           05  BALANCE-AFTER           PIC S9(11)V99 COMP-3.
           05  DESCRIPTION             PIC X(100).
           05  BRANCH-CODE             PIC X(6).
           05  TELLER-ID               PIC X(10).
           05  AUTHORIZATION-CODE      PIC X(20).
           05  STATUS                  PIC X.
               88  COMPLETED           VALUE 'C'.
               88  PENDING             VALUE 'P'.
               88  FAILED              VALUE 'F'.
               88  REVERSED            VALUE 'R'.

