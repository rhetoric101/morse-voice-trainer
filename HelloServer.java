import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// Add imports for transcribe endpoint
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class HelloServer {

    public static void main(String[] args) throws Exception {
        int port = 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        // GET /ping -> "pong"
        server.createContext("/ping", new TextHandler("pong\n"));
        System.out.println("REGISTERED /ping");

        // Serve a tiny test page at /
        server.createContext("/", new StaticIndexHandler());
        System.out.println("REGISTERED / (static)");

        // POST /echo -> returns whatever body you send
        server.createContext("/echo", new EchoHandler());
        System.out.println("REGISTERED /echo");

        // POST /upload-audio to read all bytes
        server.createContext("/upload-audio", new UploadAudioHandler());
        System.out.println("REGISTERED /upload-audio");

        // POST /transcribe (stub)
        server.createContext("/transcribe", new TranscribeStubHandler());
        System.out.println("REGISTERED /transcribe (STUB)");

        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("SERVER STARTED "
            + java.time.Instant.now()
            + " pid=" + ProcessHandle.current().pid()
            + " addr=" + server.getAddress());

        System.out.println("Server running on http://127.0.0.1:" + port);
        System.out.println("Try: curl http://127.0.0.1:" + port + "/ping");
    }

    // --- Handlers ---

    // Add new handler class
        static class UploadAudioHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange ex) throws IOException {
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    String msg = "{\"ok\":false,\"error\":\"POST required. Use fetch() with method:'POST'.\"}\n";
                    sendJson(ex, 405, msg);
                    return;
                }


                // Add server-side logging to see requests (MUST be inside handle)
                String ua = ex.getRequestHeaders().getFirst("User-Agent");

                String ct = ex.getRequestHeaders().getFirst("Content-Type");
                byte[] body = readAllBytes(ex.getRequestBody());

                System.out.println("[/upload-audio] UA=" + ua);
                System.out.println("[/upload-audio] Content-Type=" + ct + " bytes=" + body.length);

                String json = "{\"ok\":true,\"contentType\":\"" + safeJson(ct) + "\",\"bytes\":" + body.length + "}\n";
                sendBytes(ex, 200, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
            }

            private String safeJson(String s) {
                if (s == null) return "";
                return s.replace("\\", "\\\\").replace("\"", "\\\"");
            }
        }

    // Add drop-in style handler for /transcribe endpoint (server side)
    /* class TranscribeHandler implements com.sun.net.httpserver.HttpHandler {

        private static final int VOSK_SAMPLE_RATE = 16000;

        // Keep one model loaded for the life of the server (much faster than loading per request).
        private final Model model;

        TranscribeHandler() throws IOException {
            // "model" folder at repo root
            this.model = new Model("model");
        }

        @Override
        public void handle(com.sun.net.httpserver.HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, "{\"ok\":false,\"error\":\"POST only\"}");
                return;
            }

            byte[] webmBytes = ex.getRequestBody().readAllBytes();

            Path tmpDir = Files.createTempDirectory("morse-stt-");
            Path webm = tmpDir.resolve("audio.webm");
            Path wav = tmpDir.resolve("audio.wav");

            Files.write(webm, webmBytes);

            // Convert to 16kHz mono WAV, signed 16-bit PCM
            // ffmpeg -y -i audio.webm -ac 1 -ar 16000 -f wav audio.wav
            List<String> cmd = List.of(
                    "ffmpeg", "-y",
                    "-i", webm.toString(),
                    "-ac", "1",
                    "-ar", String.valueOf(VOSK_SAMPLE_RATE),
                    "-f", "wav",
                    wav.toString()
            );

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);

            Process p = pb.start();
            String ffmpegOut;
            try (InputStream is = p.getInputStream()) {
                ffmpegOut = new String(is.readAllBytes());
            }
            try {
                int code = p.waitFor();
                if (code != 0 || !Files.exists(wav)) {
                    sendJson(ex, 500, jsonErr("ffmpeg failed", ffmpegOut));
                    return;
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                sendJson(ex, 500, jsonErr("ffmpeg interrupted", ie.toString()));
                return;
            }

            // Read WAV and strip the 44-byte header (typical PCM WAV header).
            // (Good enough for our controlled ffmpeg output; later we can parse properly.)
            byte[] wavBytes = Files.readAllBytes(wav);
            if (wavBytes.length <= 44) {
                sendJson(ex, 500, jsonErr("wav too small", "bytes=" + wavBytes.length));
                return;
            }
            byte[] pcm = Arrays.copyOfRange(wavBytes, 44, wavBytes.length);

            // Optional: grammar restriction for MUCH higher accuracy on training vocab.
            // Example: only accept these words:
            // String grammar = "[\"test\",\"a\",\"b\",\"c\",\"cq\",\"k\"]";
            // try (Recognizer rec = new Recognizer(model, VOSK_SAMPLE_RATE, grammar)) { ... }

            String resultJson;
            try (Recognizer rec = new Recognizer(model, VOSK_SAMPLE_RATE)) {
                rec.setWords(true);
                boolean ok = rec.acceptWaveForm(pcm, pcm.length);
                // If ok==false you still get partial; finalResult gives the best final guess.
                resultJson = rec.getFinalResult();
            }

            sendJson(ex, 200, "{\"ok\":true,\"vosk\":" + resultJson + "}");

            // cleanup best-effort
            try { Files.deleteIfExists(webm); } catch (Exception ignored) {}
            try { Files.deleteIfExists(wav); } catch (Exception ignored) {}
            try { Files.deleteIfExists(tmpDir); } catch (Exception ignored) {}
        }
*/

        // Insert stub for /transcribe
        static class TranscribeStubHandler implements HttpHandler {
            @Override
            public void handle(HttpExchange ex) throws IOException {

                // Diagnostic header (safe to set before sendBytes)
                ex.getResponseHeaders().set("X-Handler", "transcribe-stub");

                // Let sendBytes() handle OPTIONS universally:
                // if this request is OPTIONS, sendBytes() will respond 204/-1 and return.
                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())
                        && !"OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {

                    String msg = """
                            {"ok":false,"error":"POST required. Use fetch() with method:'POST'."}
                            """;
                    sendBytes(ex, 405, "application/json; charset=utf-8",
                            msg.getBytes(StandardCharsets.UTF_8));
                    return;
                }

                if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                    // It's OPTIONS; sendBytes will do 204/no-body for us.
                    sendBytes(ex, 204, "application/json; charset=utf-8", new byte[0]);
                    return; // (sendBytes will already have returned early on OPTIONS)
                }

                String ok = """
                        {"ok":true,"stub":true,"message":"/transcribe hit"}
                        """;
                sendBytes(ex, 200, "application/json; charset=utf-8",
                        ok.getBytes(StandardCharsets.UTF_8));
            }
}


    // End stub for /transcribe ^
        private static void sendJson(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
            byte[] b = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(code, b.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(b); }
        }

        private static String jsonErr(String msg, String detail) {
            String safeMsg = msg.replace("\"", "'");
            String safeDetail = (detail == null ? "" : detail).replace("\"", "'");
            return "{\"ok\":false,\"error\":\"" + safeMsg + "\",\"detail\":\"" + safeDetail + "\"}";
        }
    

    static class TextHandler implements HttpHandler {
        private final byte[] payload;

        TextHandler(String text) {
            this.payload = text.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, "Method Not Allowed\n");
                return;
            }
            sendBytes(ex, 200, "text/plain; charset=utf-8", payload);
        }
    }

    static class EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                send(ex, 405, "Method Not Allowed\n");
                return;
            }

            byte[] body = readAllBytes(ex.getRequestBody());
            // For now: assume UTF-8 text. Later we'll accept audio bytes.
            sendBytes(ex, 200, "text/plain; charset=utf-8", body);
        }
    }

    static class StaticIndexHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("HIT StaticIndexHandler uri=" + exchange.getRequestURI()
                    + " method=" + exchange.getRequestMethod()
                    + " context=" + exchange.getHttpContext().getPath());

            // 👇 UNIQUE DIAGNOSTIC HEADER (goes here)
            exchange.getResponseHeaders().set("X-Handler", "static-index");
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.getResponseHeaders().set("X-Diag", "static-index-YES");
            byte[] body = fallbackPageBytes();

            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        }

        private byte[] fallbackPageBytes() {
            String page = """
                    <!doctype html>
                    <html>
                    <head><meta charset="utf-8"><title>Morse Voice Trainer</title></head>
                    <body>
                        <h1>Morse Voice Trainer (local)</h1>
                        <p>No index.html found. Create one next to HelloServer.java.</p>
                    </body>
                    </html>
                    """;
            return page.getBytes(StandardCharsets.UTF_8);
        }
    }
    // --- Utilities ---

    private static void send(HttpExchange ex, int status, String text) throws IOException {
        sendBytes(ex, status, "text/plain; charset=utf-8", text.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendBytes(HttpExchange ex, int status, String contentType, byte[] bytes) throws IOException {
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", contentType);
        // CORS: helpful later if you host frontend separately. Safe to include now.
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Headers", "Content-Type");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");

        // Handle preflight quickly
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        } finally {
            ex.close();
        }
    }


    private static byte[] readAllBytes(InputStream in) throws IOException {
        // Java 17: InputStream#readAllBytes exists.
        return in.readAllBytes();
    }
}

