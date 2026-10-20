package io.github.fortunatechick.attendease;

import android.app.Application;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import io.github.fortunatechick.attendease.data.AttendanceRepository;
import io.github.fortunatechick.attendease.data.ImportSummary;
import io.github.fortunatechick.attendease.data.PdfTextExtractor;
import io.github.fortunatechick.attendease.data.Prefs;
import io.github.fortunatechick.attendease.parser.AttendanceParser;
import io.github.fortunatechick.attendease.parser.ParseResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Holds the import across screen rotation. Work runs on a single background thread and
// results are posted back to the main thread with a Handler.
public class ImportViewModel extends AndroidViewModel {

    public enum Stage { IDLE, READING, REVIEW, SAVING, SAVED, ERROR }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<Stage> stage = new MutableLiveData<>(Stage.IDLE);

    private final AttendanceRepository repository;

    String fileName;
    ParseResult result;
    AttendanceRepository.ImportPreview preview;
    ImportSummary summary;
    String errorMessage;

    public ImportViewModel(@NonNull Application application) {
        super(application);
        repository = new AttendanceRepository(application);
    }

    public LiveData<Stage> stage() {
        return stage;
    }

    public void read(Uri uri) {
        stage.setValue(Stage.READING);
        executor.execute(() -> {
            try {
                String name = displayName(uri);
                String text = PdfTextExtractor.extract(getApplication().getContentResolver(), uri,
                        getApplication().getCacheDir());
                ParseResult parsed = new AttendanceParser().parse(text);
                if (parsed.isEmpty()) {
                    throw new PdfTextExtractor.ImportException(text.trim().isEmpty()
                            ? PdfTextExtractor.Reason.NO_TEXT : PdfTextExtractor.Reason.NO_ROWS);
                }
                AttendanceRepository.ImportPreview p = repository.previewImport(parsed);
                mainHandler.post(() -> {
                    fileName = name;
                    result = parsed;
                    preview = p;
                    stage.setValue(Stage.REVIEW);
                });
            } catch (PdfTextExtractor.ImportException e) {
                postError(messageFor(e.reason));
            } catch (OutOfMemoryError e) {
                postError(messageFor(PdfTextExtractor.Reason.OUT_OF_MEMORY));
            } catch (RuntimeException e) {
                postError(getApplication().getString(R.string.import_read_failed, e.getClass().getSimpleName()));
            }
        });
    }

    public void save() {
        if (result == null) return;
        stage.setValue(Stage.SAVING);
        ParseResult toSave = result;
        String name = fileName;
        executor.execute(() -> {
            try {
                ImportSummary s = repository.importReport(toSave, name);
                new Prefs(getApplication()).saveProfile(s.semesterId, toSave.profile);
                mainHandler.post(() -> {
                    summary = s;
                    stage.setValue(Stage.SAVED);
                });
            } catch (RuntimeException e) {
                postError(getApplication().getString(R.string.import_save_failed, e.getClass().getSimpleName()));
            }
        });
    }

    private String messageFor(PdfTextExtractor.Reason reason) {
        Application app = getApplication();
        switch (reason) {
            case PASSWORD:
                return app.getString(R.string.import_error_password);
            case NOT_FOUND:
                return app.getString(R.string.import_error_not_found);
            case TOO_LARGE:
                return app.getString(R.string.import_error_too_large, PdfTextExtractor.MAX_BYTES / (1024 * 1024));
            case TOO_MANY_PAGES:
                return app.getString(R.string.import_error_too_many_pages, PdfTextExtractor.MAX_PAGES);
            case OUT_OF_MEMORY:
                return app.getString(R.string.import_error_out_of_memory);
            case NO_TEXT:
                return app.getString(R.string.import_no_text) + " " + app.getString(R.string.import_download_again);
            case NO_ROWS:
                return app.getString(R.string.import_no_rows) + " " + app.getString(R.string.import_download_again);
            case NOT_PDF:
            default:
                return app.getString(R.string.import_error_not_pdf);
        }
    }

    private void postError(String message) {
        mainHandler.post(() -> {
            errorMessage = message;
            stage.setValue(Stage.ERROR);
        });
    }

    private String displayName(Uri uri) {
        try (Cursor c = getApplication().getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException ignored) {
            // Fall back to the last path segment below.
        }
        return uri.getLastPathSegment();
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
    }
}
