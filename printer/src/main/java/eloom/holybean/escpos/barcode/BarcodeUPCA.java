package eloom.holybean.escpos.barcode;

import eloom.holybean.escpos.EscPosPrinterCommands;
import eloom.holybean.escpos.EscPosPrinterSize;
import eloom.holybean.escpos.exceptions.EscPosBarcodeException;

public class BarcodeUPCA extends BarcodeNumber {

    public BarcodeUPCA(EscPosPrinterSize printerSize, String code, float widthMM, float heightMM, int textPosition) throws EscPosBarcodeException {
        super(printerSize, EscPosPrinterCommands.BARCODE_TYPE_UPCA, code, widthMM, heightMM, textPosition);
    }

    @Override
    public int getCodeLength() {
        return 12;
    }
}
