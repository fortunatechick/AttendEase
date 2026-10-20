package io.github.fortunatechick.attendease;

// A one-argument callback. java.util.function.Consumer would do, but it needs Android 7.0
// (or library desugaring, which breaks PdfBox under R8), and the app supports Android 5.0.
public interface Callback<T> {
    void accept(T value);
}
