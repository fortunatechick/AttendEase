package io.github.fortunatechick.attendease.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ParseResult {

    public final StudentProfile profile;
    public final List<LectureRecord> records;
    public final Map<String, String> prefixMerges;   // truncated name -> canonical name
    public final List<Integer> missingSrNos;
    public final List<Integer> duplicateSrNos;
    public final List<String> unmatchedLines;        // lines that start with a number but did not parse
    public final Map<String, Integer> unknownStatuses; // status codes other than P/A/E/L/NU -> row count

    public ParseResult(StudentProfile profile, List<LectureRecord> records,
                       Map<String, String> prefixMerges, List<Integer> missingSrNos,
                       List<Integer> duplicateSrNos, List<String> unmatchedLines,
                       Map<String, Integer> unknownStatuses) {
        this.profile = profile;
        this.records = records;
        this.prefixMerges = prefixMerges;
        this.missingSrNos = missingSrNos;
        this.duplicateSrNos = duplicateSrNos;
        this.unmatchedLines = unmatchedLines;
        this.unknownStatuses = unknownStatuses;
    }

    public boolean isEmpty() {
        return records.isEmpty();
    }

    public boolean hasSequenceProblems() {
        return !missingSrNos.isEmpty() || !duplicateSrNos.isEmpty();
    }

    public int countByStatus(String status) {
        int count = 0;
        for (LectureRecord r : records) {
            if (r.status.equals(status)) count++;
        }
        return count;
    }

    public List<String> subjectNames() {
        TreeSet<String> names = new TreeSet<>();
        for (LectureRecord r : records) {
            names.add(r.subjectName);
        }
        return new ArrayList<>(names);
    }

    public List<LectureRecord> recordsFor(String subjectName) {
        List<LectureRecord> list = new ArrayList<>();
        for (LectureRecord r : records) {
            if (r.subjectName.equals(subjectName)) list.add(r);
        }
        return list;
    }
}
