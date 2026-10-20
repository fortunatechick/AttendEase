# AttendEase

An offline Android app that reads the attendance PDF from the SVKM's NMIMS SAP portal, works out per-subject attendance against the 80% rule, and tracks medical leaves that the college hasn't decided on yet.

An independent student project. Java + XML, raw SQLite, no network.

---

## Download

Get the latest `AttendEase-v<version>.apk` from the [Releases page](https://github.com/fortunatechick/AttendEase/releases/latest). It needs Android 5.0 (Lollipop) or newer.

To install on a phone:

1. Open the Releases page on the phone and download the APK.
2. Tap the downloaded file. If Android asks, allow your browser or Files app to *install unknown apps*.
3. Open AttendEase, tap *Upload attendance PDF*, and pick the attendance report downloaded from the SAP portal.

### Coming from an earlier test build

Builds before 1.0.0 used a temporary package name (`com.example.*`). Android treats 1.0.0 as a **different app**, so it installs alongside the old one rather than updating it, and the old app's data doesn't carry over. To move over:

1. In the old app, *Settings → Export CSV* if you want a copy of your figures.
2. Uninstall the old app.
3. Install 1.0.0 and upload your attendance PDF again.

From 1.0.0 on, every release installs over the previous one and keeps your data.

---

## Screenshots

Screenshots are being regenerated with placeholder data for phones, foldables and tablets, and will appear here and under `docs/devices/`.

---

## Why it exists

The SAP portal publishes attendance as a PDF with one row per lecture *hour*, across 16 pages. It shows no percentages and lists theory and practical as separate courses. It also gives no per-subject total, so the only way to know where you stand is to count rows by hand.

Medical leave makes this worse. You hand a certificate to the HOD or course coordinator, and the decision can take weeks. You are never told the outcome directly, and meanwhile SAP has already marked those lectures **A**. Your percentage could be either of two numbers, and you can't plan around either.

AttendEase imports the PDF and does the counting. Lectures covered by a leave go into a third state, *pending*, so you see the range your real percentage will fall in.

---

## What it does

- **Imports the SAP PDF.** It extracts the text, parses every row, shows a review screen, and only then writes anything.
- **Per-subject attendance.** Theory and practical are merged into one subject and also shown separately, each checked against your threshold.
- **A semester absence budget.** Attendance is judged over the whole 15-week semester, theory and lab together. A subject with 4 lectures a week has 60 lectures and allows 12 absences; one with 3 a week has 45 and allows 9. The app says how many you can still miss ("You can miss 3 more this semester"), or how far over you are and the best you can still finish on.
- **Medical leave tracking.** You pick the dates, tick the lectures the leave covers, and record the outcome for the whole request or lecture by lecture. Until it is decided you see a range, e.g. `74.0% – 78.0%`.
- **Re-import safely.** Importing the same PDF again changes nothing. A newer PDF updates what changed, auto-approves pending leaves the college has now marked, and never silently overwrites a decision you recorded.
- **Manual entry.** You can add subjects and lectures by hand between PDF releases. Nothing needs a PDF.
- **Any student, any semester.** Subjects come from the report itself, so other electives, programmes and semesters work without changes. Each PDF is filed under the semester in its header, semesters never mix, and you can switch between them.
- **Medical leave per lecture.** Long-press any lecture to mark *Medical leave submitted*, then long-press it again later to record *approved* or *rejected*.
- **SAP portal shortcut.** *⋮ → SAP portal* opens the college portal so you can log in and download the report. Tap *Open* on the download, choose AttendEase, and it goes straight to the import screen.
- **Guest mode.** *⋮ → Guest mode* lets a friend check their attendance on your phone. It starts blank, uses its own separate data, never touches yours, and is erased when you exit.
- **No notifications.** The app never posts a notification and doesn't ask for the permission to.
- **Light and dark mode** from the button next to the ⋮ menu.
- **Tablets, foldables and Chromebooks:** on a wide window the dashboard and the subject sit side by side and stay in sync. A PDF can be dragged onto the dashboard; Ctrl+O uploads and Esc goes back. Screenshots are in [docs/devices](docs/devices).
- **CSV export** that opens in Excel, shared through the normal Android share sheet.

---

## The source PDF

The report is an Adobe LiveCycle (XML Forms) PDF. It is **text-based**, so no OCR is needed. PdfBox-Android reads it directly. With `setSortByPosition(true)`, each table row comes out as one line:

```
1 Operating SystemsT1 BT IT A Jan 11, 2027 9:00:01 AM 10:00:00 AM P
2 Data Structures and Algorithms T1  B Tech IT A Jan 11, 2027 10:00:01 AM 11:00:00 AM A
5 Cloud Computing and VirtualizatT1BTITA Jan 12, 2027 2:00:01 PM 3:00:00 PM P
13 Design ThinkingP1 BT IT A1 Jan 13, 2027 11:00:01 AM 12:00:00 PM NU
```

Page 1 also carries a header block (name, student number, roll no., session, programme, report duration). It is parsed into the student profile.

Status codes: **P** Present · **A** Absent · **E** Exemption · **L** Late admission · **NU** Not updated.

The row regex is anchored on the date and on the closed set of status codes. The lazy course-name group therefore can't swallow the date, and the regex accepts any whitespace between columns:

```
^\s*(\d{1,4})\s+(.+?)\s+([A-Z][a-z]{2}\s+\d{1,2},\s*\d{4})\s+
(\d{1,2}:\d{2}:\d{2}\s*[AP]M)\s+(\d{1,2}:\d{2}:\d{2}\s*[AP]M)\s+(P|A|E|L|NU)\s*$
```

The raw text PdfBox-Android produced on a device from the sample report is kept locally at `docs/spike/pdfbox_dump.txt`. A copy goes in `app/src/test/resources/sap_report_2026-10-11.txt`, where the JVM tests use it as ground truth. **Both files contain a real student's name, student number and every lecture, so they are git-ignored and never committed.** Without them, the tests that need the full report (`SampleReportTest`, `ImportPlannerTest`, and the real-report tests in `LeaveRulesTest` and `AttendanceCalculatorTest`) are skipped, not failed. The fixture-based tests still run. To get the full suite, run the extraction on your own report and save its text under that test-resource name.

---

## The three parsing rules

Each one changes the result. Leaving any out gives wrong numbers.

### 1. `NU` rows are not lectures held

`NU` means the faculty hasn't marked that lecture yet. Counting it as held would push every percentage down for something that hasn't happened. NU rows are **stored**, shown as "Not updated", and **excluded from both attended and held** until a later PDF marks them.

### 2. Theory and practical are one subject

The course name carries a component marker: `Operating SystemsT1 BT CE C` is theory, `Operating SystemsP1 BT CE C C2` is the lab. The name is split on the first `T<n>` / `P<n>` marker. The text before it is the subject, the letter is the component, and the text after it is the batch.

The marker comes in several shapes, and all of them must work:

| Raw course name | Subject | Comp. | Batch |
|---|---|---|---|
| `Operating SystemsT1 BT CE C` | Operating Systems | T | BT CE C |
| `Artificial Intelligence T1  B Tech CE C` | Artificial Intelligence | T | B Tech CE C |
| `RAG Sys&Agen WorkflowithPine&LaGrT1BTC5C` | RAG Sys&Agen WorkflowithPine&LaGr | T | BTC5C |
| `Engineering Mathematics - IIITU1 B Tech IT A` | Engineering Mathematics - III | T (tutorial) | B Tech IT A |
| `Python ProgrammingP2 BT IT A2` | Python Programming | P | BT IT A2 |

A tutorial marker (`TU1`) counts with theory. A name with no marker at all is treated as theory.

> **Correction to the original brief.** The brief's regex was `\s*([TP])(\d+)\b`. The trailing `\b` fails on the third shape, where the batch is glued straight onto `T1`, and that subject would be lost. The parser uses `\s*([TP])(\d+)` and takes the first match that isn't at the start of the name.

### 3. Truncated names are merged by prefix

SAP truncates long course names, and differently for theory and lab rows. Lab rows say `Mobile Application Develop`; theory rows say `Mobile Application Development`. Without merging, MAD shows up as two subjects at 81.8% and 83.3% instead of one at 82.6%.

A name is merged into a longer one when it looks like a SAP truncation of it:
- **Same text** up to its length, ignoring case, repeated spaces and trailing punctuation.
- **At least 8 characters long.**
- **Cut in the middle of a word**, like `Develop|ment` or `Der|ivatives`.

A prefix that ends at a word boundary isn't merged automatically. `Machine Learning` and `Machine Learning Applications` can be two real electives. Names that differ only in capitals or spacing (`operating systems` / `Operating Systems`) are the same subject. If a short name prefixes two unrelated longer names, it's left alone rather than guessed. The import review screen lists every merge.

For anything the rules can't be sure of, **merge by hand**: open the subject and choose *⋮ → Merge into another subject*. Its lectures move over, and the old spelling is remembered as an alias, so later imports put those rows in the right subject.

The same rule runs **across imports**. If a later PDF spells out `Financial Derivatives` where an earlier one only had `Financial Der`, the stored subject is renamed instead of duplicated.

> **Correction to the original brief.** In the sample report, `Financial Der` never appears in a longer form, so no parsing rule can expand it. Any subject can be renamed from its detail screen (*⋮ → Rename subject*). PDF rows still match on the original name.

### Other students, programmes and semesters

Nothing in the app is tied to the sample report: there is no list of subjects, electives, programmes or semesters anywhere in the code. The row format is matched loosely so other reports still parse:

- **Dates:** `Jul 13, 2026`, `13.07.2026`, `13/07/2026`, `13-07-2026`, `13-Jul-2026`, `13 Jul 2026` (numeric dates are day first).
- **Times:** `12:00:01 PM`, `12:00 PM`, `12:00PM`, `12:00 p.m.`, or 24-hour `14:00`.
- **Status codes:** any code is accepted. P, A, E, L and NU are understood. Any other code (say `OD`) is kept as *not updated*, so it isn't counted, and the review screen lists it so you can set those lectures by hand. Before, such rows would have silently vanished.
- **Semesters:** each PDF is filed under the semester in its "Academic Year & Academic Session" line, e.g. *2027-2028, Semester III*. A new semester gets its own subjects, its own 15-week budget and its own name, roll number and programme from that PDF. Prefix merging only looks within one semester, so a new elective is never folded into last semester's subject. *Settings → Semester* switches which semester the app shows, or starts one by hand before its first PDF.

`ParserVariationsTest` covers another student's Semester III report with different electives, a tutorial, an unknown status code, case variants, a truncated name and other date and time formats. The same report was imported end to end on the emulator.

---

## The medical-leave model

Every lecture has one effective state:

| State | Comes from | In held? | In attended? |
|---|---|---|---|
| `PRESENT` | P / E / L in the PDF, or marked by hand | yes | yes |
| `ABSENT` | A in the PDF, or marked by hand | yes | no |
| `PENDING_LEAVE` | covered by a leave that hasn't been decided | **held apart** | **no** |
| `EXEMPT` | covered by an **approved** leave | depends on setting | depends on setting |
| `NOT_UPDATED` | NU in the PDF | no | no |

### The three percentages

```
decidedHeld     = present + absent        (+ exempt, when approved leave counts as attended)
decidedAttended = present                 (+ exempt, when approved leave counts as attended)
pending         = lectures in PENDING_LEAVE

confirmed  = decidedAttended / decidedHeld
ifApproved = (decidedAttended + pending) / (decidedHeld + pending)   "counted as attended"
           =  decidedAttended / decidedHeld                          "excluded from total"
ifRejected =  decidedAttended / (decidedHeld + pending)
```

`ifRejected` is the floor and `ifApproved` the ceiling. With anything pending, the app shows the range `floor – ceiling` and "N lectures awaiting decision". With nothing pending it shows one number.

**Approval convention** (Settings): institutions differ on what an approved leave means. *Counted as attended* (default) keeps the lectures in the total as present. *Excluded from total* removes them as if they were never held. Every figure is recomputed from the setting; neither behaviour is hard-coded.

### Worked example (from the brief)

Artificial Intelligence at 26 attended, 7 absent, 33 held = 78.8%. A medical leave covers 2 of the absences.

| | Value |
|---|---|
| Confirmed (decided only) | 26 / 31 = **83.9%** |
| If approved, counted as attended | 28 / 33 = **84.8%** |
| If approved, excluded from total | 26 / 31 = **83.9%** |
| If rejected | 26 / 33 = **78.8%** |

Shown as **`78.8% – 84.8%`**, "2 lectures awaiting decision". This exact case is a unit test (`AttendanceCalculatorTest`).

### The same flow on the real report

In the sample report AI is actually 37 / 50 = 74.0%. A leave over the two AI practical absences on 9 Oct 2026 gives:

| | Value |
|---|---|
| Pending | **`74.0% – 78.0%`** (confirmed 37/48 = 77.1%) |
| Approved (counted) | 39 / 50 = **78.0%** |
| Approved (excluded) | 37 / 48 = **77.1%** |
| Rejected | 37 / 50 = **74.0%** |
| One approved, one rejected | 38 / 50 = **76.0%**, request shows **PARTIAL** |

This is covered by `LeaveRulesTest` and was also run end to end on an emulator.

### How a leave moves

1. **Create.** Pick the dates (spinner date picker), the reason, who you submitted to (HOD / Course Coordinator) and when.
2. **Choose lectures.** Every lecture in the range is listed by date with subject, Theory/Practical, time and current status. All start ticked; untick what the leave doesn't cover. *All*, *None* and *By subject* helpers are available. Lectures already in another leave are greyed out.
3. **Save.** Ticked lectures become `PENDING_LEAVE` and are linked to the request. Their PDF status stays in `source_status`.
4. **Decide.** On the request: *Approve all*, *Reject all*, or a Pending / Approved / Rejected toggle per lecture. Approved → `EXEMPT`. Rejected → back to what the latest PDF says, or to your own manual mark if you had one.
5. **Request status** is derived from its lectures: `SUBMITTED` (all pending), `APPROVED`, `REJECTED`, or `PARTIAL` (any mix). A request can also be withdrawn, which puts every lecture back.

**From a single lecture:** you can also do all of this lecture by lecture, without opening the leave screens.
- **Mark it.** Long-press a lecture on the subject screen and choose *Medical leave submitted…* (HOD or Course Coordinator). It becomes *Pending leave* and joins any open request that already covers that date; otherwise it starts a one-day request.
- **Record the outcome.** Long-press it again later and choose *Leave approved*, *Leave rejected* (counts as absent again), *Remove from the leave*, or *Open the leave request*. The leave request and its status stay in step either way.

### Re-import reconciliation

When a newer PDF is imported:

- A **pending** lecture the PDF now shows as **P / E / L**: the college has acted, so the lecture is auto-approved.
- A **pending** lecture still showing **A** stays pending.
- The import summary says so, e.g. *"2 pending lectures were checked against this import: 2 approved, 0 still absent."*
- A lecture you **recorded as rejected** that the PDF now shows as attended is a **conflict**. So is a lecture you **changed by hand** that the PDF disagrees with. Your version is kept, and after saving you get a list to tick where the PDF is right. Nothing is overwritten silently.

---

## Screens

1. **Dashboard.** Overall percentage against the target (a range when leave is pending), with held / attended / missed / pending totals. Below it, one card per subject: percentage, progress bar, SAFE / AT RISK / DEPENDS ON LEAVE chip, the semester absence budget, and "N awaiting decision". The header says how many subjects are over their absence limit or close to it. With no data, an empty state offers *Upload attendance PDF* and *Add a subject by hand*.
2. **Import.** System file picker → progress → **review before saving**: student profile, rows read with status counts, the Sr No. sequence check (gaps are shown as a warning), subjects found with theory/lab counts, prefix merges listed explicitly, and what saving will do. A repeat import says *"already saved, saving again changes nothing."* Save / Cancel.
3. **Subject detail.** Combined, Theory and Practical figures, each with attended/held, missed and the pending range. The combined card carries the semester budget; the component cards show their planned share. *⋮ → Lectures per week* sets the timetable when the estimate from the PDF is wrong. Below that, the chronological lecture list with filter chips (All / Present / Absent / Pending / Not updated). Long-press a lecture to change its status, put it back to the PDF's, mark *medical leave submitted*, or record the leave's outcome. From the toolbar you can add a lecture, rename the subject, merge it into another one, or delete it.
4. **Medical leave.** The list of requests (dates, lecture count, status, subjects), the new-request flow, and request detail with per-lecture decisions, its effect on each subject, and a free-text note.
5. **Settings.** Attendance target (slider, default 80%), semester length (default 15 weeks), approval convention,  which semester to show (or start a new one), CSV export, and clear all data (with confirmation).
6. **Toolbar.** The light/dark button (moon or sun) sits just left of ⋮. The ⋮ menu holds SAP portal, Upload attendance PDF, Add subject, Medical leave, Guest mode (or *Exit guest mode*) and Settings. The bottom of Settings shows the version, *Check for updates*, and the credits.

### Getting the PDF: the SAP portal shortcut

*⋮ → SAP portal* (also on the empty dashboard) shows three steps, then opens <https://sdc-sppap1.svkm.ac.in:50001/irj/portal> in a **Custom Tab**: the phone's browser slides in over the app, in the app's colours.

1. Log in and download the student attendance report as PDF.
2. Tap **Open** on the download and choose **Upload to AttendEase**.
3. The import review screen opens directly. After *Save* you land on the dashboard.

AttendEase is registered for *Open with* and *Share* on PDFs, so this also works from the Files app, WhatsApp or email.

A built-in browser (WebView) was considered and rejected for two reasons:
- **Offline guarantee:** it would need the app to hold the INTERNET permission, which breaks "fully offline". With a Custom Tab the browser does the networking and the app needs no network permission at all.
- **Reliability:** SAP portal logins and PDF downloads are unreliable inside a WebView.

The Custom Tab is just a browser intent with one extra, so no library was added. Browsers without Custom Tab support open the page normally.

### Light and dark mode

The moon/sun button just left of ⋮ switches between light and dark. It follows the phone's setting until you first use it, then remembers your choice (guest mode included).

### Updates

The app has no internet access, so it can't check for updates by itself. *Settings → Check for updates* shows the installed version and opens the [Releases page](https://github.com/fortunatechick/AttendEase/releases/latest) in the browser. Download the newer APK there and install it over the old one; your data is kept.

### Guest mode

For a friend to check their own attendance on your phone, without touching yours.

- **Starts blank.** *⋮ → Guest mode → Start* switches the whole app to a **separate database file and separate settings**. Your database isn't even opened while it's on. A banner and a "Guest mode" line under every title make it obvious.
- **Works like normal.** The friend imports their PDF (or uses the SAP portal shortcut) and sees everything: dashboard, semester budget, subject detail, leaves.
- **One-time.** *Exit* (banner) or *⋮ → Exit guest mode* erases the guest's database and settings and brings your own data back exactly as it was. Starting guest mode again always begins blank.

Guest mode isn't a lock: anyone holding the phone can exit it. It keeps the two people's data apart, not secret.

### Semester absence budget

The college judges attendance over the **whole semester** (15 weeks), counting theory and lab absences together. So the question that matters is how many more lectures you can miss before you finish under 80%, not where the percentage stands today.

```
total        = max(lectures per week × weeks, lectures already recorded)
missesAllowed = floor(total × (100 − p) / 100)          p = threshold, default 80
canStillMiss  = missesAllowed − absences so far
```

| Timetable | Lectures in 15 weeks | Misses allowed at 80% |
|---|---|---|
| 2 theory + 2 lab a week | 60 | 12 |
| 1 theory + 2 lab, or 3 theory | 45 | 9 |

Missing exactly the allowed number finishes at 80% or just above; one more drops you below. Some details:

- **Pending leave** is shown as the worst case too, e.g. *"You can miss 1 more this semester (over the limit by 1 if the leave is rejected)"*.
- **Approved leave** isn't a miss under *counted as attended*. Under *excluded from total* it shrinks the total, and the budget with it.
- **Extra classes** beyond the timetable grow the total.
- **Over the limit:** the app says by how much, and the best figure you can still finish on by attending everything left. On the sample report, AI has 13 absences against 12 allowed, so even a perfect finish is 47 / 60 = **78.3%**.

**Lectures per week** are estimated from the PDF: the most common number of theory and lab lectures in a week, so holiday weeks and make-up classes don't skew it. Override it per subject (*⋮ → Lectures per week*) for electives with an irregular timetable, and set the semester length in Settings.

When a subject's weekly pattern can't be worked out yet (no lectures recorded), the card falls back to the running figure:

```
canMiss    = floor((100·attended − p·held) / p)          when at or above the target
mustAttend = ceil((p·held − 100·attended) / (100 − p))   when below it
```

Both formulas are checked against a brute-force search over every attended/held pair up to 60 lectures at five thresholds. The semester budget has its own tests for the 60/12 and 45/9 rules.

---

## Acceptance results on the sample report

The sample report runs 13.07.2026 – 11.10.2026: 16 pages and 327 rows, numbered 1–327 with no gaps. Statuses are **268 P, 59 A**, with no E, L or NU rows.

> **Note.** The original brief quoted figures (228 rows, 181 / 222, 7 subjects) from an earlier, shorter report. The numbers below are from the actual file and are what the tests assert.

| Subject | Attended / Held | % | Theory | Practical | Per week (est.) | Semester | Misses used / allowed | Can still miss |
|---|---|---|---|---|---|---|---|---|
| Artificial Intelligence | 37 / 50 | 74.0 | 19 / 26 | 18 / 24 | 2 + 2 | 60 | 13 / 12 | **over by 1** (best finish 78.3%) |
| Financial Der | 27 / 32 | 84.4 | 27 / 32 | – | 4 + 0 * | 60 | 5 / 12 | 7 |
| Image and Video Processing | 45 / 54 | 83.3 | 19 / 26 | 26 / 28 | 2 + 2 | 60 | 9 / 12 | 3 |
| Mobile Application Development | 38 / 46 | 82.6 | 20 / 24 | 18 / 22 | 2 + 2 | 60 | 8 / 12 | 4 |
| Operating Systems | 44 / 50 | 88.0 | 22 / 26 | 22 / 24 | 2 + 2 | 60 | 6 / 12 | 6 |
| RAG Sys&Agen WorkflowithPine&LaGr | 15 / 15 | 100.0 | 15 / 15 | – | 2 + 0 * | 30 | 0 / 6 | 6 |
| Research Methodology | 27 / 35 | 77.1 | 27 / 35 | – | 3 + 0 | 45 | 8 / 9 | 1 |
| Software Engineering | 35 / 45 | 77.8 | 19 / 25 | 16 / 20 | 2 + 2 | 60 | 10 / 12 | 2 |
| **Overall** | **268 / 327** | **82.0** | | | | | | |

\* Estimates worth checking. Financial Der alternates 4 and 2 lectures a week, and RAG stopped after week 8, so set their *Lectures per week* to the real timetable.

These results hold:

- **8 subjects** after one prefix merge (`Mobile Application Develop → Mobile Application Development`).
- **Importing twice changes nothing:** the second import reports 0 new, 0 updated, 327 unchanged. This is checked against real SQLite on a device and in `ImportPlannerTest`.
- The **AI leave flow** behaves as in the table above.
- The app **launches, imports and exports in airplane mode.** The APK requests no permissions at all: no `INTERNET`, and no notification permission, since the app never posts notifications.
- A **non-PDF** picked as a PDF shows *"This file isn't a readable PDF…"* with what to do, and doesn't crash. Password-protected and scanned PDFs get their own messages.

---

## Data model

SQLite through `SQLiteOpenHelper` (`AttendEaseDb`, version 3, with a step-by-step `onUpgrade`). Upgrades keep existing data:
- **Version 2** added the weekly-lecture columns with `ALTER TABLE`.
- **Version 3** added semesters. Your existing data becomes one semester, labelled from the last PDF header. The subject table is rebuilt with SQLite's documented procedure so a name only has to be unique within its semester; every `_id` is kept, so lectures stay attached.

Foreign keys are on. They're switched on after any upgrade, so the rebuild can't cascade-delete lectures.

```
semester      (_id, label UNIQUE NOCASE, created_at)                   -- e.g. "2026-2027, Semester V"
subject       (_id, semester_id, canonical_name, short_name, created_at,
               theory_per_week, lab_per_week,                          -- NULL = estimate from the PDF
               UNIQUE (semester_id, canonical_name))
subject_alias (semester_id, alias_key, subject_id)                     -- spellings merged by hand
lecture       (_id, subject_id, component 'T'|'P', date_millis, start_time, start_minutes, end_time,
               raw_course_name, batch_label, sr_no, source_status 'P'|'A'|'E'|'L'|'NU',
               effective_status 'PRESENT'|'ABSENT'|'PENDING_LEAVE'|'NOT_UPDATED'|'EXEMPT',
               manual_override, leave_id, import_run_id,
               UNIQUE (raw_course_name, date_millis, start_time))      -- the natural key
leave_request (_id, from_date, to_date, reason, submitted_to 'HOD'|'COURSE_COORDINATOR',
               submitted_on, status 'SUBMITTED'|'APPROVED'|'REJECTED'|'PARTIAL', note)
leave_lecture (leave_id, lecture_id, decision 'PENDING'|'APPROVED'|'REJECTED',
               previous_status, previous_manual)
import_run    (_id, file_name, imported_at, rows_parsed, rows_skipped, duration_from, duration_to)
```

Additions to the brief's schema, and why:

- `start_minutes`: sorting by the text `"10:00:01 AM"` puts 10 AM before 9 AM.
- `EXEMPT`: the state of an approved leave lecture, counted according to the convention setting.
- `previous_status` / `previous_manual`: let a rejection restore a lecture you had marked by hand before the leave.
- `subject.short_name`: the display name, which you can rename. `canonical_name` is what PDF rows match on.

Dates are stored as midnight UTC, so a lecture's date never shifts with the phone's time zone.

**Re-import** matches on the natural key and updates rows in place. Hand-added lectures are matched by subject + date + start time + component. A PDF row in the same slot replaces the hand-added one instead of counting twice.

**SharedPreferences** (`Prefs`) hold the student profile from the PDF header, the threshold, the semester length and the approval convention.

---

## Project layout

```
app/src/main/java/io/github/fortunatechick/attendease/
  parser/   AttendanceParser, LectureRecord, ParseResult, StudentProfile    -- plain Java, no Android
  calc/     AttendanceCalculator, AttendanceStats, BunkAdvice, SemesterBudget,
            WeeklyPattern, ApprovalConvention, CsvExporter                   -- plain Java, no Android
  data/     AttendEaseDb, *Dao, AttendanceRepository, LeaveRepository,
            ImportPlanner, SubjectResolver, LeaveRules, Status, Prefs,
            PdfTextExtractor                                                 -- ImportPlanner / SubjectResolver /
                                                                                LeaveRules are plain Java
  *.java    Activities, adapters, ThemeMode, SapPortal, Background (single DB thread)
app/src/test/java/...                                                       -- JVM unit tests
docs/spike/pdfbox_dump.txt                                                   -- raw PdfBox output of the sample (git-ignored)
```

Anything that decides a number lives in a plain-Java class with a JVM test. The activities only display and collect input.

**Threading.** PDF extraction runs on an `ExecutorService` inside `ImportViewModel`, so it survives rotation. All other database work goes through `Background`, a single-thread executor. Results come back through a `Handler` on the main looper. Nothing touches the database on the UI thread.

---

## Building and running

**Requirements:** Android Studio (recent), JDK 17+, Android SDK platform 36. Minimum Android version: 5.0 (API 21).

**Android Studio:** *File → Open* → select the `AttendEase` folder, let Gradle sync, then run the `app` configuration on a device or emulator.

**Command line** (Windows; use `./gradlew` on macOS/Linux):

```bash
gradlew.bat assembleDebug
```

```bash
gradlew.bat testDebugUnitTest
```

The debug APK lands in `app/build/outputs/apk/debug/app-debug.apk`. For the shareable build, run `gradlew.bat assembleRelease`; it writes `app/build/outputs/apk/release/app-release.apk`, which is what the Releases page carries (renamed `AttendEase.apk`). The unit tests run on the JVM with no emulator: 98 tests covering the parser (the sample report, a fixture, and other students' report variations), the re-import planner, subject resolution, leave rules, the calculator, the semester budget and the CSV export.

**Trying it:** copy the attendance PDF onto the phone (Downloads works), open AttendEase, tap *Upload attendance PDF*, pick the file, check the review, and tap *Save*.

### Dependencies

| Library | Why |
|---|---|
| `com.tom-roush:pdfbox-android:2.0.27.0` | PDF text extraction (the only third-party library) |
| `androidx.appcompat`, `com.google.android.material`, `androidx.recyclerview` | Standard Android UI platform libraries |
| `junit:junit` (test only) | Unit tests |

There is no Apache POI: the Excel export is a CSV, which Excel opens directly. There is also no Room, Compose, data binding, Firebase, analytics or network access.

Build setup: Android Gradle Plugin 8.13, Gradle 8.14.3, `compileSdk`/`targetSdk` 36, Java 17 source level, Groovy build scripts.
