-- Create and switch to placement_portal database
CREATE DATABASE IF NOT EXISTS placement_portal;
USE placement_portal;

-- Drop tables if they exist (in reverse order of dependencies)
DROP TABLE IF EXISTS interview_feedback;
DROP TABLE IF EXISTS scores;
DROP TABLE IF EXISTS questions;
DROP TABLE IF EXISTS mock_tests;
DROP TABLE IF EXISTS student_tasks;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS users;

-- 1. Create users table
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    role VARCHAR(10) NOT NULL, -- 'admin' or 'student'
    full_name VARCHAR(100) NOT NULL,
    roll_number VARCHAR(50) DEFAULT NULL,
    department VARCHAR(100) DEFAULT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Create tasks table
CREATE TABLE tasks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    due_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Create student_tasks table
CREATE TABLE student_tasks (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    task_id INT NOT NULL,
    status VARCHAR(20) DEFAULT 'Pending', -- 'Pending' or 'Completed'
    submitted_at TIMESTAMP NULL DEFAULT NULL,
    submission_link VARCHAR(255) DEFAULT NULL,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    UNIQUE KEY (student_id, task_id)
);

-- 4. Create mock_tests table
CREATE TABLE mock_tests (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 30,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. Create questions table
CREATE TABLE questions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    test_id INT NOT NULL,
    question_text TEXT NOT NULL,
    option_a VARCHAR(255) NOT NULL,
    option_b VARCHAR(255) NOT NULL,
    option_c VARCHAR(255) NOT NULL,
    option_d VARCHAR(255) NOT NULL,
    correct_option CHAR(1) NOT NULL, -- 'A', 'B', 'C', 'D'
    FOREIGN KEY (test_id) REFERENCES mock_tests(id) ON DELETE CASCADE
);

-- 6. Create scores table
CREATE TABLE scores (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    test_id INT NOT NULL,
    score INT NOT NULL,
    max_score INT NOT NULL,
    taken_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (test_id) REFERENCES mock_tests(id) ON DELETE CASCADE
);

-- 7. Create interview_feedback table
CREATE TABLE interview_feedback (
    id INT AUTO_INCREMENT PRIMARY KEY,
    student_id INT NOT NULL,
    interviewer_name VARCHAR(100) NOT NULL,
    interview_date DATE NOT NULL,
    communication_score INT NOT NULL, -- 1 to 10
    technical_score INT NOT NULL, -- 1 to 10
    coding_score INT NOT NULL, -- 1 to 10
    comments TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Insert Sample Users (Passwords are plain text for simplicity)
INSERT INTO users (username, password, email, role, full_name, roll_number, department) VALUES
('admin', 'admin123', 'admin@placement.com', 'admin', 'Admin Instructor', NULL, NULL),
('student1', 'student123', 'alice@student.com', 'student', 'Alice Smith', '2026CSE01', 'Computer Science'),
('student2', 'student223', 'bob@student.com', 'student', 'Bob Jones', '2026ECE05', 'Electronics');

-- Insert Sample Tasks
INSERT INTO tasks (title, description, due_date) VALUES
('LeetCode Array Problems', 'Solve 3 Easy/Medium array problems on LeetCode: Two Sum, Contains Duplicate, and Maximum Subarray. Upload link to your solutions.', DATE_ADD(CURDATE(), INTERVAL 1 DAY)),
('Resume Building', 'Draft your one-page technical resume using the standard Overleaf resume template and upload the PDF file link.', DATE_ADD(CURDATE(), INTERVAL 2 DAY)),
('SQL Joins Practice', 'Complete the Basic Joins practice exercises on HackerRank and submit a screenshot or repo link of your progress.', DATE_ADD(CURDATE(), INTERVAL 3 DAY));

-- Insert Sample Mock Tests
INSERT INTO mock_tests (title, description, duration_minutes) VALUES
('Java Programming Basics', 'Test your knowledge on basic Java syntax, OOP concepts, exceptions, and collections.', 15),
('Aptitude and Reasoning', 'Evaluate your logical reasoning, data sufficiency, and quantitative aptitude.', 20);

-- Insert Questions for Java Programming Basics (test_id = 1)
INSERT INTO questions (test_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES
(1, 'Which of these is NOT a valid access modifier in Java?', 'public', 'private', 'protected', 'internal', 'D'),
(1, 'What is the size of double variable in Java?', '16 bits', '32 bits', '64 bits', '8 bits', 'C'),
(1, 'Which package is imported by default in all Java programs?', 'java.util', 'java.lang', 'java.io', 'java.net', 'B'),
(1, 'Which keyword is used to prevent a method from being overridden?', 'static', 'abstract', 'final', 'volatile', 'C'),
(1, 'What is the superclass of all classes in Java?', 'Object', 'Class', 'String', 'System', 'A');

-- Insert Questions for Aptitude and Reasoning (test_id = 2)
INSERT INTO questions (test_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES
(2, 'Complete the series: 3, 5, 9, 17, 33, ...', '65', '49', '50', '60', 'A'),
(2, 'If A is B''s brother, B is C''s sister, how is C related to A?', 'Brother or Sister', 'Cousin', 'Uncle', 'Aunt', 'A'),
(2, 'A train moves at 80 km/h. How far does it travel in 15 minutes?', '12 km', '15 km', '20 km', '25 km', 'C');

-- Insert Sample Scores (for Alice Smith - student_id = 2)
INSERT INTO scores (student_id, test_id, score, max_score, taken_at) VALUES
(2, 1, 4, 5, DATE_SUB(NOW(), INTERVAL 2 DAY)),
(2, 2, 2, 3, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Insert Sample Scores (for Bob Jones - student_id = 3)
INSERT INTO scores (student_id, test_id, score, max_score, taken_at) VALUES
(3, 1, 3, 5, DATE_SUB(NOW(), INTERVAL 1 DAY));

-- Insert Sample Interview Feedback (for Alice Smith - student_id = 2)
INSERT INTO interview_feedback (student_id, interviewer_name, interview_date, communication_score, technical_score, coding_score, comments) VALUES
(2, 'Interviewer John', DATE_SUB(CURDATE(), INTERVAL 1 DAY), 8, 7, 9, 'Alice showed great programming logic. Her explanations for the dynamic programming problems were clear. Needs to work on system design basics.');
