package fun.commons.retask4j.core.message;

public final class FuTaskStatus {

    public final static String WAITING = "WAITING";
    public final static String PENDING = "PENDING";
    public final static String SUCCESS = "SUCCESS";
    public final static String FAIL = "FAIL";

    private FuTaskStatus() {}

    public static boolean isTerminal(String status) {
        return SUCCESS.equals(status) || FAIL.equals(status);
    }

}
