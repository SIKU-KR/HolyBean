package eloom.holybean.escpos.barcode;

import eloom.holybean.escpos.EscPosPrinterCommands;
import eloom.holybean.escpos.EscPosPrinterSize;
import eloom.holybean.escpos.exceptions.EscPosBarcodeException;

public class BarcodeEAN8 extends BarcodeNumber {
    public BarcodeEAN8(EscPosPrinterSize printerSize, String code, float widthMM, float heightMM, int textPosition) throws EscPosBarcodeException {
        super(printerSize, EscPosPrinterCommands.BARCODE_TYPE_EAN8, code, widthMM, heightMM, textPosition);
    }

    @Override
    public int getCodeLength() {
        return 8;
    }
}
