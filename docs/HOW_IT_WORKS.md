# How AttendEase works

Technical notes for contributors. All examples use placeholder data.

## Layout

```
app/src/main/java/io/github/fortunatechick/attendease/
  parser/   AttendanceParser, LectureRecord, ParseResult, StudentProfile    plain Java, no Android
  calc/     AttendanceCalculator, AttendanceStats, BunkAdvice, SemesterBudget,
            WeeklyPattern, ApprovalConvention, CsvExporter                   plain Java, no Android
  data/     AttendEaseDb, *Dao, AttendanceRepository, LeaveRepository,
            ImportPlanner, SubjectResolver, LeaveRules, Status, Prefs,
            PdfTextExtractor, GuestMode, DataChanges
  *.java    Activities, adapters, Formats (wording), TwoPane, CrashLog, ...
```

Anything that decides a number lives in a plain-Java class with a JVM test. The screens only
display and collect input. Their wording comes from string resources, and
`WordingInstrumentedTest` checks it matches the calc classes' English text.

**Threading.**
- PDF extraction runs on an executor inside `ImportViewModel`, so it survives rotation.
- All other database work goes through `Background`, a single-thread executor.
- Every write passes through `AttendEaseDb.getWritableDatabase()`, which tells open screens to reload (`DataChanges`). This keeps two panes in step.

## The report PDF

The report is a text-based PDF, so no OCR is needed. PdfBox-Android extracts the text with
`setSortByPosition(true)`, giving one line per lecture row:

```
1 Operating SystemsT1 BT IT A Jan 11, 2027 9:00:01 AM 10:00:00 AM P
2 Data Structures and Algorithms T1  B Tech IT A Jan 11, 2027 10:00:01 AM 11:00:00 AM A
5 Cloud Computing and VirtualizatT1BTITA Jan 12, 2027 2:00:01 PM 3:00:00 PM P
13 Design ThinkingP1 BT IT A1 Jan 13, 2027 11:00:01 AM 12:00:00 PM NU
```

The header block on page 1 (name, roll no., session, programme, report dates) becomes the
student profile.

Status codes: **P** present, **A** absent, **E** exemption, **L** late admission, **NU** not updated.

**Low-memory reading.**
- The picked file is copied to a private temp file, and anything over 25 MB is refused.
- PdfBox reads the copy with a 4 MB buffer that spills to disk.
- More than 300 pages is refused.
- Damaged files end in a clear error, never a crash (`PdfImportInstrumentedTest`, `ParserFuzzTest`).

## The three parsing rules

1. **`NU` rows are not lectures held.** They are stored and shown as *Not updated*, but kept out of both attended and held until a later PDF marks them.
2. **Theory and practical are one subject.** The course name is split on the first `T<n>` / `P<n>` marker (a `TU<n>` tutorial counts as theory):

   | Raw course name | Subject | Component | Batch |
   |---|---|---|---|
   | `Operating SystemsT1 BT IT A` | Operating Systems | T | BT IT A |
   | `Data Structures and Algorithms T1  B Tech IT A` | Data Structures and Algorithms | T | B Tech IT A |
   | `Cloud Computing and VirtualizatT1BTITA` | Cloud Computing and Virtualizat | T | BTITA |
   | `Engineering Mathematics - IIITU1 B Tech IT A` | Engineering Mathematics - III | T | B Tech IT A |
   | `Python ProgrammingP2 BT IT A2` | Python Programming | P | BT IT A2 |

3. **Truncated names are merged by prefix.** SAP cuts long names differently for theory and lab rows. A name is folded into a longer one only when all of these hold:
   - It matches the longer name up to its own length (ignoring case and spacing).
   - It is at least 8 characters long.
   - It is cut in the middle of a word.

   `Machine Learning` and `Machine Learning Applications` stay separate. The review screen lists every merge, and *⋮ → Merge into another subject* handles anything else by hand.

Dates, times and status codes are matched loosely, so other students' and other semesters'
reports parse too (`ParserVariationsTest`). Unknown status codes are kept as *not updated*
and listed on the review screen.

## Medical leave

| State | Comes from | In held? | In attended? |
|---|---|---|---|
| `PRESENT` | P / E / L, or marked by hand | yes | yes |
| `ABSENT` | A, or marked by hand | yes | no |
| `PENDING_LEAVE` | covered by an undecided leave | held apart | no |
| `EXEMPT` | covered by an approved leave | per setting | per setting |
| `NOT_UPDATED` | NU | no | no |

```
confirmed  = decidedAttended / decidedHeld
ifApproved = (decidedAttended + pending) / (decidedHeld + pending)   counted as attended
           =  decidedAttended / decidedHeld                          excluded from total
ifRejected =  decidedAttended / (decidedHeld + pending)
```

With anything pending the app shows `ifRejected – ifApproved`. A newer PDF that shows a
pending lecture as attended auto-approves it. Where your record and the PDF disagree, you
choose; nothing is overwritten silently.

## Semester absence budget

```
total         = max(lectures per week × weeks, lectures already recorded)
missesAllowed = floor(total × (100 − target) / 100)
canStillMiss  = missesAllowed − absences so far
```

With a 15-week semester and an 80% target, 4 lectures a week allows 12 misses and 3 a week
allows 9. Lectures per week are estimated from the PDF (the most common weekly count) and
can be set per subject.

## Data

SQLite, schema version 3, upgraded step by step in `onUpgrade` so existing data is always kept:

```
semester      (_id, label, created_at)
subject       (_id, semester_id, canonical_name, short_name, created_at, theory_per_week, lab_per_week)
subject_alias (semester_id, alias_key, subject_id)
lecture       (_id, subject_id, component, date_millis, start_time, start_minutes, end_time,
               raw_course_name, batch_label, sr_no, source_status, effective_status,
               manual_override, leave_id, import_run_id)
leave_request (_id, from_date, to_date, reason, submitted_to, submitted_on, status, note)
leave_lecture (leave_id, lecture_id, decision, previous_status, previous_manual)
import_run    (_id, file_name, imported_at, rows_parsed, rows_skipped, duration_from, duration_to)
```

Dates are stored as midnight UTC. Guest mode uses a separate database and preferences file.

## Tests

- **JVM:** `./gradlew testDebugUnitTest` covers the parser, fuzzing, the re-import planner, subject resolution, leave rules, the calculator, the semester budget and the CSV export.
- **Device:** `./gradlew connectedReleaseAndroidTest -PtestBuildType=release` checks real PdfBox extraction on the minified build, damaged files and the wording.

A few JVM tests also use a real report's extracted text. That file holds personal data, so it
is git-ignored and those tests are skipped when it's missing. Never commit a real report.
