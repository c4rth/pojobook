package org.pojobook.benchmark.codegen.cobol;

import net.sf.JRecord.Common.IFieldDetail;
import net.sf.JRecord.Details.AbstractLine;
import net.sf.JRecord.Details.LayoutDetail;
import net.sf.JRecord.Details.Line;
import net.sf.JRecord.Details.fieldValue.IFieldValueUpdLine;
import net.sf.JRecord.Details.fieldValue.LineFieldCreator;
import net.sf.JRecord.cgen.impl.derSer.BasePojoConverter;
import net.sf.JRecord.def.IO.builders.ISchemaIOBuilder;

import java.io.IOException;

public class ConvertSampleCbk extends BasePojoConverter<LineSampleCbkPojo> {

    public final IFieldValueUpdLine fuNmCaller;
    public final IFieldValueUpdLine fuNmBb;
    public final IFieldValueUpdLine fuCoTransmission;
    public final IFieldValueUpdLine fuNmFunction;
    public final IFieldValueUpdLine fuWsCoTypreq;
    public final IFieldValueUpdLine fuWsNsId;
    public final IFieldValueUpdLine fuWsDaPur;
    public final IFieldValueUpdLine fuWsTeLogo;
    public final IFieldValueUpdLine fuWsTeBrand;
    public final IFieldValueUpdLine fuWsTeCateg;
    public final IFieldValueUpdLine fuWsTe1letter;
    public final IFieldValueUpdLine fuWsTeGeocoord;
    public final IFieldValueUpdLine fuWsTeFormadr;
    public final IFieldValueUpdLine fuWsFiller;
    public final IFieldValueUpdLine fuWsFiller1;
    public final IFieldValueUpdLine fuWsFiller2;


    public ConvertSampleCbk(ISchemaIOBuilder lineCreator) throws IOException {
        super(lineCreator);

        LayoutDetail schema = lineCreator.getLayout();
        FieldNamesSampleCbk.RecordSampleCbk fn
                = FieldNamesSampleCbk.RECORD_SAMPLECBK;
        Line line = null;
        LineFieldCreator lfc = LineFieldCreator.getInstance();


        IFieldDetail fldNmCaller = schema.getFieldFromName(fn.nmCaller);
        IFieldDetail fldNmBb = schema.getFieldFromName(fn.nmBb);
        IFieldDetail fldCoTransmission = schema.getFieldFromName(fn.coTransmission);
        IFieldDetail fldNmFunction = schema.getFieldFromName(fn.nmFunction);
        IFieldDetail fldWsCoTypreq = schema.getFieldFromName(fn.wsCoTypreq);
        IFieldDetail fldWsNsId175tran = schema.getFieldFromName(fn.wsNsId);
        IFieldDetail fldWsDaPur175tran = schema.getFieldFromName(fn.wsDaPur);
        IFieldDetail fldWsTeLogo175refc = schema.getFieldFromName(fn.wsTeLogo);
        IFieldDetail fldWsTeBrand175refc = schema.getFieldFromName(fn.wsTeBrand);
        IFieldDetail fldWsTeCateg175refc = schema.getFieldFromName(fn.wsTeCateg);
        IFieldDetail fldWsTe1letter175refc = schema.getFieldFromName(fn.wsTe1letter);
        IFieldDetail fldWsTeGeocoord175refc = schema.getFieldFromName(fn.wsTeGeocoord);
        IFieldDetail fldWsTeFormadr175refc = schema.getFieldFromName(fn.wsTeFormadr);
        IFieldDetail fldWsFiller = schema.getFieldFromName(fn.wsFiller);
        IFieldDetail fldWsFiller1 = schema.getFieldFromName(fn.wsFiller1);
        IFieldDetail fldWsFiller2 = schema.getFieldFromName(fn.wsFiller2);

        fuNmCaller = lfc.newFieldValue(line, fldNmCaller);
        fuNmBb = lfc.newFieldValue(line, fldNmBb);
        fuCoTransmission = lfc.newFieldValue(line, fldCoTransmission);
        fuNmFunction = lfc.newFieldValue(line, fldNmFunction);
        fuWsCoTypreq = lfc.newFieldValue(line, fldWsCoTypreq);
        fuWsNsId = lfc.newFieldValue(line, fldWsNsId175tran);
        fuWsDaPur = lfc.newFieldValue(line, fldWsDaPur175tran);
        fuWsTeLogo = lfc.newFieldValue(line, fldWsTeLogo175refc);
        fuWsTeBrand = lfc.newFieldValue(line, fldWsTeBrand175refc);
        fuWsTeCateg = lfc.newFieldValue(line, fldWsTeCateg175refc);
        fuWsTe1letter = lfc.newFieldValue(line, fldWsTe1letter175refc);
        fuWsTeGeocoord = lfc.newFieldValue(line, fldWsTeGeocoord175refc);
        fuWsTeFormadr = lfc.newFieldValue(line, fldWsTeFormadr175refc);
        fuWsFiller = lfc.newFieldValue(line, fldWsFiller);
        fuWsFiller1 = lfc.newFieldValue(line, fldWsFiller1);
        fuWsFiller2 = lfc.newFieldValue(line, fldWsFiller2);


    }


    @Override
    public LineSampleCbkPojo toPojo(AbstractLine line) {

        LineSampleCbkPojo pojo = new LineSampleCbkPojo();

        pojo.setNmCaller(fuNmCaller.setLine(line).asString());
        pojo.setNmBb(fuNmBb.setLine(line).asString());
        pojo.setCoTransmission(fuCoTransmission.setLine(line).asString());
        pojo.setNmFunction(fuNmFunction.setLine(line).asString());
        pojo.setWsCoTypreq(fuWsCoTypreq.setLine(line).asString());
        pojo.setWsNsId(fuWsNsId.setLine(line).asLong());
        pojo.setWsDaPur(fuWsDaPur.setLine(line).asString());
        pojo.setWsTeLogo(fuWsTeLogo.setLine(line).asString());
        pojo.setWsTeBrand(fuWsTeBrand.setLine(line).asString());
        pojo.setWsTeCateg(fuWsTeCateg.setLine(line).asString());
        pojo.setWsTe1letter(fuWsTe1letter.setLine(line).asString());
        pojo.setWsTeGeocoord(fuWsTeGeocoord.setLine(line).asString());
        pojo.setWsTeFormadr(fuWsTeFormadr.setLine(line).asString());
        pojo.setWsFiller(fuWsFiller.setLine(line).asString());
        pojo.setWsFiller1(fuWsFiller1.setLine(line).asString());
        pojo.setWsFiller2(fuWsFiller2.setLine(line).asString());
        // false


        return pojo;
    }

    @Override
    public void updateLine(AbstractLine line, LineSampleCbkPojo pojo) {

        fuNmCaller.setLine(line).set(pojo.getNmCaller());
        fuNmBb.setLine(line).set(pojo.getNmBb());
        fuCoTransmission.setLine(line).set(pojo.getCoTransmission());
        fuNmFunction.setLine(line).set(pojo.getNmFunction());
        fuWsCoTypreq.setLine(line).set(pojo.getWsCoTypreq());
        fuWsNsId.setLine(line).set(pojo.getWsNsId());
        fuWsDaPur.setLine(line).set(pojo.getWsDaPur());
        fuWsTeLogo.setLine(line).set(pojo.getWsTeLogo());
        fuWsTeBrand.setLine(line).set(pojo.getWsTeBrand());
        fuWsTeCateg.setLine(line).set(pojo.getWsTeCateg());
        fuWsTe1letter.setLine(line).set(pojo.getWsTe1letter());
        fuWsTeGeocoord.setLine(line).set(pojo.getWsTeGeocoord());
        fuWsTeFormadr.setLine(line).set(pojo.getWsTeFormadr());
        fuWsFiller.setLine(line).set(pojo.getWsFiller());
        fuWsFiller1.setLine(line).set(pojo.getWsFiller1());
        fuWsFiller2.setLine(line).set(pojo.getWsFiller2());

    }


}
