       01  CUSTOMER-RECORD.
           05  CUSTOMER-ID           PIC 9(5).
           05  CUSTOMER-NAME         PIC X(20).
           05  CUSTOMER-TYPE         PIC X.
               88  REGULAR-CUSTOMER     VALUE 'R'.
               88  PREMIUM-CUSTOMER     VALUE 'P'.
               88  VIP-CUSTOMER         VALUE 'V'.
           05  CUSTOMER-STATUS       PIC X.
               88  ACTIVE-CUSTOMER      VALUE 'A'.
               88  INACTIVE-CUSTOMER    VALUE 'I'.
               88  SUSPENDED-CUSTOMER   VALUE 'S'.
           05  CUSTOMER-BALANCE      PIC 9(7)V99.
