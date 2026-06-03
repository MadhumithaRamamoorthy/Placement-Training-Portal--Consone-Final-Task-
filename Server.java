package src;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {
    private static final int PORT = 8080;

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
            
            // Route handlers
            server.createContext("/", new StaticFileHandler());
            server.createContext("/api/auth/register", new RegisterHandler());
            server.createContext("/api/auth/login", new LoginHandler());
            server.createContext("/api/tasks", new TasksHandler());
            server.createContext("/api/tasks/submit", new TaskSubmitHandler());
            server.createContext("/api/tests", new TestsHandler());
            server.createContext("/api/tests/questions", new TestQuestionsHandler());
            server.createContext("/api/tests/submit", new TestSubmitHandler());
            server.createContext("/api/scores", new ScoresHandler());
            server.createContext("/api/feedback", new FeedbackHandler());
            server.createContext("/api/students", new StudentsHandler());

            server.setExecutor(null); // default executor
            System.out.println("Placement Portal Server started on http://localhost:" + PORT);
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Core API Helpers ---

    private static String getRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String responseText, String contentType) throws IOException {
        byte[] responseBytes = responseText.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        // Enable CORS in case frontend runs in a separate dev server
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.trim().isEmpty()) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length > 1) {
                try {
                    params.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8.name()), 
                               URLDecoder.decode(kv[1], StandardCharsets.UTF_8.name()));
                } catch (UnsupportedEncodingException e) {
                    // Ignore
                }
            } else if (kv.length == 1) {
                params.put(kv[0], "");
            }
        }
        return params;
    }

    // Simple custom JSON parser using Regex
    private static String getJsonStringValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static Integer getJsonIntValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    // --- Route Handlers ---

    // 1. Static File Handler
    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }

            // Path to project files
            File file = new File("web" + path);
            if (!file.exists() || file.isDirectory()) {
                sendResponse(exchange, 404, "File Not Found", "text/plain");
                return;
            }

            String contentType = "application/octet-stream";
            if (path.endsWith(".html")) {
                contentType = "text/html; charset=utf-8";
            } else if (path.endsWith(".css")) {
                contentType = "text/css; charset=utf-8";
            } else if (path.endsWith(".js")) {
                contentType = "application/javascript; charset=utf-8";
            } else if (path.endsWith(".png")) {
                contentType = "image/png";
            } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
                contentType = "image/jpeg";
            } else if (path.endsWith(".ico")) {
                contentType = "image/x-icon";
            }

            // Serve the file
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.sendResponseHeaders(200, file.length());
            
            try (FileInputStream fis = new FileInputStream(file);
                 OutputStream os = exchange.getResponseBody()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        }
    }

    // 2. Authentication: Register
    static class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try {
                String body = getRequestBody(exchange);
                String username = getJsonStringValue(body, "username");
                String password = getJsonStringValue(body, "password");
                String email = getJsonStringValue(body, "email");
                String role = getJsonStringValue(body, "role"); // 'student' by default, or 'admin'
                String fullName = getJsonStringValue(body, "full_name");
                String rollNumber = getJsonStringValue(body, "roll_number");
                String department = getJsonStringValue(body, "department");

                if (username == null || password == null || email == null || fullName == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing required fields\"}", "application/json");
                    return;
                }

                if (role == null) {
                    role = "student";
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    // Check if username/email exists
                    String checkSql = "SELECT COUNT(*) FROM users WHERE username = ? OR email = ?";
                    try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                        checkStmt.setString(1, username);
                        checkStmt.setString(2, email);
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (rs.next() && rs.getInt(1) > 0) {
                                sendResponse(exchange, 400, "{\"error\":\"Username or Email already exists\"}", "application/json");
                                return;
                            }
                        }
                    }

                    // Insert user
                    String sql = "INSERT INTO users (username, password, email, role, full_name, roll_number, department) VALUES (?, ?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        stmt.setString(2, password);
                        stmt.setString(3, email);
                        stmt.setString(4, role);
                        stmt.setString(5, fullName);
                        stmt.setString(6, rollNumber);
                        stmt.setString(7, department);
                        stmt.executeUpdate();
                    }

                    sendResponse(exchange, 201, "{\"message\":\"User registered successfully\"}", "application/json");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }

    // 3. Authentication: Login
    static class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try {
                String body = getRequestBody(exchange);
                String username = getJsonStringValue(body, "username");
                String password = getJsonStringValue(body, "password");

                if (username == null || password == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing credentials\"}", "application/json");
                    return;
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "SELECT id, username, role, full_name, roll_number, department, email FROM users WHERE username = ? AND password = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setString(1, username);
                        stmt.setString(2, password);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                String userJson = String.format(
                                    "{\"status\":\"success\",\"user\":{\"id\":%d,\"username\":\"%s\",\"role\":\"%s\",\"full_name\":\"%s\",\"roll_number\":\"%s\",\"department\":\"%s\",\"email\":\"%s\"}}",
                                    rs.getInt("id"),
                                    rs.getString("username"),
                                    rs.getString("role"),
                                    rs.getString("full_name"),
                                    rs.getString("roll_number") != null ? rs.getString("roll_number") : "",
                                    rs.getString("department") != null ? rs.getString("department") : "",
                                    rs.getString("email")
                                );
                                sendResponse(exchange, 200, userJson, "application/json");
                            } else {
                                sendResponse(exchange, 401, "{\"error\":\"Invalid username or password\"}", "application/json");
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }

    // 4. Tasks Handler: GET (all or with student completion status) and POST (create task)
    static class TasksHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                try {
                    Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                    String studentIdStr = queryParams.get("studentId");
                    
                    try (Connection conn = DatabaseManager.getConnection()) {
                        StringBuilder sb = new StringBuilder("[");
                        if (studentIdStr != null && !studentIdStr.isEmpty()) {
                            // Fetch all tasks with student submission status
                            int studentId = Integer.parseInt(studentIdStr);
                            String sql = "SELECT t.id, t.title, t.description, t.due_date, " +
                                         "COALESCE(st.status, 'Pending') AS status, st.submission_link, st.submitted_at " +
                                         "FROM tasks t " +
                                         "LEFT JOIN student_tasks st ON t.id = st.task_id AND st.student_id = ? " +
                                         "ORDER BY t.due_date ASC";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setInt(1, studentId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    while (rs.next()) {
                                        sb.append(String.format(
                                            "{\"id\":%d,\"title\":\"%s\",\"description\":\"%s\",\"due_date\":\"%s\",\"status\":\"%s\",\"submission_link\":\"%s\",\"submitted_at\":\"%s\"},",
                                            rs.getInt("id"),
                                            rs.getString("title").replace("\"", "\\\""),
                                            rs.getString("description").replace("\"", "\\\"").replace("\n", "\\n"),
                                            rs.getDate("due_date").toString(),
                                            rs.getString("status"),
                                            rs.getString("submission_link") != null ? rs.getString("submission_link").replace("\"", "\\\"") : "",
                                            rs.getTimestamp("submitted_at") != null ? rs.getTimestamp("submitted_at").toString() : ""
                                        ));
                                    }
                                }
                            }
                        } else {
                            // Standard get all tasks
                            String sql = "SELECT * FROM tasks ORDER BY due_date ASC";
                            try (Statement stmt = conn.createStatement();
                                 ResultSet rs = stmt.executeQuery(sql)) {
                                while (rs.next()) {
                                    sb.append(String.format(
                                        "{\"id\":%d,\"title\":\"%s\",\"description\":\"%s\",\"due_date\":\"%s\"},",
                                        rs.getInt("id"),
                                        rs.getString("title").replace("\"", "\\\""),
                                        rs.getString("description").replace("\"", "\\\"").replace("\n", "\\n"),
                                        rs.getDate("due_date").toString()
                                    ));
                                }
                            }
                        }
                        if (sb.length() > 1) {
                            sb.setLength(sb.length() - 1); // remove trailing comma
                        }
                        sb.append("]");
                        sendResponse(exchange, 200, sb.toString(), "application/json");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // Admin creates a daily task
                try {
                    String body = getRequestBody(exchange);
                    String title = getJsonStringValue(body, "title");
                    String description = getJsonStringValue(body, "description");
                    String dueDate = getJsonStringValue(body, "due_date"); // yyyy-MM-dd

                    if (title == null || description == null || dueDate == null) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing task details\"}", "application/json");
                        return;
                    }

                    try (Connection conn = DatabaseManager.getConnection()) {
                        String sql = "INSERT INTO tasks (title, description, due_date) VALUES (?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setString(1, title);
                            stmt.setString(2, description);
                            stmt.setString(3, dueDate);
                            stmt.executeUpdate();
                        }
                        sendResponse(exchange, 201, "{\"message\":\"Task created successfully\"}", "application/json");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
            }
        }
    }

    // 5. Submit Task Handler: POST
    static class TaskSubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try {
                String body = getRequestBody(exchange);
                Integer studentId = getJsonIntValue(body, "student_id");
                Integer taskId = getJsonIntValue(body, "task_id");
                String submissionLink = getJsonStringValue(body, "submission_link");

                if (studentId == null || taskId == null || submissionLink == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing submission fields\"}", "application/json");
                    return;
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "INSERT INTO student_tasks (student_id, task_id, status, submitted_at, submission_link) " +
                                 "VALUES (?, ?, 'Completed', NOW(), ?) " +
                                 "ON DUPLICATE KEY UPDATE status = 'Completed', submitted_at = NOW(), submission_link = ?";
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, studentId);
                        stmt.setInt(2, taskId);
                        stmt.setString(3, submissionLink);
                        stmt.setString(4, submissionLink);
                        stmt.executeUpdate();
                    }
                    sendResponse(exchange, 200, "{\"message\":\"Task submitted successfully\"}", "application/json");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }

    // 6. Mock Tests Handler: GET (all tests) and POST (create mock test with questions)
    static class TestsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "SELECT * FROM mock_tests ORDER BY id DESC";
                    StringBuilder sb = new StringBuilder("[");
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery(sql)) {
                        while (rs.next()) {
                            sb.append(String.format(
                                "{\"id\":%d,\"title\":\"%s\",\"description\":\"%s\",\"duration_minutes\":%d},",
                                rs.getInt("id"),
                                rs.getString("title").replace("\"", "\\\""),
                                rs.getString("description").replace("\"", "\\\"").replace("\n", "\\n"),
                                rs.getInt("duration_minutes")
                            ));
                        }
                    }
                    if (sb.length() > 1) {
                        sb.setLength(sb.length() - 1);
                    }
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString(), "application/json");
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // Admin creates a mock test
                try {
                    String body = getRequestBody(exchange);
                    String title = getJsonStringValue(body, "title");
                    String description = getJsonStringValue(body, "description");
                    Integer durationMinutes = getJsonIntValue(body, "duration_minutes");

                    if (title == null || description == null || durationMinutes == null) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing mock test details\"}", "application/json");
                        return;
                    }

                    try (Connection conn = DatabaseManager.getConnection()) {
                        conn.setAutoCommit(false); // Transaction boundary
                        
                        // Insert test
                        int testId = -1;
                        String testSql = "INSERT INTO mock_tests (title, description, duration_minutes) VALUES (?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(testSql, Statement.RETURN_GENERATED_KEYS)) {
                            stmt.setString(1, title);
                            stmt.setString(2, description);
                            stmt.setInt(3, durationMinutes);
                            stmt.executeUpdate();
                            try (ResultSet keys = stmt.getGeneratedKeys()) {
                                if (keys.next()) {
                                    testId = keys.getInt(1);
                                }
                            }
                        }

                        if (testId == -1) {
                            conn.rollback();
                            sendResponse(exchange, 500, "{\"error\":\"Failed to save mock test header\"}", "application/json");
                            return;
                        }

                        // Parse and insert questions array
                        int start = body.indexOf("\"questions\"");
                        if (start != -1) {
                            int arrayStart = body.indexOf("[", start);
                            int arrayEnd = body.lastIndexOf("]");
                            if (arrayStart != -1 && arrayEnd != -1 && arrayEnd > arrayStart) {
                                String qArrayStr = body.substring(arrayStart + 1, arrayEnd).trim();
                                
                                // Matches { ... } block
                                Pattern qPattern = Pattern.compile("\\{[^\\}]+\\}");
                                Matcher qMatcher = qPattern.matcher(qArrayStr);
                                
                                String questionSql = "INSERT INTO questions (test_id, question_text, option_a, option_b, option_c, option_d, correct_option) VALUES (?, ?, ?, ?, ?, ?, ?)";
                                try (PreparedStatement qStmt = conn.prepareStatement(questionSql)) {
                                    while (qMatcher.find()) {
                                        String qBlock = qMatcher.group();
                                        String qText = getJsonStringValue(qBlock, "question_text");
                                        String optA = getJsonStringValue(qBlock, "option_a");
                                        String optB = getJsonStringValue(qBlock, "option_b");
                                        String optC = getJsonStringValue(qBlock, "option_c");
                                        String optD = getJsonStringValue(qBlock, "option_d");
                                        String correct = getJsonStringValue(qBlock, "correct_option"); // "A", "B", "C", "D"

                                        if (qText != null && optA != null && optB != null && optC != null && optD != null && correct != null) {
                                            qStmt.setInt(1, testId);
                                            qStmt.setString(2, qText);
                                            qStmt.setString(3, optA);
                                            qStmt.setString(4, optB);
                                            qStmt.setString(5, optC);
                                            qStmt.setString(6, optD);
                                            qStmt.setString(7, correct.toUpperCase());
                                            qStmt.addBatch();
                                        }
                                    }
                                    qStmt.executeBatch();
                                }
                            }
                        }

                        conn.commit();
                        sendResponse(exchange, 201, "{\"message\":\"Mock test created successfully\",\"test_id\":" + testId + "}", "application/json");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
            }
        }
    }

    // 7. Get Questions Handler: GET questions for a test (Hiding correct option)
    static class TestQuestionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                String testIdStr = queryParams.get("id");
                if (testIdStr == null || testIdStr.isEmpty()) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing test ID\"}", "application/json");
                    return;
                }

                int testId = Integer.parseInt(testIdStr);
                try (Connection conn = DatabaseManager.getConnection()) {
                    String sql = "SELECT id, question_text, option_a, option_b, option_c, option_d FROM questions WHERE test_id = ?";
                    StringBuilder sb = new StringBuilder("[");
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, testId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                sb.append(String.format(
                                    "{\"id\":%d,\"question_text\":\"%s\",\"option_a\":\"%s\",\"option_b\":\"%s\",\"option_c\":\"%s\",\"option_d\":\"%s\"},",
                                    rs.getInt("id"),
                                    rs.getString("question_text").replace("\"", "\\\"").replace("\n", "\\n"),
                                    rs.getString("option_a").replace("\"", "\\\""),
                                    rs.getString("option_b").replace("\"", "\\\""),
                                    rs.getString("option_c").replace("\"", "\\\""),
                                    rs.getString("option_d").replace("\"", "\\\"")
                                ));
                            }
                        }
                    }
                    if (sb.length() > 1) {
                        sb.setLength(sb.length() - 1);
                    }
                    sb.append("]");
                    sendResponse(exchange, 200, sb.toString(), "application/json");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }

    // 8. Submit Answers Handler: POST answers and grade them
    static class TestSubmitHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try {
                String body = getRequestBody(exchange);
                Integer studentId = getJsonIntValue(body, "student_id");
                Integer testId = getJsonIntValue(body, "test_id");
                
                if (studentId == null || testId == null) {
                    sendResponse(exchange, 400, "{\"error\":\"Missing score details\"}", "application/json");
                    return;
                }

                // Extract answers map: "answers": { "1": "A", "2": "C" }
                Map<Integer, String> studentAnswers = new HashMap<>();
                int answersIndex = body.indexOf("\"answers\"");
                if (answersIndex != -1) {
                    int mapStart = body.indexOf("{", answersIndex);
                    int mapEnd = body.indexOf("}", mapStart);
                    if (mapStart != -1 && mapEnd != -1 && mapEnd > mapStart) {
                        String mapStr = body.substring(mapStart + 1, mapEnd);
                        // Regex matches "id": "option" (e.g. "1":"A" or "1": "A")
                        Pattern pairPattern = Pattern.compile("\"(\\d+)\"\\s*:\\s*\"([A-Da-d])\"");
                        Matcher pairMatcher = pairPattern.matcher(mapStr);
                        while (pairMatcher.find()) {
                            int qId = Integer.parseInt(pairMatcher.group(1));
                            String option = pairMatcher.group(2).toUpperCase();
                            studentAnswers.put(qId, option);
                        }
                    }
                }

                try (Connection conn = DatabaseManager.getConnection()) {
                    // Fetch correct options
                    String sql = "SELECT id, correct_option FROM questions WHERE test_id = ?";
                    int score = 0;
                    int maxScore = 0;
                    try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                        stmt.setInt(1, testId);
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                maxScore++;
                                int qId = rs.getInt("id");
                                String correctOpt = rs.getString("correct_option").toUpperCase();
                                
                                String studentOpt = studentAnswers.get(qId);
                                if (studentOpt != null && studentOpt.equals(correctOpt)) {
                                    score++;
                                }
                            }
                        }
                    }

                    // Insert score
                    String insertSql = "INSERT INTO scores (student_id, test_id, score, max_score) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                        stmt.setInt(1, studentId);
                        stmt.setInt(2, testId);
                        stmt.setInt(3, score);
                        stmt.setInt(4, maxScore);
                        stmt.executeUpdate();
                    }

                    String respJson = String.format("{\"status\":\"success\",\"score\":%d,\"max_score\":%d}", score, maxScore);
                    sendResponse(exchange, 200, respJson, "application/json");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }

    // 9. Score Tracking Handler: GET (student individual scores OR admin aggregate stats)
    static class ScoresHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try {
                Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                String studentIdStr = queryParams.get("studentId");
                
                try (Connection conn = DatabaseManager.getConnection()) {
                    if (studentIdStr != null && !studentIdStr.isEmpty()) {
                        // Return individual student's test scores for plotting
                        int studentId = Integer.parseInt(studentIdStr);
                        String sql = "SELECT s.score, s.max_score, s.taken_at, t.title AS test_title " +
                                     "FROM scores s " +
                                     "JOIN mock_tests t ON s.test_id = t.id " +
                                     "WHERE s.student_id = ? " +
                                     "ORDER BY s.taken_at ASC";
                        StringBuilder sb = new StringBuilder("[");
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, studentId);
                            try (ResultSet rs = stmt.executeQuery()) {
                                while (rs.next()) {
                                    sb.append(String.format(
                                        "{\"score\":%d,\"max_score\":%d,\"taken_at\":\"%s\",\"test_title\":\"%s\"},",
                                        rs.getInt("score"),
                                        rs.getInt("max_score"),
                                        rs.getTimestamp("taken_at").toString(),
                                        rs.getString("test_title").replace("\"", "\\\"")
                                    ));
                                }
                            }
                        }
                        if (sb.length() > 1) {
                            sb.setLength(sb.length() - 1);
                        }
                        sb.append("]");
                        sendResponse(exchange, 200, sb.toString(), "application/json");
                    } else {
                        // Admin Dashboard: aggregate statistics
                        // We will return a JSON containing overall stats, average percentages of tests, and a list of all raw scores
                        
                        // 1. Total Student Count
                        int totalStudents = 0;
                        String countSql = "SELECT COUNT(*) FROM users WHERE role = 'student'";
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery(countSql)) {
                            if (rs.next()) {
                                totalStudents = rs.getInt(1);
                            }
                        }

                        // 2. Average percentages of each mock test
                        StringBuilder testAverages = new StringBuilder("[");
                        String avgSql = "SELECT t.title, AVG(s.score * 100.0 / s.max_score) AS avg_pct " +
                                        "FROM scores s JOIN mock_tests t ON s.test_id = t.id " +
                                        "GROUP BY s.test_id, t.title";
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery(avgSql)) {
                            while (rs.next()) {
                                testAverages.append(String.format(
                                    "{\"test_title\":\"%s\",\"avg_percentage\":%.2f},",
                                    rs.getString("title").replace("\"", "\\\""),
                                    rs.getDouble("avg_pct")
                                ));
                            }
                        }
                        if (testAverages.length() > 1) {
                            testAverages.setLength(testAverages.length() - 1);
                        }
                        testAverages.append("]");

                        // 3. Raw recent scores of all students
                        StringBuilder rawScores = new StringBuilder("[");
                        String rawSql = "SELECT u.full_name, u.roll_number, t.title AS test_title, s.score, s.max_score, s.taken_at " +
                                        "FROM scores s " +
                                        "JOIN users u ON s.student_id = u.id " +
                                        "JOIN mock_tests t ON s.test_id = t.id " +
                                        "ORDER BY s.taken_at DESC";
                        try (Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery(rawSql)) {
                            while (rs.next()) {
                                rawScores.append(String.format(
                                    "{\"student_name\":\"%s\",\"roll_number\":\"%s\",\"test_title\":\"%s\",\"score\":%d,\"max_score\":%d,\"taken_at\":\"%s\"},",
                                    rs.getString("full_name").replace("\"", "\\\""),
                                    rs.getString("roll_number") != null ? rs.getString("roll_number").replace("\"", "\\\"") : "",
                                    rs.getString("test_title").replace("\"", "\\\""),
                                    rs.getInt("score"),
                                    rs.getInt("max_score"),
                                    rs.getTimestamp("taken_at").toString()
                                ));
                            }
                        }
                        if (rawScores.length() > 1) {
                            rawScores.setLength(rawScores.length() - 1);
                        }
                        rawScores.append("]");

                        String responseJson = String.format(
                            "{\"total_students\":%d,\"test_averages\":%s,\"recent_scores\":%s}",
                            totalStudents,
                            testAverages.toString(),
                            rawScores.toString()
                        );
                        sendResponse(exchange, 200, responseJson, "application/json");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }

    // 10. Interview Feedback Handler: GET (all or filter by studentId) and POST (submit feedback)
    static class FeedbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                try {
                    Map<String, String> queryParams = parseQueryParams(exchange.getRequestURI().getQuery());
                    String studentIdStr = queryParams.get("studentId");
                    
                    try (Connection conn = DatabaseManager.getConnection()) {
                        StringBuilder sb = new StringBuilder("[");
                        if (studentIdStr != null && !studentIdStr.isEmpty()) {
                            // Filtered for individual student
                            int studentId = Integer.parseInt(studentIdStr);
                            String sql = "SELECT * FROM interview_feedback WHERE student_id = ? ORDER BY interview_date DESC";
                            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                                stmt.setInt(1, studentId);
                                try (ResultSet rs = stmt.executeQuery()) {
                                    while (rs.next()) {
                                        sb.append(String.format(
                                            "{\"id\":%d,\"interviewer_name\":\"%s\",\"interview_date\":\"%s\"," +
                                            "\"communication_score\":%d,\"technical_score\":%d,\"coding_score\":%d," +
                                            "\"comments\":\"%s\",\"created_at\":\"%s\"},",
                                            rs.getInt("id"),
                                            rs.getString("interviewer_name").replace("\"", "\\\""),
                                            rs.getDate("interview_date").toString(),
                                            rs.getInt("communication_score"),
                                            rs.getInt("technical_score"),
                                            rs.getInt("coding_score"),
                                            rs.getString("comments").replace("\"", "\\\"").replace("\n", "\\n"),
                                            rs.getTimestamp("created_at").toString()
                                        ));
                                    }
                                }
                            }
                        } else {
                            // Fetch all feedbacks with student names for Admin
                            String sql = "SELECT f.*, u.full_name, u.roll_number, u.department " +
                                         "FROM interview_feedback f " +
                                         "JOIN users u ON f.student_id = u.id " +
                                         "ORDER BY f.created_at DESC";
                            try (Statement stmt = conn.createStatement();
                                 ResultSet rs = stmt.executeQuery(sql)) {
                                while (rs.next()) {
                                    sb.append(String.format(
                                        "{\"id\":%d,\"student_name\":\"%s\",\"roll_number\":\"%s\",\"department\":\"%s\"," +
                                        "\"interviewer_name\":\"%s\",\"interview_date\":\"%s\"," +
                                        "\"communication_score\":%d,\"technical_score\":%d,\"coding_score\":%d," +
                                        "\"comments\":\"%s\",\"created_at\":\"%s\"},",
                                        rs.getInt("id"),
                                        rs.getString("full_name").replace("\"", "\\\""),
                                        rs.getString("roll_number") != null ? rs.getString("roll_number").replace("\"", "\\\"") : "",
                                        rs.getString("department") != null ? rs.getString("department").replace("\"", "\\\"") : "",
                                        rs.getString("interviewer_name").replace("\"", "\\\""),
                                        rs.getDate("interview_date").toString(),
                                        rs.getInt("communication_score"),
                                        rs.getInt("technical_score"),
                                        rs.getInt("coding_score"),
                                        rs.getString("comments").replace("\"", "\\\"").replace("\n", "\\n"),
                                        rs.getTimestamp("created_at").toString()
                                    ));
                                }
                            }
                        }
                        if (sb.length() > 1) {
                            sb.setLength(sb.length() - 1);
                        }
                        sb.append("]");
                        sendResponse(exchange, 200, sb.toString(), "application/json");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // Admin submits interview feedback
                try {
                    String body = getRequestBody(exchange);
                    Integer studentId = getJsonIntValue(body, "student_id");
                    String interviewerName = getJsonStringValue(body, "interviewer_name");
                    String interviewDate = getJsonStringValue(body, "interview_date"); // yyyy-MM-dd
                    Integer commScore = getJsonIntValue(body, "communication_score");
                    Integer techScore = getJsonIntValue(body, "technical_score");
                    Integer codingScore = getJsonIntValue(body, "coding_score");
                    String comments = getJsonStringValue(body, "comments");

                    if (studentId == null || interviewerName == null || interviewDate == null ||
                        commScore == null || techScore == null || codingScore == null || comments == null) {
                        sendResponse(exchange, 400, "{\"error\":\"Missing feedback details\"}", "application/json");
                        return;
                    }

                    try (Connection conn = DatabaseManager.getConnection()) {
                        String sql = "INSERT INTO interview_feedback (student_id, interviewer_name, interview_date, communication_score, technical_score, coding_score, comments) VALUES (?, ?, ?, ?, ?, ?, ?)";
                        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                            stmt.setInt(1, studentId);
                            stmt.setString(2, interviewerName);
                            stmt.setString(3, interviewDate);
                            stmt.setInt(4, commScore);
                            stmt.setInt(5, techScore);
                            stmt.setInt(6, codingScore);
                            stmt.setString(7, comments);
                            stmt.executeUpdate();
                        }
                        sendResponse(exchange, 201, "{\"message\":\"Interview feedback recorded successfully\"}", "application/json");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
                }
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
            }
        }
    }

    // 11. Students Handler: GET list of registered students (useful for admin selection dropdowns)
    static class StudentsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 204, "", "text/plain");
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}", "application/json");
                return;
            }

            try (Connection conn = DatabaseManager.getConnection()) {
                String sql = "SELECT id, full_name, roll_number, department, email FROM users WHERE role = 'student' ORDER BY full_name ASC";
                StringBuilder sb = new StringBuilder("[");
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        sb.append(String.format(
                            "{\"id\":%d,\"full_name\":\"%s\",\"roll_number\":\"%s\",\"department\":\"%s\",\"email\":\"%s\"},",
                            rs.getInt("id"),
                            rs.getString("full_name").replace("\"", "\\\""),
                            rs.getString("roll_number") != null ? rs.getString("roll_number").replace("\"", "\\\"") : "",
                            rs.getString("department") != null ? rs.getString("department").replace("\"", "\\\"") : "",
                            rs.getString("email").replace("\"", "\\\"")
                        ));
                    }
                }
                if (sb.length() > 1) {
                    sb.setLength(sb.length() - 1);
                }
                sb.append("]");
                sendResponse(exchange, 200, sb.toString(), "application/json");
            } catch (Exception e) {
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}", "application/json");
            }
        }
    }
}
