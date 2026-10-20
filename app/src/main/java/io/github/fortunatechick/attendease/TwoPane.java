package io.github.fortunatechick.attendease;

import android.app.Activity;
import android.content.Context;

import androidx.window.embedding.ActivityEmbeddingController;
import androidx.window.embedding.RuleController;
import androidx.window.embedding.SplitAttributes;
import androidx.window.embedding.SplitController;
import androidx.window.WindowSdkExtensions;
import androidx.window.layout.DisplayFeature;
import androidx.window.layout.FoldingFeature;

// Side-by-side panes on wide windows, from the rules in res/xml/split_rules.xml. The screens
// themselves are unchanged: Android places the subject screen next to the dashboard instead
// of on top of it. Devices without window-manager support simply ignore the rules.
final class TwoPane {

    private TwoPane() {
    }

    static void install(Context context) {
        RuleController.getInstance(context)
                .setRules(RuleController.parseRules(context, R.xml.split_rules));

        // On a foldable whose hinge separates the screen (a book-style dual screen), split
        // exactly at the hinge so no text falls into it. Otherwise keep the rule's 42 / 58 split.
        // (Custom split sizing needs window extensions level 2; older ones use the rule as is.)
        SplitController splits = SplitController.getInstance(context);
        if (splits.getSplitSupportStatus() == SplitController.SplitSupportStatus.SPLIT_AVAILABLE
                && WindowSdkExtensions.getInstance().getExtensionVersion() >= 2) {
            splits.setSplitAttributesCalculator(params -> {
                if (!params.areDefaultConstraintsSatisfied()) {
                    return new SplitAttributes.Builder()
                            .setSplitType(SplitAttributes.SplitType.SPLIT_TYPE_EXPAND)
                            .build();
                }
                for (DisplayFeature feature : params.getParentWindowLayoutInfo().getDisplayFeatures()) {
                    if (feature instanceof FoldingFeature) {
                        FoldingFeature fold = (FoldingFeature) feature;
                        if (fold.isSeparating()
                                && fold.getOrientation() == FoldingFeature.Orientation.VERTICAL) {
                            return new SplitAttributes.Builder()
                                    .setSplitType(SplitAttributes.SplitType.SPLIT_TYPE_HINGE)
                                    .build();
                        }
                    }
                }
                return params.getDefaultSplitAttributes();
            });
        }
    }

    // True when the activity is one of two panes (so it needs no back arrow of its own).
    static boolean isEmbedded(Activity activity) {
        return ActivityEmbeddingController.getInstance(activity).isActivityEmbedded(activity);
    }
}
