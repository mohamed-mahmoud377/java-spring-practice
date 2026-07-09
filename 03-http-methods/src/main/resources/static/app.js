/*
 * Front-end for the 03 student/course API.
 *
 * It is plain HTML + vanilla JavaScript on purpose — no frameworks, no build step — so you
 * can read exactly how a browser talks to the Spring API. It is served BY Spring from
 * src/main/resources/static/, so it lives at the same origin (http://localhost:8080) as the
 * API. Same origin = no CORS headaches.
 *
 * The one thing to notice everywhere below: every call sends the X-Username / X-Password
 * headers. That is the exact same authentication the console app does with a login prompt.
 */

// ---------------------------------------------------------------------------
// Where the API lives.
//
// If this page was served BY Spring (http://localhost:8080/), the API is at the
// same origin, so we use relative paths ('' + '/api/login').
// If you opened the page some other way — e.g. IntelliJ's built-in preview server
// on a port like 63343 — we point straight at the Spring server instead. The
// backend allows this cross-origin call via its CORS config (see WebConfig.java).
// ---------------------------------------------------------------------------
const API_BASE = (location.port === '8080') ? '' : 'http://localhost:8080';

// ---------------------------------------------------------------------------
// A tiny bit of state: who is logged in. We keep the credentials in memory and
// attach them to every request. (Real apps use a token; this mirrors the API's
// header-based scheme so the lesson stays simple.)
// ---------------------------------------------------------------------------
let auth = { username: null, password: null, role: null };
let myCourseIds = new Set(); // for the student view: which courses I'm already in

// ---------------------------------------------------------------------------
// The single function that talks to the API. Adds the auth headers, sends/reads
// JSON, and turns an error response into a thrown Error carrying the API message.
// ---------------------------------------------------------------------------
async function api(method, path, body) {
    const headers = {
        'X-Username': auth.username,
        'X-Password': auth.password,
    };
    const options = { method, headers };
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json'; // we are SENDING json
        headers['Accept'] = 'application/json';       // we WANT json back
        options.body = JSON.stringify(body);
    }

    const res = await fetch(API_BASE + path, options);

    if (res.status === 204) return null; // No Content (e.g. DELETE)

    const text = await res.text();
    const data = text ? JSON.parse(text) : null;

    if (!res.ok) {
        // Spring's error body looks like { status, error, message, ... }
        const message = (data && (data.message || data.error)) || res.statusText;
        throw new Error(message + ' (HTTP ' + res.status + ')');
    }
    return data;
}

// ---------------------------------------------------------------------------
// Little DOM / UI helpers
// ---------------------------------------------------------------------------
const $ = (id) => document.getElementById(id);

function show(id) { $(id).classList.remove('hidden'); }
function hide(id) { $(id).classList.add('hidden'); }

function banner(message, kind) {
    const b = $('banner');
    b.textContent = message;
    b.className = 'banner ' + kind;
}
const showOk = (m) => banner(m, 'ok');
const showErr = (m) => banner(m, 'err');

function statusPill(active) {
    const span = document.createElement('span');
    span.className = 'pill ' + (active ? 'active' : 'inactive');
    span.textContent = active ? 'active' : 'inactive';
    return span;
}

/** Builds a <tr> from an array of cell contents (strings or DOM nodes). */
function row(cells) {
    const tr = document.createElement('tr');
    for (const cell of cells) {
        const td = document.createElement('td');
        if (cell instanceof Node) td.appendChild(cell);
        else td.textContent = cell;
        tr.appendChild(td);
    }
    return tr;
}

function button(label, className, onClick) {
    const b = document.createElement('button');
    b.textContent = label;
    b.className = className;
    b.addEventListener('click', onClick);
    return b;
}

// ---------------------------------------------------------------------------
// LOGIN / LOGOUT
// ---------------------------------------------------------------------------
$('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    // Tentatively set the credentials, then verify them with GET /api/login.
    auth = {
        username: $('login-username').value.trim(),
        password: $('login-password').value,
        role: null,
    };
    try {
        const me = await api('GET', '/api/login'); // returns { username, role, ... }
        auth.role = me.role;
        showOk('Logged in as ' + me.username + ' (' + me.role + ')');
        enterApp();
    } catch (err) {
        auth = { username: null, password: null, role: null };
        showErr('Login failed: ' + err.message);
    }
});

$('logout-btn').addEventListener('click', () => {
    auth = { username: null, password: null, role: null };
    myCourseIds = new Set();
    $('who').classList.add('hidden');
    hide('admin-view');
    hide('student-view');
    show('login-view');
    banner('', '');
    $('banner').classList.add('hidden');
    $('login-form').reset();
});

/** Show the right view for the logged-in role and load its data. */
function enterApp() {
    hide('login-view');
    $('who-text').textContent = auth.username + ' · ' + auth.role;
    $('who').classList.remove('hidden');

    if (auth.role === 'ADMIN') {
        show('admin-view');
        hide('student-view');
        adminLoadCourses();
        adminLoadUsers();
    } else {
        show('student-view');
        hide('admin-view');
        studentRefresh();
    }
}

// ===========================================================================
// ADMIN
// ===========================================================================

// Create course — POST /api/courses
$('create-course-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const course = await api('POST', '/api/courses', {
            name: $('new-course-name').value.trim(),
            active: $('new-course-active').checked,
        });
        showOk('Created course #' + course.id + ' “' + course.name + '”');
        $('create-course-form').reset();
        $('new-course-active').checked = true;
        adminLoadCourses();
    } catch (err) {
        showErr(err.message);
    }
});

// List courses — GET /api/courses?active=…
async function adminLoadCourses() {
    const filter = $('admin-course-filter').value; // '', 'true', 'false'
    const query = filter === '' ? '' : '?active=' + filter;
    try {
        const courses = await api('GET', '/api/courses' + query);
        const tbody = $('admin-courses-table').querySelector('tbody');
        tbody.innerHTML = '';
        if (courses.length === 0) {
            tbody.appendChild(row([emptyCell('no courses')]));
        }
        for (const c of courses) {
            const actions = document.createElement('div');
            actions.className = 'row';
            actions.style.marginBottom = '0';
            // Toggle active/inactive — PUT /api/courses/{id}
            actions.appendChild(button(c.active ? 'deactivate' : 'activate', 'small',
                () => adminSetActive(c.id, !c.active)));
            // Rename — PUT /api/courses/{id}
            actions.appendChild(button('rename', 'small link', () => adminRename(c)));
            // Delete — DELETE /api/courses/{id}
            actions.appendChild(button('delete', 'small danger', () => adminDelete(c)));
            tbody.appendChild(row([c.id, c.name, statusPill(c.active), actions]));
        }
    } catch (err) {
        showErr(err.message);
    }
}

async function adminSetActive(id, active) {
    try {
        await api('PUT', '/api/courses/' + id, { active });
        showOk('Course ' + id + ' is now ' + (active ? 'active' : 'inactive'));
        adminLoadCourses();
    } catch (err) { showErr(err.message); }
}

async function adminRename(course) {
    const name = prompt('New name for course #' + course.id, course.name);
    if (name === null || name.trim() === '') return;
    try {
        await api('PUT', '/api/courses/' + course.id, { name: name.trim() });
        showOk('Renamed course ' + course.id);
        adminLoadCourses();
    } catch (err) { showErr(err.message); }
}

async function adminDelete(course) {
    if (!confirm('Delete course “' + course.name + '”? This also un-enrolls all students.')) return;
    try {
        await api('DELETE', '/api/courses/' + course.id);
        showOk('Deleted course ' + course.id);
        adminLoadCourses();
        adminLoadUsers();
    } catch (err) { showErr(err.message); }
}

// Create student — POST /api/users
$('create-student-form').addEventListener('submit', async (e) => {
    e.preventDefault();
    try {
        const u = await api('POST', '/api/users', {
            username: $('new-student-username').value.trim(),
            password: $('new-student-password').value,
        });
        showOk('Created student “' + u.username + '”');
        $('create-student-form').reset();
        adminLoadUsers();
    } catch (err) { showErr(err.message); }
});

// List users — GET /api/users
async function adminLoadUsers() {
    try {
        const users = await api('GET', '/api/users');
        const tbody = $('admin-users-table').querySelector('tbody');
        tbody.innerHTML = '';
        for (const u of users) {
            const enrolled = u.enrolledCourseIds && u.enrolledCourseIds.length
                ? u.enrolledCourseIds.join(', ') : '—';
            tbody.appendChild(row([u.username, u.role, enrolled]));
        }
    } catch (err) { showErr(err.message); }
}

$('admin-refresh-courses').addEventListener('click', adminLoadCourses);
$('admin-course-filter').addEventListener('change', adminLoadCourses);
$('admin-refresh-users').addEventListener('click', adminLoadUsers);

// ===========================================================================
// STUDENT
// ===========================================================================

function studentRefresh() {
    studentLoadMine().then(studentLoadCourses);
}

// My courses — GET /api/me/courses
async function studentLoadMine() {
    try {
        const mine = await api('GET', '/api/me/courses');
        myCourseIds = new Set(mine.map((c) => c.id));
        const tbody = $('student-mine-table').querySelector('tbody');
        tbody.innerHTML = '';
        if (mine.length === 0) {
            tbody.appendChild(row([emptyCell('you are not enrolled in anything yet')]));
        }
        for (const c of mine) {
            const drop = button('drop', 'small danger', () => studentDrop(c));
            tbody.appendChild(row([c.id, c.name, statusPill(c.active), drop]));
        }
    } catch (err) { showErr(err.message); }
}

// Browse courses — GET /api/courses?active=…
async function studentLoadCourses() {
    const filter = $('student-course-filter').value;
    const query = filter === '' ? '' : '?active=' + filter;
    try {
        const courses = await api('GET', '/api/courses' + query);
        const tbody = $('student-courses-table').querySelector('tbody');
        tbody.innerHTML = '';
        if (courses.length === 0) {
            tbody.appendChild(row([emptyCell('no courses')]));
        }
        for (const c of courses) {
            let action;
            if (myCourseIds.has(c.id)) {
                action = document.createElement('span');
                action.className = 'muted';
                action.textContent = 'enrolled';
            } else if (!c.active) {
                action = document.createElement('span');
                action.className = 'muted';
                action.textContent = 'closed';
            } else {
                action = button('enroll', 'small', () => studentEnroll(c));
            }
            tbody.appendChild(row([c.id, c.name, statusPill(c.active), action]));
        }
    } catch (err) { showErr(err.message); }
}

async function studentEnroll(course) {
    try {
        await api('POST', '/api/courses/' + course.id + '/enroll');
        showOk('Enrolled in “' + course.name + '”');
        studentRefresh();
    } catch (err) { showErr(err.message); }
}

async function studentDrop(course) {
    try {
        await api('DELETE', '/api/courses/' + course.id + '/enroll');
        showOk('Dropped “' + course.name + '”');
        studentRefresh();
    } catch (err) { showErr(err.message); }
}

$('student-refresh-mine').addEventListener('click', studentLoadMine);
$('student-refresh-courses').addEventListener('click', studentLoadCourses);
$('student-course-filter').addEventListener('change', studentLoadCourses);

// ---------------------------------------------------------------------------
// misc
// ---------------------------------------------------------------------------
function emptyCell(text) {
    const span = document.createElement('span');
    span.className = 'muted';
    span.textContent = text;
    return span;
}
