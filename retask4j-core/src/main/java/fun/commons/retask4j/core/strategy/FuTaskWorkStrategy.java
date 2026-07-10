package fun.commons.retask4j.core.strategy;

import com.alibaba.fastjson2.JSONObject;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

@Getter
public class FuTaskWorkStrategy {


    // Type name
    @Setter(AccessLevel.NONE)
    private String name;

    // Success assertion: BiFunction<JSONObject output, FuTaskWorkStrategy strategy, Boolean>
    @Setter(AccessLevel.NONE)
    private volatile BiFunction<JSONObject, FuTaskWorkStrategy, Boolean> assertResultFunction;

    @Setter(AccessLevel.NONE)
    private volatile BiConsumer<JSONObject, FuTaskWorkStrategy> onCompleteConsumer;

    @Setter(AccessLevel.NONE)
    private volatile BiConsumer<Throwable, FuTaskWorkStrategy> onFailConsumer;

    @Setter(AccessLevel.NONE)
    private volatile BiConsumer<Throwable, FuTaskWorkStrategy> onFinallyFailConsumer;

    @Setter(AccessLevel.NONE)
    private volatile BiConsumer<JSONObject, FuTaskWorkStrategy> onSuccessConsumer;

    public FuTaskWorkStrategy(String name) {
        Objects.requireNonNull(name, "strategy name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("strategy name must not be blank");
        }
        if (name.length() > 128) {
            throw new IllegalArgumentException("strategy name must not exceed 128 characters: " + name.length());
        }
        this.name = name;
    }

    public void setAssertResultFunction(BiFunction<JSONObject, FuTaskWorkStrategy, Boolean> assertResultFunction) {
        this.assertResultFunction = assertResultFunction;
    }

    public void setOnCompleteConsumer(BiConsumer<JSONObject, FuTaskWorkStrategy> onCompleteConsumer) {
        this.onCompleteConsumer = onCompleteConsumer;
    }

    public void setOnFailConsumer(BiConsumer<Throwable, FuTaskWorkStrategy> onFailConsumer) {
        this.onFailConsumer = onFailConsumer;
    }

    public void setOnFinallyFailConsumer(BiConsumer<Throwable, FuTaskWorkStrategy> onFinallyFailConsumer) {
        this.onFinallyFailConsumer = onFinallyFailConsumer;
    }

    public void setOnSuccessConsumer(BiConsumer<JSONObject, FuTaskWorkStrategy> onSuccessConsumer) {
        this.onSuccessConsumer = onSuccessConsumer;
    }

}
