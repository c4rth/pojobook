package org.pojobook.benchmark.codegen;


import org.pojobook.benchmark.codegen.cobol.ConvertSampleCbk;
import org.pojobook.benchmark.codegen.cobol.LineSampleCbkPojo;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sf.JRecord.Common.Constants;
import net.sf.JRecord.External.CopybookLoader;
import net.sf.JRecord.JRecordInterface1;
import net.sf.JRecord.cgen.def.IReader;
import net.sf.JRecord.cgen.def.IWriter;
import net.sf.JRecord.cgen.impl.io.IoBuilder;
import net.sf.JRecord.def.IO.builders.ICobolIOBuilder;
import net.sf.cb2xml.def.Cb2xmlConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;


@Slf4j
@Getter
@Setter
@SuppressWarnings({"java:S2093", "unused"})
public class CopybookConverter {

    protected IoBuilder<LineSampleCbkPojo> ioB = null;

    protected String charset = "CP1047";

    protected IoBuilder<LineSampleCbkPojo> getIoB() {
        if (ioB == null) {
            ioB = this.builder();
        }
        return ioB;
    }

    private IoBuilder<LineSampleCbkPojo> builder() {
        IoBuilder<LineSampleCbkPojo> ioBLocal = null;
        try {
            ICobolIOBuilder iob = JRecordInterface1.COBOL
                    .newIOBuilder(copybookAsStream(), copybookName())
                    .setFont(charset)
                    .setCopybookFileFormat(Cb2xmlConstants.USE_COLS_6_TO_80)
                    .setFileOrganization(Constants.IO_FIXED_LENGTH)
                    .setSplitCopybook(CopybookLoader.SPLIT_NONE)
                    .setDropCopybookNameFromFields(true);

            ioBLocal = new IoBuilder<>(pojoConverter(iob), iob);

        } catch (IOException e) {
            log.error("IoBuilder", e);
        }
        return ioBLocal;
    }

    public LineSampleCbkPojo convertToCopybookModel(byte[] data) throws CopyBookException {
        IReader<LineSampleCbkPojo> reader = null;
        LineSampleCbkPojo line;
        try {
            reader = getIoB().newReader(new ByteArrayInputStream(data));

            if ((line = reader.read()) != null) {
                return line;
            }

        } catch (IOException e) {
            throw new CopyBookException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return null;
    }

    public byte[] convertToCopybookData(LineSampleCbkPojo pojo) throws CopyBookException {
        IWriter<LineSampleCbkPojo> writer = null;
        byte[] data;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writer = this.getIoB().newWriter(baos);
            writer.write(pojo);
            writer.close();
            writer = null;
            data = baos.toByteArray();
        } catch (IOException e) {
            throw new CopyBookException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        return data;
    }

    public ConvertSampleCbk pojoConverter(ICobolIOBuilder iob) throws IOException {
        return new ConvertSampleCbk(iob);
    }

    public InputStream copybookAsStream() {
        return this.getClass().getResourceAsStream("/copybook/sample-cbk.cbl");
    }

    public String copybookName() {
        return "RKHC920I";
    }

}
