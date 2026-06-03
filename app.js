const API_BASE = '';
let currentUser = null;
let currentActivePanel = null;
let charts = {};
let quizTimerInterval = null;
document.addEventListener('DOMContentLoaded', () => {
    checkSession();
    initAuthListeners();
    initDashboardListeners();
});
function checkSession() {
    const userStr = localStorage.getItem('currentUser');
    if (userStr) {
        currentUser = JSON.parse(userStr);
        showDashboard();
    } else {
        showAuth();
    }
}
function showAuth() {
    document.getElementById('dashboard-container').classList.add('d-none');
    document.getElementById('auth-container').classList.remove('d-none');
    document.getElementById('login-card').classList.remove('d-none');
    document.getElementById('register-card').classList.add('d-none');
    document.getElementById('login-form').reset();
    document.getElementById('register-form').reset();
}
function showDashboard() {
    document.getElementById('auth-container').classList.add('d-none');
    document.getElementById('dashboard-container').classList.remove('d-none');
    document.getElementById('user-display-name').textContent = currentUser.full_name;
    document.getElementById('user-display-role').textContent = currentUser.role;

    const subText = currentUser.role === 'student'
        ? `${currentUser.roll_number} • ${currentUser.department}`
        : currentUser.email;
    document.getElementById('user-display-sub').textContent = subText;
    renderSidebarMenu();

    const defaultPanel = currentUser.role === 'student' ? 'student-dashboard' : 'admin-dashboard';
    navigateTo(defaultPanel);
}
function renderSidebarMenu() {
    const menu = document.getElementById('dashboard-menu');
    menu.innerHTML = '';

    const studentItems = [
        { id: 'student-dashboard', label: 'Dashboard', icon: 'fa-chart-line' },
        { id: 'student-tasks', label: 'Daily Tasks', icon: 'fa-list-check' },
        { id: 'student-tests', label: 'Mock Tests', icon: 'fa-file-invoice' },
        { id: 'student-scores', label: 'Score Tracking', icon: 'fa-trophy' },
        { id: 'student-feedback', label: 'Interview Feedback', icon: 'fa-comments' }
    ];

    const adminItems = [
        { id: 'admin-dashboard', label: 'Dashboard', icon: 'fa-chart-line' },
        { id: 'admin-tasks', label: 'Manage Tasks', icon: 'fa-plus-circle' },
        { id: 'admin-tests', label: 'Manage Tests', icon: 'fa-file-medical' },
        { id: 'admin-scores', label: 'Score Tracking', icon: 'fa-star' },
        { id: 'admin-feedback', label: 'Interview Feedback', icon: 'fa-user-check' }
    ];

    const items = currentUser.role === 'student' ? studentItems : adminItems;

    items.forEach(item => {
        const li = document.createElement('li');
        li.className = 'nav-item';
        li.innerHTML = `
            <a href="#" class="nav-link" id="nav-${item.id}" onclick="navigateTo('${item.id}')">
                <i class="fa-solid ${item.icon}"></i>
                <span>${item.label}</span>
            </a>
        `;
        menu.appendChild(li);
    });
}
function showToast(message, type = 'success') {
    const toastEl = document.getElementById('app-toast');
    const toastBody = document.getElementById('toast-body-text');

    toastBody.textContent = message;
    toastEl.classList.remove('bg-success', 'bg-danger', 'toast-success', 'toast-error');

    if (type === 'success') {
        toastEl.classList.add('bg-success', 'toast-success');
    } else {
        toastEl.classList.add('bg-danger', 'toast-error');
    }

    const toast = new bootstrap.Toast(toastEl, { delay: 4000 });
    toast.show();
}
function initAuthListeners() {
    // Toggle login/register links
    document.getElementById('show-register-link').addEventListener('click', (e) => {
        e.preventDefault();
        document.getElementById('login-card').classList.add('d-none');
        document.getElementById('register-card').classList.remove('d-none');
    });

    document.getElementById('show-login-link').addEventListener('click', (e) => {
        e.preventDefault();
        document.getElementById('register-card').classList.add('d-none');
        document.getElementById('login-card').classList.remove('d-none');
    });
    document.getElementById('login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const usernameInput = document.getElementById('login-username').value.trim();
        const passwordInput = document.getElementById('login-password').value;

        try {
            const resp = await fetch(`${API_BASE}/api/auth/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username: usernameInput, password: passwordInput })
            });

            const data = await resp.json();
            if (resp.ok && data.status === 'success') {
                currentUser = data.user;
                localStorage.setItem('currentUser', JSON.stringify(currentUser));
                showToast(`Welcome back, ${currentUser.full_name}!`);
                showDashboard();
            } else {
                showToast(data.error || 'Authentication failed', 'error');
            }
        } catch (err) {
            console.error(err);
            showToast('Network error, please try again.', 'error');
        }
    });

    // Handle Register Form Submit
    document.getElementById('register-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const username = document.getElementById('reg-username').value.trim();
        const password = document.getElementById('reg-password').value;
        const fullName = document.getElementById('reg-fullname').value.trim();
        const email = document.getElementById('reg-email').value.trim();
        const roll = document.getElementById('reg-roll').value.trim();
        const dept = document.getElementById('reg-dept').value;

        if (password.length < 6) {
            showToast('Password must be at least 6 characters long', 'error');
            return;
        }

        try {
            const resp = await fetch(`${API_BASE}/api/auth/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    username, password, email, role: 'student',
                    full_name: fullName, roll_number: roll, department: dept
                })
            });

            const data = await resp.json();
            if (resp.ok) {
                showToast('Registration successful! Please sign in.');
                showAuth();
            } else {
                showToast(data.error || 'Registration failed', 'error');
            }
        } catch (err) {
            console.error(err);
            showToast('Network error, please try again.', 'error');
        }
    });
    const demoStudentBtn = document.getElementById('demo-student-btn');
    if (demoStudentBtn) {
        demoStudentBtn.addEventListener('click', () => {
            document.getElementById('login-username').value = 'student1';
            document.getElementById('login-password').value = 'student123';
            document.getElementById('login-submit-btn').click();
        });
    }

    const demoAdminBtn = document.getElementById('demo-admin-btn');
    if (demoAdminBtn) {
        demoAdminBtn.addEventListener('click', () => {
            document.getElementById('login-username').value = 'admin';
            document.getElementById('login-password').value = 'admin123';
            document.getElementById('login-submit-btn').click();
        });
    }
}

function initDashboardListeners() {
    document.getElementById('logout-btn').addEventListener('click', () => {
        localStorage.removeItem('currentUser');
        currentUser = null;
        if (quizTimerInterval) clearInterval(quizTimerInterval);
        showToast('Logged out successfully');
        showAuth();
    });
    document.getElementById('submit-task-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const taskId = document.getElementById('submit-task-id').value;
        const link = document.getElementById('submission-link').value.trim();

        try {
            const resp = await fetch(`${API_BASE}/api/tasks/submit`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    student_id: currentUser.id,
                    task_id: parseInt(taskId),
                    submission_link: link
                })
            });

            if (resp.ok) {
                showToast('Task submitted successfully!');
                const modalEl = document.getElementById('submitTaskModal');
                const modal = bootstrap.Modal.getInstance(modalEl);
                modal.hide();
                loadStudentTasks(); // Reload tasks table
            } else {
                const data = await resp.json();
                showToast(data.error || 'Failed to submit task', 'error');
            }
        } catch (err) {
            console.error(err);
            showToast('Connection error', 'error');
        }
    });

    const createTaskForm = document.getElementById('create-task-form');
    if (createTaskForm) {
        createTaskForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const title = document.getElementById('task-title').value.trim();
            const description = document.getElementById('task-desc').value.trim();
            const due_date = document.getElementById('task-duedate').value;

            try {
                const resp = await fetch(`${API_BASE}/api/tasks`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ title, description, due_date })
                });

                if (resp.ok) {
                    showToast('Daily task assigned successfully!');
                    createTaskForm.reset();
                    loadAdminTasks();
                } else {
                    const data = await resp.json();
                    showToast(data.error || 'Failed to assign task', 'error');
                }
            } catch (err) {
                console.error(err);
                showToast('Connection error', 'error');
            }
        });
    }
    const addQBtn = document.getElementById('add-question-builder-btn');
    if (addQBtn) {
        addQBtn.addEventListener('click', () => {
            addQuestionBuilderCard();
        });
    }
    const createTestForm = document.getElementById('create-test-form');
    if (createTestForm) {
        createTestForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const title = document.getElementById('test-title').value.trim();
            const description = document.getElementById('test-desc').value.trim();
            const duration = parseInt(document.getElementById('test-duration').value);
            const qCards = document.querySelectorAll('.question-builder-card');
            if (qCards.length === 0) {
                showToast('Please add at least one question option', 'error');
                return;
            }

            const questions = [];
            for (let card of qCards) {
                const text = card.querySelector('.q-text').value.trim();
                const a = card.querySelector('.q-opt-a').value.trim();
                const b = card.querySelector('.q-opt-b').value.trim();
                const c = card.querySelector('.q-opt-c').value.trim();
                const d = card.querySelector('.q-opt-d').value.trim();
                const ans = card.querySelector('.q-correct').value;

                if (!text || !a || !b || !c || !d || !ans) {
                    showToast('Please fill out all question fields', 'error');
                    return;
                }

                questions.push({
                    question_text: text,
                    option_a: a,
                    option_b: b,
                    option_c: c,
                    option_d: d,
                    correct_option: ans
                });
            }

            try {
                const resp = await fetch(`${API_BASE}/api/tests`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        title,
                        description,
                        duration_minutes: duration,
                        questions: questions
                    })
                });

                if (resp.ok) {
                    showToast('Mock test published successfully!');
                    createTestForm.reset();
                    document.getElementById('admin-questions-list').innerHTML = '';
                    loadAdminTests();
                } else {
                    const data = await resp.json();
                    showToast(data.error || 'Failed to publish test', 'error');
                }
            } catch (err) {
                console.error(err);
                showToast('Connection error', 'error');
            }
        });
    }
    const createFeedbackForm = document.getElementById('create-feedback-form');
    if (createFeedbackForm) {
        createFeedbackForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const studentId = parseInt(document.getElementById('feedback-student-id').value);
            const interviewer = document.getElementById('feedback-interviewer').value.trim();
            const date = document.getElementById('feedback-date').value;
            const comm = parseInt(document.getElementById('feedback-comm').value);
            const tech = parseInt(document.getElementById('feedback-tech').value);
            const coding = parseInt(document.getElementById('feedback-coding').value);
            const comments = document.getElementById('feedback-comments').value.trim();

            try {
                const resp = await fetch(`${API_BASE}/api/feedback`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        student_id: studentId,
                        interviewer_name: interviewer,
                        interview_date: date,
                        communication_score: comm,
                        technical_score: tech,
                        coding_score: coding,
                        comments: comments
                    })
                });

                if (resp.ok) {
                    showToast('Interview feedback submitted successfully!');
                    createFeedbackForm.reset();
                    loadAdminFeedback();
                } else {
                    const data = await resp.json();
                    showToast(data.error || 'Failed to submit feedback', 'error');
                }
            } catch (err) {
                console.error(err);
                showToast('Connection error', 'error');
            }
        });
    }
    document.getElementById('quiz-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        submitMockTestAttempt();
    });

    function navigateTo(panelId) {
        const menuLinks = document.querySelectorAll('#dashboard-menu .nav-link');
        menuLinks.forEach(link => link.classList.remove('active'))
        const activeLink = document.getElementById(`nav-${panelId}`);
        if (activeLink) activeLink.classList.add('active');
        const panels = document.querySelectorAll('.dashboard-panel');
        panels.forEach(panel => panel.classList.add('d-none'));
        const activePanel = document.getElementById(`panel-${panelId}`);
        if (activePanel) {
            activePanel.classList.remove('d-none');
            currentActivePanel = panelId;
        }
        updatePageHeaders(panelId);
        if (panelId !== 'student-tests' && quizTimerInterval) {
            clearInterval(quizTimerInterval);
            document.getElementById('quiz-attempt-view').classList.add('d-none');
            document.getElementById('tests-list-view').classList.remove('d-none');
        }
        loadPanelData(panelId);
    }
    function updatePageHeaders(panelId) {
        const titles = {
            'student-dashboard': { title: 'Dashboard Overview', sub: 'Placement preparation analytics at a glance.' },
            'student-tasks': { title: 'Placement Preparation Tasks', sub: 'Assigned assignments and daily tasks.' },
            'student-tests': { title: 'Mock Test Hub', sub: 'Practice real exam questions to benchmark skills.' },
            'student-scores': { title: 'Mock Test Metrics', sub: 'Analyze historical performance across mock tests.' },
            'student-feedback': { title: 'Mock Interview Evaluations', sub: 'Recruiter review feedback and rating breakdowns.' },
            'admin-dashboard': { title: 'Admin Overview Panel', sub: 'Placement stats, class averages, and diagnostics.' },
            'admin-tasks': { title: 'Assign Placement Tasks', sub: 'Deploy preparation instructions and track submissions.' },
            'admin-tests': { title: 'Mock Exam Controller', sub: 'Create online mock test sets and configure answers.' },
            'admin-scores': { title: 'Candidate Performance Log', sub: 'Observe historical student scores and rankings.' },
            'admin-feedback': { title: 'Mock Interview Assessor', sub: 'Submit Candidate Technical and Comm Evaluations.' }
        };

        const header = titles[panelId] || { title: 'Placement Training Portal', sub: '' };
        document.getElementById('page-header-title').textContent = header.title;
        document.getElementById('page-header-subtitle').textContent = header.sub;
        const options = { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' };
        document.getElementById('header-date-display').textContent = new Date().toLocaleDateString('en-US', options);
    }
    function loadPanelData(panelId) {
        switch (panelId) {
            case 'student-dashboard':
                loadStudentDashboard();
                break;
            case 'student-tasks':
                loadStudentTasks();
                break;
            case 'student-tests':
                loadStudentTests();
                break;
            case 'student-scores':
                loadStudentScores();
                break;
            case 'student-feedback':
                loadStudentFeedback();
                break;
            case 'admin-dashboard':
                loadAdminDashboard();
                break;
            case 'admin-tasks':
                loadAdminTasks();
                break;
            case 'admin-tests':
                loadAdminTests();
                break;
            case 'admin-scores':
                loadAdminScores();
                break;
            case 'admin-feedback':
                loadAdminFeedback();
                break;
        }
    }
    const PREP_TIPS = [
        "Always explain your brute-force logic before optimizing. It shows strong engineering reasoning!",
        "Communicate your thoughts out loud while writing code. Interviewers want to understand your process, not just see the solution.",
        "Keep your resume clean, focused, and limited to 1 page. Highlight your impact using metrics and the STAR method.",
        "In system design rounds, start with high-level architecture before diving into database details.",
        "When asked if you have any questions, ask about the engineering team's day-to-day challenges or system scale."
    ];

    async function loadStudentDashboard() {
        try {
            const tipEl = document.getElementById('daily-tip-text');
            if (tipEl) {
                const randomTip = PREP_TIPS[Math.floor(Math.random() * PREP_TIPS.length)];
                tipEl.innerHTML = `"${randomTip}"`;
            }
            const tasksResp = await fetch(`${API_BASE}/api/tasks?studentId=${currentUser.id}`);
            const tasks = await tasksResp.json();
            const completedTasks = tasks.filter(t => t.status === 'Completed').length;
            document.getElementById('stat-student-tasks-completed').textContent = `${completedTasks}/${tasks.length}`;
            const pendingContainer = document.getElementById('student-dashboard-tasks');
            pendingContainer.innerHTML = '';
            const pending = tasks.filter(t => t.status === 'Pending').slice(0, 3);
            if (pending.length === 0) {
                pendingContainer.innerHTML = '<p class="text-muted small mt-2">No pending tasks today! Outstanding work.</p>';
            } else {
                pending.forEach(t => {
                    const item = document.createElement('div');
                    item.className = 'list-group-item bg-transparent text-white border-bottom border-light border-opacity-10 py-3 px-0';
                    item.innerHTML = `
                    <div class="d-flex justify-content-between align-items-start">
                        <div>
                            <h6 class="mb-1 fw-semibold">${t.title}</h6>
                            <span class="text-danger small"><i class="fa-regular fa-clock me-1"></i>Due: ${t.due_date}</span>
                        </div>
                        <button class="btn btn-sm btn-primary py-1 px-3" onclick="navigateTo('student-tasks')">Open</button>
                    </div>
                `;
                    pendingContainer.appendChild(item);
                });
            }
            const scoresResp = await fetch(`${API_BASE}/api/scores?studentId=${currentUser.id}`);
            const scores = await scoresResp.json();
            document.getElementById('stat-student-tests-count').textContent = scores.length;
            let totalPct = 0;
            scores.forEach(s => {
                totalPct += (s.score / s.max_score) * 100;
            });
            const avg = scores.length > 0 ? Math.round(totalPct / scores.length) : 0;
            document.getElementById('stat-student-avg-score').textContent = `${avg}%`;
            const feedbackResp = await fetch(`${API_BASE}/api/feedback?studentId=${currentUser.id}`);
            const feedback = await feedbackResp.json();
            document.getElementById('stat-student-feedback-count').textContent = feedback.length;
            drawStudentScoresChart('student-score-chart', scores);
        } catch (err) {
            console.error('Failed to load student dashboard stats:', err);
        }
    }
    async function loadStudentTasks() {
        try {
            const resp = await fetch(`${API_BASE}/api/tasks?studentId=${currentUser.id}`);
            const tasks = await resp.json();

            const body = document.getElementById('student-tasks-table-body');
            body.innerHTML = '';

            if (tasks.length === 0) {
                body.innerHTML = `<tr><td colspan="5" class="text-center text-muted">No placement tasks assigned yet.</td></tr>`;
                return;
            }

            tasks.forEach(t => {
                const isCompleted = t.status === 'Completed';
                const badgeClass = isCompleted ? 'badge-completed' : 'badge-pending';

                const actionBtn = isCompleted
                    ? `<a href="${t.submission_link}" target="_blank" class="btn btn-sm btn-outline-secondary"><i class="fa-solid fa-link me-1"></i>View Link</a>`
                    : `<button class="btn btn-sm btn-primary" onclick="openSubmitTaskModal(${t.id}, '${t.title.replace(/'/g, "\\'")}')">Submit Task</button>`;

                const tr = document.createElement('tr');
                tr.innerHTML = `
                <td class="fw-semibold">${t.title}</td>
                <td class="text-muted small w-50">${t.description}</td>
                <td>${t.due_date}</td>
                <td><span class="badge ${badgeClass}">${t.status}</span></td>
                <td>${actionBtn}</td>
            `;
                body.appendChild(tr);
            });
        } catch (err) {
            console.error('Failed to load student tasks:', err);
        }
    }

    function openSubmitTaskModal(taskId, title) {
        document.getElementById('submit-task-id').value = taskId;
        document.getElementById('submit-task-title').textContent = title;
        document.getElementById('submission-link').value = '';

        const modal = new bootstrap.Modal(document.getElementById('submitTaskModal'));
        modal.show();
    }
    async function loadStudentTests() {
        try {
            const resp = await fetch(`${API_BASE}/api/tests`);
            const tests = await resp.json();

            const container = document.getElementById('tests-list-view');
            container.innerHTML = '';

            if (tests.length === 0) {
                container.innerHTML = `<div class="col-12 text-center text-muted py-5">No mock exams published yet.</div>`;
                return;
            }

            tests.forEach(test => {
                const card = document.createElement('div');
                card.className = 'col-md-6 col-xl-4';
                card.innerHTML = `
                <div class="card stat-card rounded-4 border-0 h-100">
                    <div class="card-body p-4 d-flex flex-column">
                        <h5 class="fw-bold mb-2">${test.title}</h5>
                        <p class="text-muted small flex-grow-1">${test.description}</p>
                        <div class="d-flex justify-content-between align-items-center mt-3">
                            <span class="text-muted small"><i class="fa-regular fa-clock me-1"></i>${test.duration_minutes} Mins</span>
                            <button class="btn btn-outline-primary" onclick="startMockTest(${test.id}, '${test.title.replace(/'/g, "\\'")}', '${test.description.replace(/'/g, "\\'")}', ${test.duration_minutes})">
                                Start Quiz
                            </button>
                        </div>
                    </div>
                </div>
            `;
                container.appendChild(card);
            });
        } catch (err) {
            console.error('Failed to load mock tests list:', err);
        }
        async function startMockTest(testId, title, desc, durationMins) {
            try {
                const resp = await fetch(`${API_BASE}/api/tests/questions?id=${testId}`);
                if (!resp.ok) {
                    showToast('Failed to fetch test questions', 'error');
                    return;
                }
                const questions = await resp.json();
                if (questions.length === 0) {
                    showToast('This test has no questions assigned yet', 'error');
                    return;
                }

                // Hide list view, show quiz active attempt view
                document.getElementById('tests-list-view').classList.add('d-none');
                const attemptView = document.getElementById('quiz-attempt-view');
                attemptView.classList.remove('d-none');

                document.getElementById('active-quiz-title').textContent = title;
                document.getElementById('active-quiz-desc').textContent = desc;

                // Build Quiz Form Questions
                const qContainer = document.getElementById('quiz-questions-container');
                qContainer.innerHTML = '';

                questions.forEach((q, idx) => {
                    const block = document.createElement('div');
                    block.className = 'card quiz-card p-4 rounded-4 border-0 mb-4';
                    block.innerHTML = `
                <h6 class="fw-bold mb-3">${idx + 1}. ${q.question_text}</h6>
                <div class="row g-2">
                    <div class="col-md-6">
                        <button type="button" class="option-btn text-start q-opt-btn" data-qid="${q.id}" data-opt="A">
                            <span class="option-letter">A</span>${q.option_a}
                        </button>
                    </div>
                    <div class="col-md-6">
                        <button type="button" class="option-btn text-start q-opt-btn" data-qid="${q.id}" data-opt="B">
                            <span class="option-letter">B</span>${q.option_b}
                        </button>
                    </div>
                    <div class="col-md-6">
                        <button type="button" class="option-btn text-start q-opt-btn" data-qid="${q.id}" data-opt="C">
                            <span class="option-letter">C</span>${q.option_c}
                        </button>
                    </div>
                    <div class="col-md-6">
                        <button type="button" class="option-btn text-start q-opt-btn" data-qid="${q.id}" data-opt="D">
                            <span class="option-letter">D</span>${q.option_d}
                        </button>
                    </div>
                </div>
            `;
                    qContainer.appendChild(block);
                });

                // Add selection listener to option buttons
                const optionButtons = qContainer.querySelectorAll('.q-opt-btn');
                optionButtons.forEach(btn => {
                    btn.addEventListener('click', () => {
                        const qId = btn.getAttribute('data-qid');
                        // Unselect others for the same question
                        qContainer.querySelectorAll(`.q-opt-btn[data-qid="${qId}"]`).forEach(b => {
                            b.classList.remove('selected');
                        });
                        // Select clicked
                        btn.classList.add('selected');
                    });
                });

                // Set up Timer
                let secondsLeft = durationMins * 60;
                const timerDisplay = document.getElementById('quiz-timer');
                timerDisplay.textContent = formatTime(secondsLeft);

                if (quizTimerInterval) clearInterval(quizTimerInterval);
                quizTimerInterval = setInterval(() => {
                    secondsLeft--;
                    timerDisplay.textContent = formatTime(secondsLeft);
                    if (secondsLeft <= 0) {
                        clearInterval(quizTimerInterval);
                        showToast('Time is up! Auto-submitting...', 'error');
                        submitMockTestAttempt(testId, true);
                    }
                }, 1000);

                // Store test meta on active submission trigger
                document.getElementById('quiz-form').onsubmit = (e) => {
                    e.preventDefault();
                    submitMockTestAttempt(testId);
                };

            } catch (err) {
                console.error(err);
                showToast('Error starting quiz', 'error');
            }
        }

        function formatTime(sec) {
            const mins = Math.floor(sec / 60);
            const secs = sec % 60;
            return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
        }

        // Submit Mock Test Answers
        async function submitMockTestAttempt(testId, isAutoSubmit = false) {
            if (quizTimerInterval) clearInterval(quizTimerInterval);

            // Compile student answers
            const answers = {};
            const selectedOptions = document.querySelectorAll('.q-opt-btn.selected');
            selectedOptions.forEach(opt => {
                const qId = opt.getAttribute('data-qid');
                const val = opt.getAttribute('data-opt');
                answers[qId] = val;
            });

            try {
                const resp = await fetch(`${API_BASE}/api/tests/submit`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        student_id: currentUser.id,
                        test_id: testId,
                        answers: answers
                    })
                });

                const data = await resp.json();
                if (resp.ok) {
                    showToast(`Quiz completed! You scored ${data.score} out of ${data.max_score}`);
                    // Back to test list
                    document.getElementById('quiz-attempt-view').classList.add('d-none');
                    document.getElementById('tests-list-view').classList.remove('d-none');
                    navigateTo('student-scores'); // Navigate to results
                } else {
                    showToast(data.error || 'Failed to submit quiz', 'error');
                }
            } catch (err) {
                console.error(err);
                showToast('Connection error during quiz submission', 'error');
            }
        }

        // 4. Student Score Tracking Log and Chart
        async function loadStudentScores() {
            try {
                const resp = await fetch(`${API_BASE}/api/scores?studentId=${currentUser.id}`);
                const scores = await resp.json();

                const body = document.getElementById('student-scores-table-body');
                body.innerHTML = '';

                if (scores.length === 0) {
                    body.innerHTML = `<tr><td colspan="4" class="text-center text-muted">You haven't completed any mock tests.</td></tr>`;
                    return;
                }

                scores.forEach(s => {
                    const pct = Math.round((s.score / s.max_score) * 100);
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                <td class="fw-semibold">${s.test_title}</td>
                <td><span class="text-primary fw-semibold">${s.score}</span> / ${s.max_score}</td>
                <td>${pct}%</td>
                <td class="text-muted small">${new Date(s.taken_at).toLocaleString()}</td>
            `;
                    body.appendChild(tr);
                });

                drawStudentScoresChart('student-score-history-chart', scores);
            } catch (err) {
                console.error(err);
            }
        }

        function drawStudentScoresChart(canvasId, scores) {
            const canvas = document.getElementById(canvasId);
            if (!canvas) return;

            // Destroy existing chart if it exists
            if (charts[canvasId]) {
                charts[canvasId].destroy();
            }

            const labels = scores.map(s => s.test_title);
            const dataPts = scores.map(s => Math.round((s.score / s.max_score) * 100));

            const ctx = canvas.getContext('2d');
            charts[canvasId] = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Score Percentage (%)',
                        data: dataPts,
                        borderColor: '#6366f1',
                        backgroundColor: 'rgba(99, 102, 241, 0.15)',
                        borderWidth: 3,
                        pointBackgroundColor: '#06b6d4',
                        pointBorderColor: '#ffffff',
                        pointHoverRadius: 7,
                        fill: true,
                        tension: 0.3
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            min: 0,
                            max: 100,
                            grid: { color: 'rgba(255, 255, 255, 0.05)' },
                            ticks: { color: '#9ca3af' }
                        },
                        x: {
                            grid: { display: false },
                            ticks: { color: '#9ca3af' }
                        }
                    },
                    plugins: {
                        legend: { display: false }
                    }
                }
            });
        }

        // 5. Student Interview Feedback
        async function loadStudentFeedback() {
            try {
                const resp = await fetch(`${API_BASE}/api/feedback?studentId=${currentUser.id}`);
                const feedbacks = await resp.json();

                const container = document.getElementById('student-feedback-container');
                container.innerHTML = '';

                if (feedbacks.length === 0) {
                    container.innerHTML = `<div class="col-12 text-center text-muted py-5">No mock interview feedback recorded yet.</div>`;
                    return;
                }

                feedbacks.forEach(f => {
                    const totalScore = f.communication_score + f.technical_score + f.coding_score;
                    const avg = (totalScore / 3).toFixed(1);

                    const card = document.createElement('div');
                    card.className = 'col-md-12';
                    card.innerHTML = `
                <div class="card feedback-card p-4 rounded-4 border-0 mb-3">
                    <div class="card-body p-0">
                        <div class="d-flex flex-column flex-md-row justify-content-between align-items-md-center mb-4 gap-3">
                            <div>
                                <h5 class="fw-bold mb-1"><i class="fa-solid fa-user-tie me-2 text-primary"></i>Interview with ${f.interviewer_name}</h5>
                                <span class="text-muted small"><i class="fa-regular fa-calendar me-1"></i>Date: ${f.interview_date}</span>
                            </div>
                            <div class="d-flex align-items-center gap-3">
                                <div class="text-end">
                                    <span class="text-muted small d-block">Overall Score</span>
                                    <span class="fw-bold text-success">${avg} / 10</span>
                                </div>
                                <div class="feedback-score-circle">
                                    <span class="fw-bold fs-5">${Math.round(avg)}</span>
                                </div>
                            </div>
                        </div>

                        <div class="row g-3 mb-4">
                            <div class="col-4 col-md-3">
                                <div class="bg-dark bg-opacity-20 p-3 rounded-3 text-center border border-light border-opacity-5">
                                    <span class="text-muted small d-block">Communication</span>
                                    <h5 class="fw-bold mb-0 text-primary">${f.communication_score} / 10</h5>
                                </div>
                            </div>
                            <div class="col-4 col-md-3">
                                <div class="bg-dark bg-opacity-20 p-3 rounded-3 text-center border border-light border-opacity-5">
                                    <span class="text-muted small d-block">Technical</span>
                                    <h5 class="fw-bold mb-0 text-secondary">${f.technical_score} / 10</h5>
                                </div>
                            </div>
                            <div class="col-4 col-md-3">
                                <div class="bg-dark bg-opacity-20 p-3 rounded-3 text-center border border-light border-opacity-5">
                                    <span class="text-muted small d-block">Coding Logic</span>
                                    <h5 class="fw-bold mb-0 text-info">${f.coding_score} / 10</h5>
                                </div>
                            </div>
                        </div>

                        <div>
                            <h6 class="fw-semibold mb-2 text-white">Evaluator Comments & Feedback:</h6>
                            <p class="text-muted small mb-0">${f.comments.replace(/\n/g, '<br>')}</p>
                        </div>
                    </div>
                </div>
            `;
                    container.appendChild(card);
                });
            } catch (err) {
                console.error(err);
            }
        }


        // ----------------------------------------------------
        // ADMIN PANELS IMPLEMENTATIONS
        // ----------------------------------------------------

        // 1. Admin Dashboard
        async function loadAdminDashboard() {
            try {
                const resp = await fetch(`${API_BASE}/api/scores`);
                const stats = await resp.json();

                // Stats counters
                document.getElementById('stat-admin-students').textContent = stats.total_students;
                document.getElementById('stat-admin-feedbacks').textContent = stats.recent_scores.length;

                // Fetch task & test count
                const tasksResp = await fetch(`${API_BASE}/api/tasks`);
                const tasks = await tasksResp.json();
                document.getElementById('stat-admin-tasks').textContent = tasks.length;

                const testsResp = await fetch(`${API_BASE}/api/tests`);
                const tests = await testsResp.json();
                document.getElementById('stat-admin-tests').textContent = tests.length;

                // Draw Admin averages chart
                drawAdminScoresChart('admin-performance-chart', stats.test_averages);
            } catch (err) {
                console.error('Failed to load admin dashboard stats:', err);
            }
        }

        function drawAdminScoresChart(canvasId, averages) {
            const canvas = document.getElementById(canvasId);
            if (!canvas) return;

            if (charts[canvasId]) {
                charts[canvasId].destroy();
            }

            const labels = averages.map(t => t.test_title);
            const dataPts = averages.map(t => t.avg_percentage);

            const ctx = canvas.getContext('2d');
            charts[canvasId] = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [{
                        label: 'Class Average (%)',
                        data: dataPts,
                        backgroundColor: 'rgba(99, 102, 241, 0.7)',
                        borderColor: '#6366f1',
                        borderWidth: 2,
                        borderRadius: 8
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            min: 0,
                            max: 100,
                            grid: { color: 'rgba(255, 255, 255, 0.05)' },
                            ticks: { color: '#9ca3af' }
                        },
                        x: {
                            grid: { display: false },
                            ticks: { color: '#9ca3af' }
                        }
                    },
                    plugins: {
                        legend: { display: false }
                    }
                }
            });
        }

        // 2. Admin Manage Tasks
        async function loadAdminTasks() {
            try {
                const resp = await fetch(`${API_BASE}/api/tasks`);
                const tasks = await resp.json();

                const body = document.getElementById('admin-tasks-table-body');
                body.innerHTML = '';

                if (tasks.length === 0) {
                    body.innerHTML = `<tr><td colspan="3" class="text-center text-muted">No daily tasks created yet.</td></tr>`;
                    return;
                }

                tasks.forEach(t => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                <td class="fw-semibold">${t.title}</td>
                <td>${t.due_date}</td>
                <td class="text-muted small">${new Date(t.due_date).toLocaleDateString()}</td>
            `;
                    body.appendChild(tr);
                });
            } catch (err) {
                console.error(err);
            }
        }

        // 3. Admin Manage Tests (Create with dynamic questions option builder)
        async function loadAdminTests() {
            try {
                const resp = await fetch(`${API_BASE}/api/tests`);
                const tests = await resp.json();

                const body = document.getElementById('admin-tests-table-body');
                body.innerHTML = '';

                if (tests.length === 0) {
                    body.innerHTML = `<tr><td colspan="3" class="text-center text-muted">No mock tests created yet.</td></tr>`;
                    return;
                }

                tests.forEach(test => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                <td class="fw-semibold">${test.title}</td>
                <td>${test.duration_minutes} Mins</td>
                <td class="text-muted small">${test.description}</td>
            `;
                    body.appendChild(tr);
                });
            } catch (err) {
                console.error(err);
            }
        }

        // Question card builder
        function addQuestionBuilderCard() {
            const list = document.getElementById('admin-questions-list');
            const idx = list.children.length + 1;

            const card = document.createElement('div');
            card.className = 'card bg-dark bg-opacity-30 border border-secondary border-opacity-20 p-3 rounded-3 mb-3 question-builder-card';
            card.innerHTML = `
        <div class="d-flex justify-content-between align-items-center mb-3">
            <span class="fw-bold small text-primary">Question #${idx}</span>
            <button type="button" class="btn btn-sm btn-outline-danger py-0 px-2" onclick="removeQuestionBuilderCard(this)">
                <i class="fa-solid fa-trash small"></i> Remove
            </button>
        </div>
        <div class="mb-3">
            <input type="text" class="form-control form-control-sm q-text" placeholder="Question Text" required>
        </div>
        <div class="row g-2 mb-3">
            <div class="col-6">
                <input type="text" class="form-control form-control-sm q-opt-a" placeholder="Option A" required>
            </div>
            <div class="col-6">
                <input type="text" class="form-control form-control-sm q-opt-b" placeholder="Option B" required>
            </div>
            <div class="col-6">
                <input type="text" class="form-control form-control-sm q-opt-c" placeholder="Option C" required>
            </div>
            <div class="col-6">
                <input type="text" class="form-control form-control-sm q-opt-d" placeholder="Option D" required>
            </div>
        </div>
        <div>
            <label class="form-label small mb-1">Correct Option</label>
            <select class="form-select form-select-sm q-correct" required>
                <option value="" disabled selected>Select Answer</option>
                <option value="A">A</option>
                <option value="B">B</option>
                <option value="C">C</option>
                <option value="D">D</option>
            </select>
        </div>
    `;
            list.appendChild(card);
        }

        function removeQuestionBuilderCard(btn) {
            const card = btn.closest('.question-builder-card');
            card.remove();
            // Reindex question indices
            const cards = document.querySelectorAll('.question-builder-card');
            cards.forEach((c, idx) => {
                c.querySelector('.text-primary').textContent = `Question #${idx + 1}`;
            });
        }

        // 4. Admin Score Tracking Log
        async function loadAdminScores() {
            try {
                const resp = await fetch(`${API_BASE}/api/scores`);
                const stats = await resp.json();

                const body = document.getElementById('admin-scores-table-body');
                body.innerHTML = '';

                if (stats.recent_scores.length === 0) {
                    body.innerHTML = `<tr><td colspan="6" class="text-center text-muted">No student exam attempts recorded.</td></tr>`;
                    return;
                }

                stats.recent_scores.forEach(row => {
                    const pct = Math.round((row.score / row.max_score) * 100);
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                <td class="fw-semibold">${row.student_name}</td>
                <td>${row.roll_number}</td>
                <td>${row.test_title}</td>
                <td><span class="text-primary fw-semibold">${row.score}</span> / ${row.max_score}</td>
                <td>${pct}%</td>
                <td class="text-muted small">${new Date(row.taken_at).toLocaleString()}</td>
            `;
                    body.appendChild(tr);
                });
            } catch (err) {
                console.error(err);
            }
        }

        // 5. Admin Interview Feedback
        async function loadAdminFeedback() {
            try {
                // Fetch candidates for select dropdown
                const studentsResp = await fetch(`${API_BASE}/api/students`);
                const students = await studentsResp.json();

                const select = document.getElementById('feedback-student-id');
                select.innerHTML = `<option value="" disabled selected>Select a student</option>`;
                students.forEach(st => {
                    const opt = document.createElement('option');
                    opt.value = st.id;
                    opt.textContent = `${st.full_name} (${st.roll_number} - ${st.department})`;
                    select.appendChild(opt);
                });

                // Fetch feedback history list
                const feedbackResp = await fetch(`${API_BASE}/api/feedback`);
                const feedbacks = await feedbackResp.json();

                const container = document.getElementById('admin-feedbacks-list-container');
                container.innerHTML = '';

                if (feedbacks.length === 0) {
                    container.innerHTML = `<p class="text-center text-muted py-5">No interview evaluations log recorded.</p>`;
                    return;
                }

                feedbacks.forEach(f => {
                    const totalScore = f.communication_score + f.technical_score + f.coding_score;
                    const avg = (totalScore / 3).toFixed(1);

                    const card = document.createElement('div');
                    card.className = 'card bg-dark bg-opacity-20 border border-secondary border-opacity-10 p-4 rounded-4 mb-3';
                    card.innerHTML = `
                <div class="d-flex justify-content-between align-items-start mb-3">
                    <div>
                        <h6 class="fw-bold mb-1 text-white">${f.student_name}</h6>
                        <span class="text-muted small">${f.roll_number} • ${f.department}</span>
                    </div>
                    <span class="badge bg-success">${avg} / 10 Avg</span>
                </div>
                <div class="border-top border-light border-opacity-5 pt-3 mt-2">
                    <span class="text-muted small d-block mb-1"><strong>Evaluator:</strong> ${f.interviewer_name} (on ${f.interview_date})</span>
                    <p class="text-muted small mb-0 mt-1"><em>"${f.comments}"</em></p>
                </div>
            `;
                    container.appendChild(card);
                });
            } catch (err) {
                console.error(err);
            }
        }
        window.navigateTo = navigateTo; // Expose to HTML inline onclick
        window.openSubmitTaskModal = openSubmitTaskModal; // Expose to HTML table action
        window.startMockTest = startMockTest; // Expose to HTML quiz list card
        window.removeQuestionBuilderCard = removeQuestionBuilderCard; // Expose to dynamic buttons
