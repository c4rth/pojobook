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

