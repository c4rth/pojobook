package org.pojobook.util;

import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Detects the charset family for optimized byte-level encoding/decoding.
 * <p>
 * Returns one of three modes:
 * <ul>
 *   <li>{@link #ASCII}  – ASCII-compatible charsets (US-ASCII, UTF-8, ISO-8859-*)</li>
 *   <li>{@link #EBCDIC} – EBCDIC charsets (CP1047, CP037, …)</li>
 *   <li>{@link #UNKNOWN} – unsupported / multi-byte charsets</li>
 *   <li>{@link #UNKNOWN} – unsupported / multi-byte charsets</li>
 * </ul>
 * <p>
 * Detection results are cached so that repeated calls with the same {@link Charset}
 * instance avoid the {@code name().toUpperCase()} and {@code contains()} overhead.
 */
public final class CharsetMode {

    /** ASCII-compatible single-byte charset. */
    public static final int ASCII = 1;
    /** EBCDIC single-byte charset. */
    public static final int EBCDIC = 2;
    /** Unknown or multi-byte charset – requires standard encoding API. */
    public static final int UNKNOWN = 0;

    // ASCII byte values
    public static final byte ASCII_SPACE = 0x20;
    public static final byte ASCII_ZERO = 0x30;

    // EBCDIC byte values
    public static final byte EBCDIC_SPACE = 0x40;
    public static final byte EBCDIC_ZERO = (byte) 0xF0;

    /** Statically cached instance of IBM CP1047 to prevent high-concurrency lookup bottlenecks. */
    public static final Charset CHARSET_CP1047 = Charset.forName("CP1047");
    
    /** Statically cached instance of IBM CP037 to prevent high-concurrency lookup bottlenecks. */
    public static final Charset CHARSET_CP037 = Charset.forName("CP037");

    /** Cache of detection results keyed by Charset instance. */
    private static final ConcurrentMap<Charset, Integer> CACHE = new ConcurrentHashMap<>();

    private CharsetMode() {
        // Prevent instantiation
    }

    /**
     * Detect the charset family from a {@link Charset} instance.
     * <p>
     * The result is cached per {@link Charset} instance so that only the first
     * call performs string inspection; subsequent calls are a fast map lookup.
     *
     * @return {@link #ASCII}, {@link #EBCDIC}, or {@link #UNKNOWN}
     */
    public static int detect(Charset charset) {
        Integer cached = CACHE.get(charset);
        if (cached != null) {
            return cached;
        }
        int mode = detectUncached(charset);
        CACHE.put(charset, mode);
        return mode;
    }

    private static int detectUncached(Charset charset) {
        String charsetName = charset.name().toUpperCase(Locale.ROOT);
        if (charsetName.contains("1047") || charsetName.contains("037") || charsetName.contains("EBCDIC")) {
            return EBCDIC;
        }
        if (charsetName.contains("ASCII") || charsetName.contains("UTF-8") || charsetName.contains("ISO-8859")) {
            return ASCII;
        }
        return UNKNOWN;
    }

    /**
     * Return the byte value used for space padding in the given mode.
     */
    public static byte spaceByte(int mode) {
        return mode == EBCDIC ? EBCDIC_SPACE : ASCII_SPACE;
    }

    /**
     * Return the byte value used for zero padding in the given mode.
     */
    public static byte zeroByte(int mode) {
        return mode == EBCDIC ? EBCDIC_ZERO : ASCII_ZERO;
    }

    /**
     * Return the base byte value for digit '0' in the given mode.
     * Digit {@code d} is encoded as {@code digitBase(mode) + d}.
     */
    public static byte digitBase(int mode) {
        return mode == EBCDIC ? EBCDIC_ZERO : ASCII_ZERO;
    }
}
