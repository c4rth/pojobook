      * COBOL Copybook with Multiple OCCURS Variations
      * Demonstrates fixed arrays, variable arrays, and indexed tables
       01  SALES-ANALYSIS-RECORD.
           05  COMPANY-ID              PIC 9(6).
           05  ANALYSIS-YEAR           PIC 9(4).
           05  ANALYSIS-QUARTER        PIC 9(1).

      * Fixed OCCURS - Monthly sales data
           05  MONTHLY-DATA            OCCURS 12 TIMES.
               10  MONTH-NUMBER        PIC 9(2).
               10  SALES-AMOUNT        PIC S9(13)V99 COMP-3.
               10  UNITS-SOLD          PIC 9(9) COMP.
               10  RETURNS-AMOUNT      PIC S9(11)V99 COMP-3.
               10  RETURNS-COUNT       PIC 9(7) COMP.
               10  NET-SALES           PIC S9(13)V99 COMP-3.

      * Variable OCCURS - Regional data
           05  REGION-COUNT            PIC 9(3) COMP.
           05  REGIONAL-DATA           OCCURS 1 TO 50 TIMES
                                       DEPENDING ON REGION-COUNT.
               10  REGION-CODE         PIC X(5).
               10  REGION-NAME         PIC X(30).
               10  REGION-SALES        PIC S9(13)V99 COMP-3.
               10  STORE-COUNT         PIC 9(4) COMP.

      * Nested OCCURS - Stores within regions
               10  STORE-DATA          OCCURS 1 TO 20 TIMES
                                       DEPENDING ON STORE-COUNT.
                   15  STORE-ID        PIC 9(6).
                   15  STORE-NAME      PIC X(40).
                   15  STORE-SALES     PIC S9(11)V99 COMP-3.
                   15  EMPLOYEES       PIC 9(4) COMP.

      * OCCURS with INDEXED BY
           05  PRODUCT-COUNT           PIC 9(5) COMP.
           05  PRODUCT-TABLE           OCCURS 1 TO 10000 TIMES
                                       DEPENDING ON PRODUCT-COUNT
                                       INDEXED BY PROD-IDX.
               10  PRODUCT-CODE        PIC X(15).
               10  PRODUCT-NAME        PIC X(50).
               10  CATEGORY            PIC X(20).
               10  UNIT-PRICE          PIC S9(9)V99 COMP-3.
               10  QUANTITY-SOLD       PIC 9(9) COMP.
               10  REVENUE             PIC S9(13)V99 COMP-3.

      * OCCURS with ASCENDING KEY (sorted table)
           05  EMPLOYEE-COUNT          PIC 9(4) COMP.
           05  EMPLOYEE-TABLE          OCCURS 1 TO 1000 TIMES
                                       DEPENDING ON EMPLOYEE-COUNT
                                       ASCENDING KEY IS EMP-ID
                                       INDEXED BY EMP-IDX.
               10  EMP-ID              PIC 9(8).
               10  EMP-NAME            PIC X(40).
               10  EMP-TITLE           PIC X(30).
               10  DEPT-CODE           PIC X(10).
               10  HIRE-DATE           PIC 9(8).
               10  SALARY              PIC S9(9)V99 COMP-3.
               10  COMMISSION-PCT      PIC 9V99 COMP-3.

      * OCCURS with multiple ASCENDING KEYS
           05  CUSTOMER-COUNT          PIC 9(6) COMP.
           05  CUSTOMER-TABLE          OCCURS 1 TO 100000 TIMES
                                       DEPENDING ON CUSTOMER-COUNT
                                       ASCENDING KEY IS CUST-STATE
                                                        CUST-CITY
                                                        CUST-ID
                                       INDEXED BY CUST-IDX CUST-IDX2.
               10  CUST-ID             PIC 9(10).
               10  CUST-NAME           PIC X(50).
               10  CUST-STATE          PIC X(2).
               10  CUST-CITY           PIC X(30).
               10  CUST-ZIP            PIC 9(5).
               10  CREDIT-LIMIT        PIC S9(9)V99 COMP-3.
               10  BALANCE             PIC S9(11)V99 COMP-3.

      * Summary totals
           05  TOTAL-SALES             PIC S9(15)V99 COMP-3.
           05  TOTAL-UNITS             PIC 9(11) COMP.
           05  TOTAL-RETURNS           PIC S9(13)V99 COMP-3.
           05  NET-REVENUE             PIC S9(15)V99 COMP-3.

