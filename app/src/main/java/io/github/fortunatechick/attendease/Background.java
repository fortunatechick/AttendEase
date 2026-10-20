package io.github.fortunatechick.attendease;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// One background thread for all database work, so reads and writes never overlap and
// never run on the UI thread. Results are posted back to the main looper.
public final class Background {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Background() {
    }

    public static <T> void load(Callable<T> work, Callback<T> onResult) {
        EXECUTOR.execute(() -> {
            try {
                T result = work.call();
                MAIN.post(() -> onResult.accept(result));
            } catch (Exception e) {
                MAIN.post(() -> {
                    throw new RuntimeException(e);
                });
            }
        });
    }

    public static void run(Runnable work, Runnable then) {
        EXECUTOR.execute(() -> {
            work.run();
            if (then != null) MAIN.post(then);
        });
    }
}
