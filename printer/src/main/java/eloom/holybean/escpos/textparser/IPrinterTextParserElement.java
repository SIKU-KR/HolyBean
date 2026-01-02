package eloom.holybean.escpos.textparser;

import eloom.holybean.escpos.EscPosPrinterCommands;
import eloom.holybean.escpos.exceptions.EscPosConnectionException;
import eloom.holybean.escpos.exceptions.EscPosEncodingException;

public interface IPrinterTextParserElement {
    int length() throws EscPosEncodingException;

    IPrinterTextParserElement print(EscPosPrinterCommands printerSocket) throws EscPosEncodingException, EscPosConnectionException;
}
