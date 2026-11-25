/*
 * Not So Naive simulator - front-end logic.
 * The browser never searches the text: it sends the text (file or typed) and the pattern
 * to the Java back-end and only paints what Not So Naive returned, including the trace.
 */
(function () {
    'use strict';

    const MAX_BYTES = parseInt(document.body.dataset.maxBytes || '2097152', 10);
    const TRACE_LIMIT = parseInt(document.body.dataset.traceLimit || '300', 10);
    const CHIP_LIMIT = 30;

    const $ = (id) => document.getElementById(id);
    const els = {
        dropzone: $('dropzone'), fileInput: $('fileInput'), pickBtn: $('pickBtn'), changeBtn: $('changeBtn'),
        fileCard: $('fileCard'), fileName: $('fileName'), fileSize: $('fileSize'), fileError: $('fileError'),
        typedText: $('typedText'), textError: $('textError'),
        pattern: $('pattern'), patternShown: $('patternShown'), patternLen: $('patternLen'), patternError: $('patternError'),
        searchBtn: $('searchBtn'), pdfBtn: $('pdfBtn'), pdfStatus: $('pdfStatus'),
        statusPill: $('statusPill'), statusText: $('statusText'),
        resultsEmpty: $('resultsEmpty'), processing: $('processing'), errorBox: $('errorBox'), errorText: $('errorText'),
        results: $('results'), occChip: $('occChip'), positions: $('positions'),
        preview: $('preview'), previewNote: $('previewNote'),
        prevMatch: $('prevMatch'), nextMatch: $('nextMatch'), matchNav: $('matchNav'),
        traceSection: $('traceSection'), traceRows: $('traceRows'), traceCounter: $('traceCounter'),
        traceNotice: $('traceNotice'), traceWarning: $('traceWarning')
    };

    // source: 'file' | 'text' | null  (the last source the user touched is the active one)
    const state = {source: null, file: null, last: null, current: -1, chipsExpanded: false, busy: false, autoTimers: []};

    $('maxSize').textContent = formatBytes(MAX_BYTES);

    // ---------- helpers ----------
    function formatBytes(b) {
        if (b < 1024) return b + ' B';
        if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
        return (b / (1024 * 1024)).toFixed(2) + ' MB';
    }

    const fmt = (n) => Number(n).toLocaleString('en-US');

    function esc(s) {
        return String(s).replace(/[&<>"]/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'}[c]));
    }

    function setStatus(kind, text) {
        els.statusPill.className = 'pill pill--' + kind;
        els.statusText.textContent = text;
    }

    function showError(el, msg) {
        el.textContent = msg || '';
        el.hidden = !msg;
    }

    // ---------- source 1: file ----------
    function acceptFile(file) {
        showError(els.fileError, '');
        if (!file) return;
        if (!/\.txt$/i.test(file.name)) {
            showError(els.fileError, 'Only .txt files are accepted. "' + file.name + '" is not a .txt file.');
            return;
        }
        if (file.size === 0) {
            showError(els.fileError, 'The file "' + file.name + '" is empty.');
            return;
        }
        if (file.size > MAX_BYTES) {
            showError(els.fileError, 'The file is too large (' + formatBytes(file.size) + '). Maximum: ' + formatBytes(MAX_BYTES) + '.');
            return;
        }
        state.file = file;
        state.source = 'file';
        // choosing a file clears the typed text
        els.typedText.value = '';
        showError(els.textError, '');
        els.fileName.textContent = file.name;
        els.fileSize.textContent = formatBytes(file.size) + ' · ' + fmt(file.size) + ' bytes';
        els.fileCard.hidden = false;
        els.dropzone.hidden = true;
        setStatus('loaded', 'File loaded: ' + file.name);
        updateButtons();
    }

    function clearFile() {
        state.file = null;
        els.fileInput.value = '';
        els.fileCard.hidden = true;
        els.dropzone.hidden = false;
        showError(els.fileError, '');
    }

    function openPicker() {
        els.fileInput.value = '';
        els.fileInput.click();
    }

    els.pickBtn.addEventListener('click', (e) => { e.stopPropagation(); openPicker(); });
    els.dropzone.addEventListener('click', openPicker);
    els.dropzone.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openPicker(); }
    });
    els.changeBtn.addEventListener('click', openPicker);
    els.fileInput.addEventListener('change', () => acceptFile(els.fileInput.files[0]));

    ['dragenter', 'dragover'].forEach((ev) => els.dropzone.addEventListener(ev, (e) => {
        e.preventDefault(); els.dropzone.classList.add('is-over');
    }));
    ['dragleave', 'drop'].forEach((ev) => els.dropzone.addEventListener(ev, (e) => {
        e.preventDefault(); els.dropzone.classList.remove('is-over');
    }));
    els.dropzone.addEventListener('drop', (e) => {
        const files = e.dataTransfer && e.dataTransfer.files;
        if (files && files.length > 1) { showError(els.fileError, 'Drop a single .txt file.'); return; }
        acceptFile(files && files[0]);
    });
    document.addEventListener('dragover', (e) => e.preventDefault());
    document.addEventListener('drop', (e) => {
        e.preventDefault();
        if (e.target.closest && (e.target.closest('#dropzone') || e.target.closest('#typedText'))) return;
        const files = e.dataTransfer && e.dataTransfer.files;
        if (files && files.length === 1) acceptFile(files[0]);
    });

    // ---------- source 2: typed text ----------
    function typedProblem(t) {
        if (t.length > MAX_BYTES) return 'The text is too long (' + fmt(t.length) + ' characters). Maximum: ' + fmt(MAX_BYTES) + '.';
        let line = 1, col = 1;
        for (let i = 0; i < t.length; i++) {
            const c = t.charCodeAt(i);
            const ok = (c >= 0x20 && c <= 0x7e) || c === 9 || c === 10 || c === 13;
            if (!ok) return 'Only ASCII text is allowed: "' + t[i] + '" at line ' + line + ', column ' + col + '.';
            if (c === 10) { line++; col = 1; } else { col++; }
        }
        return '';
    }

    els.typedText.addEventListener('input', () => {
        const t = els.typedText.value;
        if (t.length > 0) {
            if (state.file) clearFile();     // typing clears the selected file
            state.source = 'text';
            setStatus('loaded', 'Typed text: ' + fmt(t.length) + ' characters');
        } else if (state.source === 'text') {
            state.source = null;
            setStatus('idle', 'No file selected');
        }
        showError(els.textError, t ? typedProblem(t) : '');
        updateButtons();
    });

    function inputProblem() {
        if (state.source === 'file' && state.file) return '';
        if (state.source === 'text') {
            const t = els.typedText.value;
            return t.length === 0 ? 'Type some text or choose a .txt file.' : typedProblem(t);
        }
        return 'Choose a .txt file or type your text.';
    }

    // ---------- pattern ----------
    function patternProblem(p) {
        if (p.length === 0) return 'The pattern must not be empty.';
        for (let i = 0; i < p.length; i++) {
            const c = p.charCodeAt(i);
            if (c < 0x20 || c > 0x7e) return 'Only printable ASCII characters are allowed (character ' + (i + 1) + ' is not).';
        }
        return '';
    }

    function renderPattern() {
        const p = els.pattern.value;
        if (p.length === 0) {
            els.patternShown.textContent = '—';
            els.patternShown.classList.add('is-empty');
            els.patternLen.textContent = '';
        } else {
            els.patternShown.textContent = p.replace(/ /g, '·');
            els.patternShown.classList.remove('is-empty');
            els.patternLen.textContent = 'm = ' + p.length + (p.includes(' ') ? ' (· = space)' : '');
        }
        const problem = p.length ? patternProblem(p) : '';
        showError(els.patternError, problem);
        els.pattern.classList.toggle('is-invalid', !!problem);
        updateButtons();
    }

    els.pattern.addEventListener('input', renderPattern);
    els.pattern.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !els.searchBtn.disabled) runSearch();
    });

    function updateButtons() {
        const ready = !state.busy && inputProblem() === '' && patternProblem(els.pattern.value) === '';
        els.searchBtn.disabled = !ready;
        els.pdfBtn.disabled = !ready;
    }

    function setBusy(btn, busy) {
        state.busy = busy;
        btn.classList.toggle('is-loading', busy);
        updateButtons();
    }

    function formData() {
        const fd = new FormData();
        if (state.source === 'file' && state.file) {
            fd.append('file', state.file, state.file.name);
        } else {
            fd.append('text', els.typedText.value);
        }
        fd.append('pattern', els.pattern.value);
        return fd;
    }

    async function readError(res) {
        try {
            const body = await res.json();
            if (body && body.error) return body.error;
        } catch (_) { /* not JSON */ }
        return 'The server answered with status ' + res.status + '.';
    }

    // ---------- search ----------
    async function runSearch() {
        if (els.searchBtn.disabled) return;
        stopAllAuto();
        state.last = null;
        els.results.hidden = true;
        els.errorBox.hidden = true;
        els.resultsEmpty.hidden = true;
        els.processing.hidden = false;
        els.traceSection.hidden = true;
        setStatus('busy', 'Searching in your browser…');
        setBusy(els.searchBtn, true);
        try {
            let text, fileName = 'typed-text.txt', fileSizeBytes;
            if (state.source === 'file' && state.file) {
                const bytes = new Uint8Array(await state.file.arrayBuffer());
                fileSizeBytes = bytes.length;
                let offset = 0;
                if (bytes.length >= 3 && bytes[0] === 0xEF && bytes[1] === 0xBB && bytes[2] === 0xBF) offset = 3;
                let line = 1, col = 1;
                for (let i = offset; i < bytes.length; i++) {
                    const c = bytes[i];
                    if (!((c >= 0x20 && c <= 0x7e) || c === 9 || c === 10 || c === 13)) {
                        throw new Error('Only ASCII text is allowed in TXT files (invalid byte at line ' + line + ', column ' + col + ').');
                    }
                    if (c === 10) { line++; col = 1; } else col++;
                }
                text = new TextDecoder('ascii').decode(bytes.slice(offset));
                fileName = state.file.name;
            } else {
                text = els.typedText.value;
                fileSizeBytes = new TextEncoder().encode(text).length;
            }
            if (!text.length) throw new Error('The text is empty. Type some text or choose a .txt file.');
            text = text.replace(/\r\n?/g, '\n');
            const started = performance.now();
            const result = window.NotSoNaive.search(text, els.pattern.value, {trace: text.length <= TRACE_LIMIT});
            const elapsedMillis = performance.now() - started;
            const maxPositions = 50000, maxPreview = 200000;
            const data = {
                algorithm: 'Not So Naive', fileName, fileSizeBytes,
                textLength: text.length, lineCount: text.split('\n').length,
                pattern: els.pattern.value, patternLength: els.pattern.value.length,
                matchCount: result.positions.length,
                positions: result.positions.slice(0, maxPositions),
                positionsTruncated: result.positions.length > maxPositions,
                elapsedMillis, comparisons: result.comparisons, attempts: result.attempts,
                k: result.k, ell: result.ell, notices: [],
                text: text.length > maxPreview ? text.slice(0, maxPreview) : text,
                previewTruncated: text.length > maxPreview,
                trace: result.trace, finalJ: result.finalJ, traceLimit: 500
            };
            state.last = data;
            state.fullText = text;
            state.fullPositions = result.positions;
            renderResults(data);
            renderTrace(data);
            setStatus('done', 'Search completed · ' + fmt(data.matchCount) + ' occurrence' + (data.matchCount === 1 ? '' : 's'));
        } catch (err) {
            els.processing.hidden = true;
            els.errorText.textContent = err.message || String(err);
            els.errorBox.hidden = false;
            setStatus('error', 'Processing error');
        } finally {
            setBusy(els.searchBtn, false);
        }
    }

    els.searchBtn.addEventListener('click', runSearch);

    // ---------- results card ----------
    function renderResults(d) {
        els.processing.hidden = true;
        els.results.hidden = false;
        els.occChip.textContent = 'Occurrences: ' + fmt(d.matchCount);
        els.occChip.classList.toggle('occ-chip--ok', d.matchCount > 0);
        state.current = -1;
        state.chipsExpanded = false;
        renderChips();
        renderPreview(d);
    }

    function renderChips() {
        const d = state.last;
        const list = d.positions;
        els.positions.innerHTML = '';
        if (list.length === 0) {
            els.positions.innerHTML = '<span class="none">none</span>';
            return;
        }
        const shown = state.chipsExpanded ? list.length : Math.min(list.length, CHIP_LIMIT);
        const frag = document.createDocumentFragment();
        for (let i = 0; i < shown; i++) {
            const b = document.createElement('button');
            b.type = 'button';
            b.className = 'pos-chip' + (i === state.current ? ' is-current' : '');
            b.textContent = list[i];
            b.dataset.idx = i;
            b.title = 'Occurrence ' + (i + 1) + ' starts at position ' + list[i];
            frag.appendChild(b);
        }
        if (list.length > CHIP_LIMIT) {
            const more = document.createElement('button');
            more.type = 'button';
            more.className = 'pos-chip pos-chip--more';
            more.dataset.toggle = '1';
            more.textContent = state.chipsExpanded ? 'show less' : '+' + fmt(list.length - CHIP_LIMIT) + ' more';
            more.title = state.chipsExpanded ? 'Show only the first ' + CHIP_LIMIT : list.slice(CHIP_LIMIT, CHIP_LIMIT + 200).join(', ')
                + (list.length > CHIP_LIMIT + 200 ? ', …' : '');
            frag.appendChild(more);
        }
        if (d.positionsTruncated) {
            const note = document.createElement('span');
            note.className = 'none';
            note.textContent = '(first ' + fmt(list.length) + ' of ' + fmt(d.matchCount) + ' listed; the PDF has all)';
            frag.appendChild(note);
        }
        els.positions.appendChild(frag);
    }

    els.positions.addEventListener('click', (e) => {
        const b = e.target.closest('button');
        if (!b) return;
        if (b.dataset.toggle) { state.chipsExpanded = !state.chipsExpanded; renderChips(); return; }
        focusMatch(parseInt(b.dataset.idx, 10));
    });

    /*
     * The preview is built from the server positions only. Boundaries are placed at every
     * occurrence start/end; each segment is painted according to how many occurrences
     * cover it (1 = match, 2+ = overlap). Each occurrence start gets an anchor id.
     */
    function renderPreview(d) {
        const text = d.text;
        const n = text.length;
        const m = d.patternLength;
        const pos = d.positions.filter((p) => p < n);
        const delta = new Map();
        const starts = new Map();
        const add = (k, v) => delta.set(k, (delta.get(k) || 0) + v);
        pos.forEach((p, i) => {
            add(p, 1);
            add(Math.min(n, p + m), -1);
            if (!starts.has(p)) starts.set(p, i);
        });
        const bounds = Array.from(new Set([0, n, ...delta.keys()])).sort((a, b) => a - b);
        let depth = 0;
        const out = [];
        for (let b = 0; b < bounds.length - 1; b++) {
            const a = bounds[b];
            depth += delta.get(a) || 0;
            const seg = esc(text.slice(a, bounds[b + 1]));
            const anchor = starts.has(a) ? ' id="m' + starts.get(a) + '"' : '';
            out.push(depth > 0 ? '<mark class="' + (depth > 1 ? 'd2' : 'd1') + '"' + anchor + '>' + seg + '</mark>' : seg);
        }
        els.preview.innerHTML = out.join('');
        els.previewNote.hidden = !d.previewTruncated;
        if (d.previewTruncated) {
            els.previewNote.textContent = 'Preview limited to the first ' + fmt(n) + ' characters of ' + fmt(d.textLength)
                + '. The count, the positions and the PDF cover the whole text.';
        }
        updateMatchNav();
    }

    function updateMatchNav() {
        const total = state.last ? state.last.positions.length : 0;
        els.matchNav.textContent = total === 0 ? '0 / 0' : (state.current < 0 ? '–' : state.current + 1) + ' / ' + fmt(total);
        els.prevMatch.disabled = total === 0;
        els.nextMatch.disabled = total === 0;
    }

    function focusMatch(i) {
        const total = state.last.positions.length;
        if (total === 0) return;
        state.current = (i + total) % total;
        if (state.current >= CHIP_LIMIT && !state.chipsExpanded) state.chipsExpanded = true;
        renderChips();
        els.preview.querySelectorAll('mark.is-current').forEach((el) => el.classList.remove('is-current'));
        const el = document.getElementById('m' + state.current);
        if (el) {
            el.classList.add('is-current');
            const top = el.offsetTop - els.preview.offsetTop - els.preview.clientHeight / 3;
            els.preview.scrollTo({top: Math.max(0, top), behavior: 'smooth'});
        }
        updateMatchNav();
    }

    els.prevMatch.addEventListener('click', () => focusMatch(state.current < 0 ? -1 : state.current - 1));
    els.nextMatch.addEventListener('click', () => focusMatch(state.current + 1));

    // ---------- step-by-step trace ----------
    /*
     * Each block is rebuilt from the attempt recorded by the Java algorithm:
     * j, outcome, shift and stopI (the value of i when the inner loop ended).
     * A step = {hl: {patternIndex: kind}, lines: [html], match: bool}.
     * kind: 'eq' (equal, green), 'ne' (different), 'done' (already matched, green).
     */
    function buildSteps(d, row) {
        const x = d.pattern, y = d.text, m = d.patternLength, j = row.j;
        const steps = [];
        const C = (s) => '<span class="c">' + esc(s) + '</span>';
        const OK = (s) => '<span class="ok">' + esc(s) + '</span>';
        const BAD = (s) => '<span class="bad">' + esc(s) + '</span>';
        const pair = (a, b) => a === b ? OK(a) + ' = ' + OK(b) : '<span class="pat">' + esc(a) + '</span> ≠ <span class="txt">' + esc(b) + '</span>';

        if (m === 1) {
            const eq = x[0] === y[j];
            const lines = [C('x[0] vs y[j]'), pair(x[0], y[j])];
            if (eq) lines.push('<span class="badge-match">Match at position ' + j + '</span>');
            else lines.push(BAD("Characters don't match"));
            lines.push(C('j = j + 1') + ' → ' + C('j = ' + j + ' + 1 = ' + (j + 1)));
            steps.push({hl: {0: eq ? 'eq' : 'ne'}, lines, match: eq});
            return steps;
        }

        const ell = d.ell, k = d.k;
        if (row.outcome === 'MISMATCH_SECOND') {
            steps.push({
                hl: {1: 'ne'},
                lines: [C('x[1] vs y[j+1]'), pair(x[1], y[j + 1]), BAD("Characters don't match"),
                    C('j = j + k') + ' → ' + C('j = ' + j + ' + ' + k + ' = ' + (j + k))],
                match: false
            });
            return steps;
        }

        // x[1] matched
        const done = {1: 'done'};
        steps.push({hl: {1: 'eq'}, lines: [C('x[1] vs y[j+1]'), pair(x[1], y[j + 1])], match: false});

        const stopI = row.stopI;
        for (let i = 2; i < m && i <= stopI; i++) {
            const eq = i < stopI;           // the loop only stops (i == stopI < m) on a mismatch
            const a = x[i], b = y[j + i];
            const hl = Object.assign({}, done);
            hl[i] = eq ? 'eq' : 'ne';
            const lines = [
                C('i = ' + i),
                C('(i < m && x[i] == y[j + i])'),
                C('(' + i + ' < ' + m + ' && x[' + i + '] == y[' + (j + i) + '])'),
                eq ? C('(' + i + ' < ' + m + ' && ') + OK(a) + C(' == ') + OK(b) + C(')')
                    : C('(' + i + ' < ' + m + ' && ') + '<span class="false"><span class="pat">' + esc(a) + '</span> == <span class="txt">' + esc(b) + '</span></span>' + C(')') + ' <span class="bad">different</span>'
            ];
            steps.push({hl, lines, match: false});
            if (eq) done[i] = 'done';
        }

        if (stopI < m) {
            const hl = Object.assign({}, done);
            hl[stopI] = 'ne';
            steps.push({hl, lines: [BAD("Characters don't match"),
                C('j = j + ell') + ' → ' + C('j = ' + j + ' + ' + ell + ' = ' + (j + ell))], match: false});
            return steps;
        }

        // i == m: compare x[0] last
        const eq0 = x[0] === y[j];
        const hl = Object.assign({}, done);
        hl[0] = eq0 ? 'eq' : 'ne';
        if (eq0) for (let p = 1; p < m; p++) hl[p] = 'eq';
        const lines = [C('i == m && x[0] == y[j]'),
            C(m + ' == ' + m + ' && ') + (eq0 ? OK(x[0]) + C(' == ') + OK(y[j])
                : '<span class="false"><span class="pat">' + esc(x[0]) + '</span> == <span class="txt">' + esc(y[j]) + '</span></span>')];
        lines.push(eq0 ? '<span class="badge-match">Match at position ' + j + '</span>' : BAD('No match, continue'));
        lines.push(C('j = j + ell') + ' → ' + C('j = ' + j + ' + ' + ell + ' = ' + (j + ell)));
        steps.push({hl, lines, match: eq0});
        return steps;
    }

    function cellsHtml(str, offset, hl, role, width) {
        let html = '';
        for (let c = 0; c < width; c++) {
            const idx = c - offset;
            if (idx < 0 || idx >= str.length) {
                html += '<span class="cell cell--empty"></span>';
                continue;
            }
            const kind = hl[idx];
            const cls = kind ? ' cell--' + kind + ' cell--' + role : '';
            const ch = str[idx];
            html += '<span class="cell' + cls + '">' + (ch === ' ' ? '·' : ch === '\n' ? '↵' : ch === '\t' ? '⇥' : esc(ch)) + '</span>';
        }
        return html;
    }

    function indexHtml(width) {
        let html = '';
        for (let c = 0; c < width; c++) html += '<span class="cell cell--idx">' + c + '</span>';
        return html;
    }

    // map pattern highlights to the text cells under the window
    function textHl(hl, j) {
        const t = {};
        Object.keys(hl).forEach((p) => { t[j + parseInt(p, 10)] = hl[p]; });
        return t;
    }

    function renderBlock(block) {
        const d = state.last;
        const step = block.steps[block.at];
        const width = block.width;
        block.center.innerHTML =
            '<div class="cells">' + indexHtml(width) + '</div>' +
            '<div class="cells">' + cellsHtml(d.text, 0, textHl(step.hl, block.j), 'txt', width) + '</div>' +
            '<div class="cells">' + cellsHtml(d.pattern, block.j, step.hl, 'pat', width) + '</div>';
        block.right.innerHTML = step.lines.map((l) => '<div class="code-line">' + l + '</div>').join('');
        if (block.counter) {
            block.counter.textContent = (block.at + 1) + ' / ' + block.steps.length;
            block.prev.disabled = block.at === 0;
            block.next.disabled = block.at === block.steps.length - 1;
            block.auto.textContent = block.timer ? 'Stop' : 'Auto';
            block.auto.setAttribute('aria-pressed', block.timer ? 'true' : 'false');
        }
        updateTraceCounter();
    }

    function updateTraceCounter() {
        const blocks = state.blocks || [];
        const visible = blocks.filter((b) => b.steps[b.at].match).length;
        const total = blocks.filter((b) => b.steps[b.steps.length - 1].match).length;
        els.traceCounter.textContent = 'Matches: ' + fmt(visible) + ' / ' + fmt(total);
        els.traceCounter.classList.toggle('occ-chip--ok', visible > 0);
    }

    function go(block, delta) {
        const at = Math.max(0, Math.min(block.steps.length - 1, block.at + delta));
        if (at !== block.at) { block.at = at; renderBlock(block); }
    }

    function stopAuto(block) {
        if (block.timer) { clearInterval(block.timer); block.timer = null; renderBlock(block); }
    }

    function stopAllAuto() {
        (state.blocks || []).forEach((b) => { if (b.timer) { clearInterval(b.timer); b.timer = null; } });
    }

    function toggleAuto(block) {
        if (block.timer) { stopAuto(block); return; }
        if (block.at === block.steps.length - 1) { block.at = 0; }   // replay from the start
        block.timer = setInterval(() => {
            if (block.at >= block.steps.length - 1) { stopAuto(block); return; }
            block.at++;
            renderBlock(block);
            if (block.at >= block.steps.length - 1) stopAuto(block);
        }, 900);
        renderBlock(block);
    }

    function row(label, extraClass) {
        const r = document.createElement('div');
        r.className = 'trow' + (extraClass ? ' ' + extraClass : '');
        r.innerHTML = '<div class="trow__left"></div><div class="trow__center"><div class="trow__scroll"></div></div><div class="trow__right"></div>';
        r.querySelector('.trow__left').innerHTML = label;
        return r;
    }

    function renderTrace(d) {
        stopAllAuto();
        state.blocks = [];
        els.traceRows.innerHTML = '';
        els.traceWarning.hidden = true;
        els.traceSection.hidden = false;

        if (!d.trace) {
            els.traceNotice.hidden = false;
            els.traceNotice.textContent = 'The step-by-step trace is shown for texts up to ' + fmt(TRACE_LIMIT)
                + ' characters (this one has ' + fmt(d.textLength) + ').';
            els.traceCounter.hidden = true;
            return;
        }
        els.traceNotice.hidden = true;
        els.traceCounter.hidden = false;

        const x = d.pattern, m = d.patternLength, n = d.textLength;
        const width = Math.max(n, m);

        // single source of truth check: the trace must report exactly the returned positions
        const tracePos = d.trace.filter((t) => t.outcome === 'MATCH').map((t) => t.j);
        if (tracePos.join(',') !== d.positions.join(',')) {
            els.traceWarning.hidden = false;
            els.traceWarning.textContent = 'Warning: the trace does not match the reported positions.';
        }

        // preprocessing row
        const pre = row(m === 1 ? 'x[0] = "' + esc(x[0]) + '"'
            : 'x[0] = "' + esc(x[0]) + '"<br>x[1] = "' + esc(x[1]) + '"', 'trow--pre');
        const preHl = m === 1 ? {} : (x[0] === x[1] ? {0: 'eq', 1: 'eq'} : {0: 'ne', 1: 'ne'});
        let preCells = '';
        for (let i = 0; i < m; i++) {
            const kind = preHl[i];
            const cls = kind === 'eq' ? ' cell--eq' : kind ? (i === 0 ? ' cell--ne cell--txt' : ' cell--ne cell--pat') : '';
            preCells += '<span class="cell' + cls + '">' + esc(x[i] === ' ' ? '·' : x[i]) + '</span>';
        }
        pre.querySelector('.trow__scroll').innerHTML = '<div class="cells">' + preCells + '</div>';
        pre.querySelector('.trow__right').innerHTML = m === 1 ? ''
            : '<div class="code-line"><span class="c">k = ' + d.k + '</span></div><div class="code-line"><span class="c">ell = ' + d.ell + '</span></div>';
        els.traceRows.appendChild(pre);

        // one block per attempt j
        d.trace.forEach((t) => {
            const r = row('j = ' + t.j);
            const block = {j: t.j, width, steps: buildSteps(d, t), center: r.querySelector('.trow__scroll'), right: r.querySelector('.trow__right')};
            block.at = 0;                          // opens at its first step
            if (block.steps.length > 1) {
                const ctl = document.createElement('div');
                ctl.className = 'stepper';
                ctl.innerHTML = '<button type="button" class="btn btn--small" aria-label="Previous step for j = ' + t.j + '">‹</button>' +
                    '<span class="stepper__count"></span>' +
                    '<button type="button" class="btn btn--small" aria-label="Next step for j = ' + t.j + '">›</button>' +
                    '<button type="button" class="btn btn--small stepper__auto" aria-label="Play the steps for j = ' + t.j + ' automatically" aria-pressed="false">Auto</button>';
                r.querySelector('.trow__center').appendChild(ctl);
                const [prev, , next, auto] = ctl.children;
                block.prev = prev; block.next = next; block.auto = auto; block.counter = ctl.querySelector('.stepper__count');
                prev.addEventListener('click', () => { stopAuto(block); go(block, -1); });
                next.addEventListener('click', () => { stopAuto(block); go(block, 1); });
                auto.addEventListener('click', () => toggleAuto(block));
                r.tabIndex = 0;
                r.setAttribute('aria-label', 'Attempt j = ' + t.j + '. Use the left and right arrow keys to step.');
                r.addEventListener('keydown', (e) => {
                    if (e.key === 'ArrowLeft') { e.preventDefault(); stopAuto(block); go(block, -1); }
                    if (e.key === 'ArrowRight') { e.preventDefault(); stopAuto(block); go(block, 1); }
                });
            }
            state.blocks.push(block);
            els.traceRows.appendChild(r);
            renderBlock(block);
        });

        // termination block
        const last = row('j = ' + d.finalJ, 'trow--end');
        last.querySelector('.trow__center').remove();
        last.querySelector('.trow__right').innerHTML =
            '<div class="code-line"><span class="c">j &gt; n - m</span> → <span class="c">' + d.finalJ + ' &gt; ' + (n - m) + '</span></div>' +
            '<div class="code-line"><span class="c">Matches: ' + fmt(d.matchCount) + '</span></div>' +
            '<div class="code-line end-msg">The pattern no longer fits in the text, so the algorithm ends.</div>';
        els.traceRows.appendChild(last);
        updateTraceCounter();
    }

    // ---------- PDF download (client-side, no Java/Spring endpoint) ----------
    els.pdfBtn.addEventListener('click', () => {
        if (els.pdfBtn.disabled || !state.last) return;
        const d = state.last;
        const text = state.fullText || d.text || '';
        const positions = state.fullPositions || d.positions || [];
        const JsPDF = window.jspdf && window.jspdf.jsPDF;
        if (!JsPDF) {
            els.pdfStatus.textContent = 'The PDF library could not be loaded. Check your internet connection and reload the page.';
            return;
        }

        try {
            const doc = new JsPDF({orientation: 'portrait', unit: 'mm', format: 'a4'});
            const pageWidth = doc.internal.pageSize.getWidth();
            const pageHeight = doc.internal.pageSize.getHeight();
            const margin = 16;
            const usableWidth = pageWidth - margin * 2;
            let y = 18;

            const addPageIfNeeded = (height) => {
                if (y + height > pageHeight - 16) {
                    doc.addPage();
                    y = 18;
                }
            };
            const addWrapped = (value, options = {}) => {
                const fontSize = options.fontSize || 9;
                doc.setFont('helvetica', options.bold ? 'bold' : 'normal');
                doc.setFontSize(fontSize);
                const lines = doc.splitTextToSize(String(value), usableWidth);
                const lineHeight = options.lineHeight || fontSize * 0.48;
                lines.forEach((line) => {
                    addPageIfNeeded(lineHeight + 1);
                    doc.text(line, margin, y);
                    y += lineHeight;
                });
                y += options.after === undefined ? 2 : options.after;
            };

            doc.setProperties({
                title: 'Not So Naive - Search report',
                subject: 'Not So Naive string matching simulation',
                author: 'Juan Diego Rojas Rey',
                creator: 'Not So Naive Simulator'
            });
            doc.setFont('helvetica', 'bold');
            doc.setFontSize(18);
            doc.text('Not So Naive - Search report', margin, y);
            y += 9;
            doc.setDrawColor(168, 18, 26);
            doc.setLineWidth(0.8);
            doc.line(margin, y, pageWidth - margin, y);
            y += 8;

            addWrapped('File: ' + (d.fileName || 'typed-text.txt'), {bold: true, fontSize: 10});
            addWrapped('Pattern: "' + d.pattern + '"    |    Pattern length: ' + d.patternLength);
            addWrapped('Occurrences: ' + d.matchCount + '    |    Comparisons: ' + d.comparisons + '    |    Attempts: ' + d.attempts);
            addWrapped('Shift values: k = ' + d.k + ', ell = ' + d.ell + '    |    Execution time: ' + Number(d.elapsedMillis || 0).toFixed(3) + ' ms');
            addWrapped('Text length: ' + d.textLength + ' characters    |    Lines: ' + (d.lineCount || text.split('\n').length));
            y += 2;
            addWrapped('Match positions (zero-based):', {bold: true, fontSize: 11});
            if (positions.length) {
                const posText = positions.join(', ') + (positions.length < d.matchCount ? ' ...' : '');
                addWrapped(posText, {fontSize: 8});
            } else {
                addWrapped('No matches found.', {fontSize: 9});
            }
            y += 3;
            addWrapped('Input text', {bold: true, fontSize: 12});
            // Keep the complete text in the PDF. jsPDF wraps it across pages.
            addWrapped(text, {fontSize: 8, lineHeight: 4});

            // Add page numbers to every page.
            const pageCount = doc.getNumberOfPages();
            for (let page = 1; page <= pageCount; page++) {
                doc.setPage(page);
                doc.setFont('helvetica', 'normal');
                doc.setFontSize(8);
                doc.setTextColor(110);
                doc.text('Not So Naive | Page ' + page + ' of ' + pageCount, pageWidth - margin, pageHeight - 8, {align: 'right'});
            }

            const baseName = (d.fileName || 'typed-text').replace(/\.txt$/i, '').replace(/[^A-Za-z0-9._-]/g, '_') || 'search';
            doc.save('not-so-naive-' + baseName + '-report.pdf');
            els.pdfStatus.textContent = 'PDF downloaded successfully.';
            setStatus('done', 'PDF downloaded');
        } catch (err) {
            console.error('PDF generation failed:', err);
            els.pdfStatus.textContent = 'Could not generate the PDF: ' + (err.message || String(err));
        }
    });

    renderPattern();
})();
