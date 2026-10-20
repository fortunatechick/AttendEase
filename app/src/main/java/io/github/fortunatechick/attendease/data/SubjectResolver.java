package io.github.fortunatechick.attendease.data;

import io.github.fortunatechick.attendease.parser.AttendanceParser;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// Applies the prefix-merge rule across imports, not just within one PDF. If last month's
// PDF only had "Financial Der" and this one has "Financial Derivatives", the stored
// subject is renamed rather than a second subject being created.
public class SubjectResolver {

    public static class Result {
        // Every existing and incoming name -> the canonical name it should live under.
        public final Map<String, String> target = new LinkedHashMap<>();
        // Existing subjects whose canonical name must change (old -> new).
        public final Map<String, String> renames = new LinkedHashMap<>();
        // Existing subjects that must be folded into another existing subject (old -> into).
        public final Map<String, String> folds = new LinkedHashMap<>();
        // Incoming canonical names that need a new subject row.
        public final Set<String> toCreate = new LinkedHashSet<>();
    }

    public static Result resolve(Collection<String> existingNames, Collection<String> incomingNames) {
        Set<String> all = new LinkedHashSet<>(existingNames);
        all.addAll(incomingNames);
        Map<String, String> canonical = AttendanceParser.mergePrefixes(all);

        Result result = new Result();
        result.target.putAll(canonical);

        Set<String> existing = new LinkedHashSet<>(existingNames);
        Set<String> claimed = new LinkedHashSet<>();
        for (String name : existingNames) {
            String target = canonical.get(name);
            if (target.equals(name)) {
                claimed.add(name);
            }
        }
        for (String name : existingNames) {
            String target = canonical.get(name);
            if (target.equals(name)) continue;
            // The first existing subject to reach a new name is renamed; any others fold into it.
            if (existing.contains(target) || claimed.contains(target)) {
                result.folds.put(name, target);
            } else {
                result.renames.put(name, target);
                claimed.add(target);
            }
        }
        for (String name : incomingNames) {
            String target = canonical.get(name);
            if (!claimed.contains(target)) {
                result.toCreate.add(target);
                claimed.add(target);
            }
        }
        return result;
    }
}
