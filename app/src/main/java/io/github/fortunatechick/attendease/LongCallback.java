package io.github.fortunatechick.attendease;

// Callback<Long> without boxing; stands in for java.util.function.LongConsumer (Android 7.0+).
public interface LongCallback {
    void accept(long value);
}
