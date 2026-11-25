# Not So Naive: String Matching Simulator (UFPS)

Web simulator of the **Not So Naive** exact string-matching algorithm, built for the course
*Analysis of Algorithms* at the Universidad Francisco de Paula Santander (UFPS).

- **Live:** https://nat-so-naive.onrender.com
- **Algorithm page:** https://nat-so-naive.onrender.com/algorithm
- **Source ZIP (NetBeans project):** https://nat-so-naive.onrender.com/delivery
- **Repository:** https://github.com/juandi12334/nat-so-naive

## Project data

| | |
|---|---|
| Student | Juan Diego Rojas Rey |
| Email | juandiegorore@ufps.edu.co |
| Course | Analysis of Algorithms |
| Program | Systems Engineering |
| University | Universidad Francisco de Paula Santander (UFPS) |
| Location | Avenida Gran Colombia No. 12E-96, Barrio Colsag, San José de Cúcuta, Norte de Santander, Colombia |
| Professor | Marco Antonio Adarme Jaimes |
| Date | 24 November 2026 |

## The method

**Not So Naive** (Christophe Hancart, 1992; as published by C. Charras and T. Lecroq in
*Exact String Matching Algorithms*) finds every occurrence of a pattern `x` (length `m`) in a text
`y` (length `n`), overlapping occurrences included. Like the naive algorithm it slides a window of
length `m` over the text, but it compares the pattern positions in the order `1, 2, …, m−1` and
finally `0`. Looking at `x[1]` first lets it move the window **two** positions in some cases without
ever skipping an occurrence.

### Preprocessing: k and ell (O(1))

| Condition | k (shift when `x[1] ≠ y[j+1]`) | ell (shift when `x[1] = y[j+1]`) |
|---|---|---|
| `x[0] == x[1]` | 2 | 1 |
| `x[0] != x[1]` | 1 | 2 |

### Searching phase

```
j ← 0
while j ≤ n − m:
    if x[1] ≠ y[j+1]:
        j ← j + k
    else:
        if x[2..m−1] = y[j+2..j+m−1] and x[0] = y[j]:
            report j
        j ← j + ell
```

A shift of 2 is safe because an occurrence can start at `j+1` only if `y[j+1] = x[0]`, and
`y[j+1]` has just been compared with `x[1]`: if `x[0] ≠ x[1]` and `y[j+1] = x[1]`, or if
`x[0] = x[1]` and `y[j+1] ≠ x[1]`, then `y[j+1] ≠ x[0]`.

A one-character pattern has no `x[1]`: every window compares only `x[0]` and moves 1.

### Complexity

- **Preprocessing:** O(1) time and space.
- **Search, worst case:** O(n·m): at most `n − m + 1` windows and `m` comparisons per window
  (e.g. `y = aⁿ`, `x = aᵐ`), the same as the naive algorithm.
- **Extra space:** O(1), excluding the stored positions.
- **Average:** slightly sub-linear by a constant factor in the number of comparisons; the gain is
  largest when `x[0] = x[1]` (up to about half the comparisons of the naive search).

### Short example: `ANA` in `BANXANA`

`A ≠ N`, so `k = 1` and `ell = 2`; the last start is `n − m = 4`.

| j | What happens | Next j |
|---|---|---|
| 0 | `x[1]` = N vs `y[1]` = A: mismatch | 0 + k = 1 |
| 1 | `x[1]` = N matches; `x[2]` = A vs `y[3]` = X: mismatch | 1 + ell = 3 |
| 3 | `x[1]` = N vs `y[4]` = A: mismatch | 3 + k = 4 |
| 4 | `x[1]`, `x[2]` and finally `x[0]` match: **occurrence at 4** | 4 + ell = 6 |
| 6 | 6 > 4: the pattern no longer fits, the search ends | |

## What the application does

**Simulator page (`/`)**

- **1 Text file:** choose or drag an ASCII `.txt` file, or type / paste the text in the
  *Or type your text* box. The last source touched is the active one (typing clears the file,
  choosing a file clears the box). Both follow the same rules: ASCII only, up to 2 MB, line endings
  normalized to `\n`.
- **2 Pattern:** exact, case-sensitive, printable ASCII.
- **Actions:** *Run Not So Naive search*, and *Generate PDF*, which downloads a PDF generated on
  the server with Apache PDFBox: the full text with every occurrence highlighted.
- **Results:** occurrence count, the 0-based positions as chips (first 30 plus a *+N more* chip),
  and the text with every occurrence highlighted (overlaps shown darker), with previous / next navigation.
- **Step-by-step trace** (texts up to 300 characters): a preprocessing row with `x[0]`, `x[1]`,
  `k` and `ell`; one block per window `j` actually visited, drawing the pattern under the text,
  shifted by `j` cells, with the compared characters highlighted (green when equal; red in the
  pattern and black in the text when different) and the real values of each comparison. Blocks that
  pass the `x[1]` test have their own stepper (previous / next / Auto, and arrow keys). A final
  block shows `j > n − m`. The *Matches* counter counts the match lines currently visible.

**Algorithm page (`/algorithm`):** idea and origin, preprocessing, searching phase with a worked
example, why a shift of 2 is safe, complexity, the base code in one method, and implementation notes.

**Delivery page (`/delivery`):** download of the complete source project as a ZIP.

Every page has the same header and a footer with the project data. The footer values are configured
in one place: `app.site.*` in `src/main/resources/application.properties`.

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/` | Simulator |
| GET | `/algorithm` | Algorithm explanation |
| GET | `/delivery` | Source ZIP page (`/entrega` redirects here) |
| GET | `/delivery/not-so-naive-project.zip` | Source project ZIP |
| POST | `/api/search` | Multipart: `file` **or** `text`, and `pattern`. Returns JSON with the count, positions, highlighted-preview text, `k`, `ell`, the trace (`j`, `outcome`, `shift`, `comparisons`, `stopI`) and `finalJ` |
| POST | `/api/pdf` | Same parameters; returns the highlighted PDF |

## Implementation

- `algorithm/NotSoNaive`: the business class. The constructor does the O(1) preprocessing; the
  search is a direct translation of the Charras & Lecroq C code (no `indexOf`, regular expressions
  or other matchers). It also records the trace (each visited `j`, its outcome, the shift and the
  value of `i` where the inner loop stopped) so the simulator's trace and the reported positions
  come from the same run.
- **SEED data structures** (`ufps.util.colecciones_seed`, unmodified UFPS classes): `ListaCD`
  collects the positions and the trace (O(1) insertion at the end) and `Secuencia` stores the final
  positions for O(1) indexed access. The production code uses no `java.util` collections.
- `service/FileService` (ASCII validation of files and typed text), `SearchService`,
  `PdfService` + `PdfLayoutEngine` (PDFBox), `ProjectArchiveService` (ZIP).
- `web/SearchController`, `DeliveryController`, `SearchResponse`, `SiteInfo` + `SiteModelAdvice`
  (footer data), `GlobalExceptionHandler`.

### Project structure

```
src/main/java/co/edu/ufps/notsonaive/
  algorithm/  NotSoNaive, SearchStatistics, TraceStep
  service/    FileService, SearchService, PdfService, PdfLayoutEngine, ProjectArchiveService
  web/        SearchController, DeliveryController, SearchResponse, SiteInfo, SiteModelAdvice, GlobalExceptionHandler
  model/      TextDocument, SearchResult
  support/    SeedCollections
  exception/  InvalidFileException, InvalidPatternException
src/main/java/ufps/util/colecciones_seed/   ListaCD, NodoD, IteratorLCD, Secuencia, ExceptionUFPS
src/main/resources/
  templates/  index.html, algorithm.html, delivery.html, fragments/layout.html (header + footer)
  static/     css/app.css, js/app.js
  application.properties
src/test/java/...   JUnit 5 tests (algorithm, trace, files, PDF, endpoints)
Dockerfile
```

## Run locally

Requirements: **Java 21** and **Maven 3.9+**.

```bash
mvn test                 # run the JUnit tests
mvn spring-boot:run      # start the app at http://localhost:8080
```

From Apache NetBeans: *File → Open Project*, select this folder, then *Run*
(main class `co.edu.ufps.notsonaive.NotSoNaiveApplication`).

## Deployment

The app is deployed on **Render** (free web service) from this repository's `main` branch, using
the `Dockerfile` (Maven build stage + Java 21 runtime). Render provides the port in `$PORT`. Free
instances sleep after a period of inactivity, so the first request after a pause can take about a
minute.

## Reference

C. Charras and T. Lecroq, *Exact String Matching Algorithms*, Not So Naive:
https://www-igm.univ-mlv.fr/~lecroq/string/node13.html
