       01  SALES-REPORT.
           05  REPORT-DATE             PIC 9(8).
           05  REPORT-PERIOD           PIC X(20).
           05  TOTAL-SALES             PIC S9(11)V99 COMP-3.
           05  TRANSACTION-COUNT       PIC 9(6) COMP.
           05  DAILY-TRANSACTIONS      OCCURS 31 TIMES.
               10  DAY-OF-MONTH        PIC 99.
               10  DAY-SALES           PIC S9(9)V99 COMP-3.
               10  DAY-COUNT           PIC 9(5) COMP.
           05  TOP-PRODUCTS            OCCURS 10 TIMES.
               10  PRODUCT-ID          PIC 9(8).
               10  PRODUCT-NAME        PIC X(30).
               10  QUANTITY-SOLD       PIC 9(6) COMP.
               10  REVENUE             PIC S9(9)V99 COMP-3.
       01  CUSTOMER-RECORD.
           05  CUSTOMER-ID             PIC 9(10).
           05  CUSTOMER-NAME           PIC X(50).
           05  CUSTOMER-ADDRESS.
               10  STREET              PIC X(40).
               10  CITY                PIC X(30).
               10  STATE               PIC X(2).
               10  ZIP-CODE            PIC 9(5).
           05  ACCOUNT-INFO.
               10  ACCOUNT-BALANCE     PIC S9(7)V99 COMP-3.
               10  CREDIT-LIMIT        PIC S9(7)V99 COMP-3.
               10  ACCOUNT-STATUS      PIC X.
           05  LAST-ORDER-DATE         PIC 9(8).
           05  ORDER-COUNT             PIC 9(5) COMP.

