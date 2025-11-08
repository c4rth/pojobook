      * Comprehensive COBOL Copybook demonstrating all language features
       01  MASTER-RECORD.
      *
      * Basic data types with various USAGE clauses
      *
           05  RECORD-KEY.
               10  COMPANY-CODE        PIC X(4).
               10  RECORD-TYPE         PIC X(2) VALUE 'MR'.
               10  SEQUENCE-NUMBER     PIC 9(10).

           05  RECORD-HEADER.
               10  CREATE-DATE         PIC 9(8).
               10  CREATE-TIME         PIC 9(6).
               10  CREATE-USER         PIC X(20).
               10  UPDATE-DATE         PIC 9(8).
               10  UPDATE-TIME         PIC 9(6).
               10  UPDATE-USER         PIC X(20).
               10  VERSION-NUMBER      PIC 9(4) COMP VALUE 1.

      *
      * Numeric fields with different USAGE types
      *
           05  NUMERIC-FIELDS.
               10  DISPLAY-NUMBER      PIC 9(9) DISPLAY.
               10  COMP-NUMBER         PIC 9(9) COMP.
               10  COMP-3-NUMBER       PIC S9(11)V99 COMP-3.
               10  COMP-1-NUMBER       COMP-1.
               10  COMP-2-NUMBER       COMP-2.
               10  COMP-5-NUMBER       PIC 9(9) COMP-5.
               10  BINARY-NUMBER       PIC 9(9) BINARY.
               10  PACKED-NUMBER       PIC S9(9)V99 PACKED-DECIMAL.

      *
      * Text fields with justification and padding
      *
           05  TEXT-FIELDS.
               10  LEFT-JUSTIFIED      PIC X(30).
               10  RIGHT-JUSTIFIED     PIC X(30) JUSTIFIED RIGHT.
               10  PADDED-FIELD        PIC X(20) VALUE SPACES.
               10  CONSTANT-TEXT       PIC X(10) VALUE 'CONSTANT'.

      *
      * Fields with BLANK WHEN ZERO
      *
           05  DISPLAY-FIELDS.
               10  AMOUNT-1            PIC Z(7)9.99.
               10  AMOUNT-2            PIC 9(9) BLANK WHEN ZERO.
               10  COUNTER             PIC 9(5) BLANK WHEN ZERO.

      *
      * SYNCHRONIZED fields for alignment
      *
           05  ALIGNED-FIELDS          SYNCHRONIZED.
               10  SYNC-COMP           PIC 9(9) COMP.
               10  SYNC-COMP-3         PIC S9(9)V99 COMP-3.
               10  FILLER              PIC X(3).
               10  SYNC-BINARY         PIC 9(9) BINARY SYNC.

      *
      * Union-like structure with REDEFINES
      *
           05  DATA-UNION              PIC X(100).
           05  DATA-AS-TEXT REDEFINES DATA-UNION.
               10  TEXT-LINE           PIC X(80).
               10  TEXT-PADDING        PIC X(20).
           05  DATA-AS-NUMBERS REDEFINES DATA-UNION.
               10  NUMBER-ARRAY        OCCURS 20 TIMES.
                   15  NUMBER-VALUE    PIC 9(5).
           05  DATA-AS-MIXED REDEFINES DATA-UNION.
               10  MIXED-CODE          PIC X(10).
               10  MIXED-AMOUNT        PIC S9(11)V99 COMP-3.
               10  MIXED-DATE          PIC 9(8).
               10  MIXED-FLAGS.
                   15  FLAG-1          PIC X.
                       88  FLAG-1-ON   VALUE 'Y'.
                       88  FLAG-1-OFF  VALUE 'N'.
                   15  FLAG-2          PIC X.
                   15  FLAG-3          PIC X.
               10  FILLER              PIC X(70).

      *
      * Fixed array with all OCCURS features
      *
           05  QUARTERLY-DATA          OCCURS 4 TIMES
                                       INDEXED BY QTR-IDX.
               10  QUARTER-NUMBER      PIC 9(1).
               10  QUARTER-SALES       PIC S9(13)V99 COMP-3.
               10  QUARTER-EXPENSES    PIC S9(13)V99 COMP-3.
               10  QUARTER-PROFIT      PIC S9(13)V99 COMP-3.

      *
      * Variable array with DEPENDING ON
      *
           05  DETAIL-COUNT            PIC 9(4) COMP.
           05  DETAIL-RECORDS          OCCURS 1 TO 999 TIMES
                                       DEPENDING ON DETAIL-COUNT
                                       INDEXED BY DTL-IDX.
               10  DETAIL-TYPE         PIC X(3).
               10  DETAIL-CODE         PIC X(10).
               10  DETAIL-AMOUNT       PIC S9(11)V99 COMP-3.
               10  DETAIL-DATE         PIC 9(8).
               10  DETAIL-NOTES        PIC X(50).

      *
      * Sorted table with ASCENDING KEY
      *
           05  REFERENCE-COUNT         PIC 9(3) COMP.
           05  REFERENCE-TABLE         OCCURS 1 TO 100 TIMES
                                       DEPENDING ON REFERENCE-COUNT
                                       ASCENDING KEY IS REF-CODE
                                       INDEXED BY REF-IDX.
               10  REF-CODE            PIC X(8).
               10  REF-DESCRIPTION     PIC X(40).
               10  REF-VALUE           PIC S9(9)V99 COMP-3.
               10  REF-ACTIVE          PIC X.
                   88  REF-IS-ACTIVE   VALUE 'Y'.
                   88  REF-INACTIVE    VALUE 'N'.

      *
      * Nested OCCURS structures
      *
           05  DEPT-COUNT              PIC 9(2) COMP.
           05  DEPARTMENT-DATA         OCCURS 1 TO 10 TIMES
                                       DEPENDING ON DEPT-COUNT.
               10  DEPT-ID             PIC X(5).
               10  DEPT-NAME           PIC X(30).
               10  EMP-COUNT           PIC 9(3) COMP.
               10  EMPLOYEE-LIST       OCCURS 1 TO 100 TIMES
                                       DEPENDING ON EMP-COUNT.
                   15  EMP-ID          PIC 9(8).
                   15  EMP-NAME        PIC X(40).
                   15  EMP-SALARY      PIC S9(9)V99 COMP-3.

      *
      * Conditional values (88 levels)
      *
           05  STATUS-CODE             PIC X(2).
               88  STATUS-ACTIVE       VALUE 'AC'.
               88  STATUS-INACTIVE     VALUE 'IN'.
               88  STATUS-PENDING      VALUE 'PE'.
               88  STATUS-DELETED      VALUE 'DE'.
               88  STATUS-VALID        VALUES 'AC' 'IN' 'PE'.

      *
      * Multiple FILLERs for spacing
      *
           05  FILLER                  PIC X(10).
           05  CONTROL-FLAGS.
               10  PROCESSING-FLAG     PIC X.
               10  FILLER              PIC X(5).
               10  AUDIT-FLAG          PIC X.
               10  FILLER              PIC X(3).
           05  FILLER                  PIC X(20).

      *
      * Trailer information
      *
           05  RECORD-TRAILER.
               10  CHECKSUM            PIC 9(10) COMP.
               10  RECORD-LENGTH       PIC 9(5) COMP.
               10  END-MARKER          PIC X(4) VALUE 'END*'.
      * Complex COBOL Copybook with REDEFINES
      * Demonstrates conditional data structures
       01  TRANSACTION-RECORD.
           05  TRANS-ID                PIC 9(10).
           05  TRANS-TYPE              PIC X(2).
               88  PAYMENT-TRANS       VALUE 'PM'.
               88  REFUND-TRANS        VALUE 'RF'.
               88  ADJUSTMENT-TRANS    VALUE 'AD'.
           05  TRANS-DATE              PIC 9(8).
           05  TRANS-TIME              PIC 9(6).

      * Main transaction data - different layouts based on type
           05  TRANS-DATA.
               10  COMMON-AMOUNT       PIC S9(11)V99 COMP-3.
               10  COMMON-CURRENCY     PIC X(3).
               10  SPECIFIC-DATA       PIC X(100).

      * REDEFINES for PAYMENT transactions
           05  PAYMENT-DATA REDEFINES TRANS-DATA.
               10  PAYMENT-AMOUNT      PIC S9(11)V99 COMP-3.
               10  PAYMENT-CURRENCY    PIC X(3).
               10  PAYMENT-METHOD      PIC X(2).
               10  CARD-NUMBER         PIC 9(16).
               10  CARDHOLDER-NAME     PIC X(30).
               10  EXPIRY-DATE         PIC 9(4).
               10  CVV                 PIC 9(3).
               10  FILLER              PIC X(45).

      * REDEFINES for REFUND transactions
           05  REFUND-DATA REDEFINES TRANS-DATA.
               10  REFUND-AMOUNT       PIC S9(11)V99 COMP-3.
               10  REFUND-CURRENCY     PIC X(3).
               10  ORIGINAL-TRANS-ID   PIC 9(10).
               10  REFUND-REASON       PIC X(50).
               10  APPROVED-BY         PIC X(20).
               10  FILLER              PIC X(17).

      * REDEFINES for ADJUSTMENT transactions
           05  ADJUSTMENT-DATA REDEFINES TRANS-DATA.
               10  ADJUSTMENT-AMOUNT   PIC S9(11)V99 COMP-3.
               10  ADJUSTMENT-CURRENCY PIC X(3).
               10  ADJUSTMENT-TYPE     PIC X(10).
               10  REFERENCE-ID        PIC X(20).
               10  NOTES               PIC X(67).

      * Transaction history with variable length
           05  HISTORY-COUNT           PIC 9(3) COMP.
           05  HISTORY-ENTRIES         OCCURS 1 TO 100 TIMES
                                       DEPENDING ON HISTORY-COUNT
                                       INDEXED BY HIST-IDX.
               10  HIST-DATE           PIC 9(8).
               10  HIST-TIME           PIC 9(6).
               10  HIST-ACTION         PIC X(10).
               10  HIST-USER           PIC X(20).

      * Optional fields
           05  STATUS-CODE             PIC X(2).
           05  ERROR-MESSAGE           PIC X(100).
           05  PROCESSED-BY            PIC X(30).
           05  PROCESSED-DATE          PIC 9(8).

