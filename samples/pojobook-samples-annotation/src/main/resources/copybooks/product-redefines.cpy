       01  PRODUCT-RECORD.
           05  PRODUCT-ID              PIC 9(8).
           05  PRODUCT-NAME            PIC X(50).
           05  PRODUCT-TYPE            PIC X.
               88  ELECTRONICS         VALUE 'E'.
               88  CLOTHING            VALUE 'C'.
               88  FOOD                VALUE 'F'.
               88  FURNITURE           VALUE 'U'.
           05  PRODUCT-DETAILS         REDEFINES PRODUCT-TYPE.
               10  ELECTRONICS-INFO.
                   15  WARRANTY-MONTHS PIC 99.
                   15  MODEL-NUMBER    PIC X(20).
               10  CLOTHING-INFO       REDEFINES ELECTRONICS-INFO.
                   15  SIZE            PIC X(10).
                   15  COLOR           PIC X(12).
               10  FOOD-INFO           REDEFINES ELECTRONICS-INFO.
                   15  EXPIRY-DATE     PIC 9(8).
                   15  ORGANIC-FLAG    PIC X.
           05  UNIT-PRICE              PIC S9(7)V99 COMP-3.
           05  QUANTITY-ON-HAND        PIC 9(6) COMP.
           05  REORDER-LEVEL           PIC 9(6) COMP.
           05  SUPPLIER-ID             PIC 9(8).

