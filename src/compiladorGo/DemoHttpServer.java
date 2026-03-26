package compiladorGo;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Servidor HTTP mínimo para demonstrar análise léxica (passo a passo no front)
 * e análise sintática sobre o código colado pelo usuário.
 */
public final class DemoHttpServer {

    private static final Object PARSER_LOCK = new Object();
    private static volatile boolean parserBootstrapped;

    public static void main(String[] args) throws IOException {
        int port = 8787;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                System.err.println("Uso: java ... DemoHttpServer [porta]");
                return;
            }
        }

        bootstrapParserIfNeeded();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/analyze", new AnalyzeHandler());
        server.createContext("/api/sample", new SampleHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Demo compilador Go — abra no navegador:");
        System.out.println("  http://127.0.0.1:" + port + "/");
        System.out.println("Pressione Ctrl+C para encerrar.");
    }

    private static void bootstrapParserIfNeeded() {
        if (parserBootstrapped) {
            return;
        }
        synchronized (PARSER_LOCK) {
            if (parserBootstrapped) {
                return;
            }
            try {
                new GoGramatica(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                throw new IllegalStateException("Falha ao inicializar parser JavaCC", e);
            }
            parserBootstrapped = true;
        }
    }

    static final class AnalyzeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                ex.close();
                return;
            }
            byte[] body = ex.getRequestBody().readAllBytes();
            String jsonIn = new String(body, StandardCharsets.UTF_8);
            String source = extractJsonString(jsonIn, "source");
            if (source == null) {
                source = "";
            }

            String responseJson;
            synchronized (PARSER_LOCK) {
                responseJson = analyzeLocked(source);
            }

            byte[] out = responseJson.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        }
    }

    static final class SampleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Path p = Path.of("teste.go");
            String sample = Files.exists(p)
                    ? Files.readString(p, StandardCharsets.UTF_8)
                    : "package main\n\nimport \"fmt\"\n\nfunc main() {\n    fmt.Println(\"oi\")\n}\n";
            String json = "{\"source\":" + jsonString(sample) + "}";
            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(out);
            }
        }
    }

    static final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path == null || "/".equals(path)) {
                path = "/index.html";
            }
            Path base = Path.of("web").toAbsolutePath().normalize();
            Path rel = Path.of(path.replaceFirst("^/+", ""));
            Path file = base.resolve(rel).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) {
                ex.sendResponseHeaders(404, -1);
                ex.close();
                return;
            }
            byte[] data = Files.readAllBytes(file);
            String ct = guessContentType(path);
            ex.getResponseHeaders().set("Content-Type", ct);
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }

        private static String guessContentType(String path) {
            if (path.endsWith(".html")) {
                return "text/html; charset=utf-8";
            }
            if (path.endsWith(".css")) {
                return "text/css; charset=utf-8";
            }
            if (path.endsWith(".js")) {
                return "text/javascript; charset=utf-8";
            }
            return "application/octet-stream";
        }
    }

    private static String analyzeLocked(String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        List<String> tokenJsonObjects = new ArrayList<>();

        boolean syntaxOk = true;
        String syntaxMessage = "Programa aceito pela gramática (Inicio → … → EOF).";

        GoGramatica.ReInit(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.name());
        try {
            while (true) {
                Token tok = GoGramaticaTokenManager.getNextToken();
                if (tok.kind == GoGramaticaConstants.EOF) {
                    break;
                }
                tokenJsonObjects.add(tokenToJsonObject(tok));
            }
        } catch (TokenMgrError te) {
            syntaxOk = false;
            syntaxMessage = te.getMessage() != null ? te.getMessage() : te.toString();
            return buildAnalyzeJson(tokenJsonObjects, syntaxOk, syntaxMessage);
        }

        GoGramatica.ReInit(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.name());
        try {
            GoGramatica.Inicio();
        } catch (ParseException pe) {
            syntaxOk = false;
            syntaxMessage = pe.getMessage() != null ? pe.getMessage() : pe.toString();
        } catch (TokenMgrError te) {
            syntaxOk = false;
            syntaxMessage = te.getMessage() != null ? te.getMessage() : te.toString();
        }

        return buildAnalyzeJson(tokenJsonObjects, syntaxOk, syntaxMessage);
    }

    private static String buildAnalyzeJson(List<String> tokenJsonObjects, boolean syntaxOk, String syntaxMessage) {
        StringBuilder sb = new StringBuilder(256 + tokenJsonObjects.size() * 64);
        sb.append("{\"tokens\":[");
        for (int i = 0; i < tokenJsonObjects.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(tokenJsonObjects.get(i));
        }
        sb.append("],\"syntax\":{");
        sb.append("\"ok\":").append(syntaxOk ? "true" : "false");
        sb.append(",\"message\":").append(jsonString(syntaxMessage));
        sb.append("}}");
        return sb.toString();
    }

    private static String tokenToJsonObject(Token tok) {
        String kindLabel = kindLabel(tok.kind);
        return "{\"kind\":" + tok.kind
                + ",\"kindName\":" + jsonString(kindLabel)
                + ",\"lexeme\":" + jsonString(tok.image)
                + ",\"line\":" + tok.beginLine
                + ",\"column\":" + tok.beginColumn + "}";
    }

    private static String kindLabel(int kind) {
        if (kind < 0 || kind >= GoGramaticaConstants.tokenImage.length) {
            return "UNKNOWN";
        }
        String raw = GoGramaticaConstants.tokenImage[kind];
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        if (raw.length() >= 2 && raw.startsWith("<") && raw.endsWith(">")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static String jsonString(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    /**
     * Extrai o valor de uma única chave string simples em JSON minimalista (\"source\":\"...\").
     */
    private static String extractJsonString(String json, String key) {
        String needle = "\"" + key + "\"";
        int k = json.indexOf(needle);
        if (k < 0) {
            return null;
        }
        int colon = json.indexOf(':', k + needle.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c == '"') {
                break;
            }
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(i + 1);
                switch (n) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '\\', '"' -> sb.append(n);
                    case 'u' -> {
                        if (i + 5 < json.length()) {
                            String hex = json.substring(i + 2, i + 6);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 5;
                        }
                    }
                    default -> sb.append(n);
                }
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }
}
