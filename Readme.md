Implementation Plan - Placement Training Portal
We will build a complete, self-contained Placement Training Portal. The backend will be implemented in Core Java (using the built-in com.sun.net.httpserver.HttpServer and MySQL JDBC), and the frontend will be a responsive web dashboard built with HTML, CSS, JavaScript, and Bootstrap 5.

Goal Description:
The Placement Training Portal helps students prepare for job placements by:
Managing Student Registration & Login.
Assigning and tracking Daily Tasks.
Facilitating Mock Tests (creation by admin, submission by students).
Tracking scores of mock tests.
Providing detailed Mock Interview Feedback.
User Review Required
IMPORTANT

MySQL Database Credentials: We detected a MySQL service MySQL80 running on localhost. We verified that the database user root is accessible using the password Madhu. We will use this password to connect to the database. We will create a database named placement_portal.

NOTE:
No External Web Server Required: We will use com.sun.net.httpserver.HttpServer which is built into standard Java. This allows running the application directly from the command line using java with no external dependencies (like Tomcat) except the downloaded MySQL Connector/J jar.

Open Questions:
There are no major open questions, but we will configure the Java server to run on port 8080 by default.

Proposed Changes:
Database Schema
We will create a database called placement_portal and initialize the following tables:

users: Stores admin and student profiles.
tasks: Stores daily tasks assigned by admins.
student_tasks: Stores task submission status and links for each student.
mock_tests: Stores tests created by admins.
questions: Stores multiple-choice questions for each test.
scores: Stores test results for students.
interview_feedback: Stores mock interview ratings and remarks.
[NEW] 
schema.sql
A SQL script to drop, create, and populate the database and tables with sample data.

Java Backend
We will write a Core Java backend that handles both serving the static frontend files and exposing a RESTful JSON API.

[NEW] 
DatabaseManager.java
Handles database connection pooling and helper methods for JDBC queries and updates.

[NEW] 
Server.java
Initializes the HTTP server, serves static files, parses JSON requests/responses, and routes paths to appropriate handlers:

We will build a high-fidelity Single Page Application (SPA) dashboard. Based on the logged-in user's role (admin or student), the UI will adapt dynamically to display relevant tabs and panels.

[NEW] 
index.html
Main application frame. Contains the dashboard layout, navigation bar, and placeholders for dynamic sections. Built using Bootstrap 5, FontAwesome for icons, and Chart.js for beautiful graphs.

[NEW] 
styles.css
Custom premium styling using HSL color variables, smooth shadows, CSS glassmorphism, animated tab switching, and responsive design enhancements.

[NEW] 
app.js
Handles client-side routing, login/session management, calling backend APIs using fetch, updating DOM elements dynamically, and initializing Chart.js graphs for score tracking.

Verification Plan
Automated Tests
Since it is a standalone web app, we will verify correctness using:

Compiling the Java project:
powershell

javac -cp "lib/mysql-connector-j-8.0.33.jar" -d bin src/*.java
Running the Java server:
powershell

