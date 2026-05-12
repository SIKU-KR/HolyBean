package eloom.holybean.escpos.exceptions;

public class EscPosConnectionException extends Exception {
    public EscPosConnectionException(String errorMessage) {
        super(errorMessage);
    }

    public EscPosConnectionException(String errorMessage, Throwable cause) {
        super(errorMessage, cause);
    }
}
