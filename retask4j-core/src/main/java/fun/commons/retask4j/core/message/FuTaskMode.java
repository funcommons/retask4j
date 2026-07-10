package fun.commons.retask4j.core.message;

public final class FuTaskMode {

    public final static String NORMAL = "NORMAL";
    public final static String FUNCTION = "FUNCTION";
    public final static String CALLBACK = "CALLBACK";

    private FuTaskMode() {}

    public static boolean isFunction(String mode) {
        return FUNCTION.equals(mode);
    }

    public static boolean isCallback(String mode) {
        return CALLBACK.equals(mode);
    }

}
