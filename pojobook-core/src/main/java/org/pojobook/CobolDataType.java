package org.pojobook;

public enum CobolDataType {
    /**
     * DISPLAY - Character data
     */
    DISPLAY,

    /**
     * COMP / COMP-4 / BINARY - Binary integer
     */
    COMP,

    /**
     * COMP-1 - Single precision floating point
     */
    COMP_1,

    /**
     * COMP-2 - Double precision floating point
     */
    COMP_2,

    /**
     * COMP-3 - Packed decimal
     */
    COMP_3,

    /**
     * COMP-5 - Native binary
     */
    COMP_5,

    /**
     * PACKED-DECIMAL - Packed decimal
     */
    PACKED_DECIMAL,

    /**
     * ZONED-DECIMAL - Zoned decimal
     */
    ZONED_DECIMAL
}
