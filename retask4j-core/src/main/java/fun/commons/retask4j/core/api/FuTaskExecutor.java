package fun.commons.retask4j.core.api;

import com.alibaba.fastjson2.JSONObject;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

public class FuTaskExecutor<T, R> {

    private final Class<T> inputCls;
    private final Function<T, R> function;
    private final BiFunction<T, JSONObject, R> biFunction;

    public FuTaskExecutor(Function<T, R> function, Class<T> inputCls) {
        this.function = Objects.requireNonNull(function, "function must not be null");
        this.inputCls = Objects.requireNonNull(inputCls, "inputCls must not be null");
        this.biFunction = null;
    }

    public FuTaskExecutor(BiFunction<T, JSONObject, R> biFunction, Class<T> inputCls) {
        this.biFunction = Objects.requireNonNull(biFunction, "biFunction must not be null");
        this.inputCls = Objects.requireNonNull(inputCls, "inputCls must not be null");
        this.function = null;
    }

    public JSONObject execute(JSONObject data, JSONObject ext) throws Exception {
        T input = data.to(inputCls);
        R output = function != null ? function.apply(input) : biFunction.apply(input, ext);
        return output != null ? JSONObject.from(output) : new JSONObject();
    }

}
