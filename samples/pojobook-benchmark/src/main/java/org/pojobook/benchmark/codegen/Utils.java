package org.pojobook.benchmark.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static void logHex(byte[] bytes) {
        if (bytes == null) {
            log.info("null");
            return;
        }

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            line.append(String.format("%02X ", bytes[i]));
            if ((i + 1) % 16 == 0 || i == bytes.length - 1) {
                log.info(line.toString().trim());
                line.setLength(0);
            }
        }
    }
}
