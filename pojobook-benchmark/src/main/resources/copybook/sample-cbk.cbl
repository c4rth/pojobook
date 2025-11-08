001800     05  WS-SAMPLECBK-INPUT.
001900       10  WS-COMMON-INPUT.
002000         20  NM-CALLER                PIC X(0008).
002100         20  NM-BB                    PIC X(0008).
002200         20  CO-TRANSMISSION          PIC X(0001).
002300         20  NM-FUNCTION              PIC X(0008).
002600           88  CONSULT                VALUE 'CONSULT '.
002800       10  WS-VARIABLE-INPUT.
002900         20  WS-REQUIRED-INPUT.
003300           30  WS-CO-TYPREQ           PIC X(0001).
003300           30  WS-NS-ID               PIC 9(0011).
003300           30  WS-DA-PUR              PIC X(0008).
003300           30  WS-TE-LOGO             PIC X(0080).
003300           30  WS-TE-BRAND            PIC X(0080).
003300           30  WS-TE-CATEG            PIC X(0030).
003300           30  WS-TE-1LETTER          PIC X(0001).
003300           30  WS-TE-GEOCOORD         PIC X(0025).
003300           30  WS-TE-FORMADR          PIC X(0080).
003300           30  WS-FILLER              PIC X(0059).
003600         20  WS-OPTIONAL-INPUT.
003300           30  WS-FILLER              PIC X(0050).
004500       10  WS-FILLER          PIC X(0050).
