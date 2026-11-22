/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez | Matrícula: Al03039306
 * ========================================================================= */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServidorWeb {
    private static BaseDatos db;
    private static Path directorioPublico;

    public static void main(String[] args) throws IOException {
        Path base = Paths.get("").toAbsolutePath();
        if (Files.exists(base.resolve("public"))) {
            directorioPublico = base.resolve("public");
            db = new BaseDatos(base.resolve("backend").resolve("database_eventgo.json"));
        } else {
            directorioPublico = base.getParent().resolve("public");
            db = new BaseDatos(base.resolve("database_eventgo.json"));
        }

        int puerto = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(puerto), 0);

        server.createContext("/api/estado", ex -> manejarApi(ex, () -> db.exportarTodoJson(), true));

        server.createContext("/api/evento/crear", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            String msg = db.crearEvento(
                d.get("nombreEvento"), d.get("fechaEvento"), d.get("recinto"), d.get("categoria"),
                Integer.parseInt(d.get("numFilas")), Integer.parseInt(d.get("numColumnas")),
                Integer.parseInt(d.get("numFilasVIP")), Double.parseDouble(d.get("costoGeneral")),
                Double.parseDouble(d.get("costoVIP"))
            );
            return jsonMensaje(true, msg);
        }, false));

        server.createContext("/api/evento/eliminar", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.eliminarEvento(d.get("idEvento")));
        }, false));

        server.createContext("/api/vender", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            List<String> listaAsientos = new ArrayList<>();
            for (String s : d.getOrDefault("asientos", "").split(",")) {
                if (!s.trim().isEmpty()) listaAsientos.add(s.trim());
            }
            String msg = db.registrarVenta(
                d.get("idEvento"), d.get("nombre"), Integer.parseInt(d.get("edad")),
                d.get("correo"), d.get("telefono"), d.get("tipo"), listaAsientos
            );
            return jsonMensaje(true, msg);
        }, false));

        server.createContext("/api/pagar", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.procesarPago(d.get("idEvento"), d.get("referencia"), Double.parseDouble(d.get("monto"))));
        }, false));

        server.createContext("/api/checkin", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.registrarCheckIn(d.get("idEvento"), d.get("referencia")));
        }, false));

        server.createContext("/api/boleto/actualizar", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.actualizarBoleto(
                d.get("idEvento"), Integer.parseInt(d.get("idBoleto")),
                d.get("nombre"), Integer.parseInt(d.get("edad")), d.get("correo")
            ));
        }, false));

        server.createContext("/api/boleto/eliminar", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.eliminarBoleto(d.get("idEvento"), Integer.parseInt(d.get("idBoleto"))));
        }, false));

        server.createContext("/api/cliente/guardar", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.guardarClienteManual(
                d.get("idCliente"), d.get("nombre"), Integer.parseInt(d.get("edad")),
                d.get("correo"), d.get("telefono")
            ));
        }, false));

        server.createContext("/api/cliente/eliminar", ex -> manejarApi(ex, () -> {
            Map<String, String> d = parsearBody(leerBody(ex));
            return jsonMensaje(true, db.eliminarCliente(d.get("idCliente")));
        }, false));

        server.createContext("/", ServidorWeb::servirArchivoEstatico);

        server.setExecutor(null);
        server.start();
        System.out.println("==========================================================");
        System.out.println(" EventGo Full-Stack Activo en: http://localhost:" + puerto);
        System.out.println(" Base de Datos JSON: database_eventgo.json");
        System.out.println("==========================================================");
    }

    @FunctionalInterface
    interface AccionApi { String ejecutar() throws Exception; }

    private static void manejarApi(HttpExchange exchange, AccionApi accion, boolean esGet) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        try {
            if (esGet && !"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarRespuesta(exchange, 405, jsonMensaje(false, "Método no permitido"));
                return;
            }
            if (!esGet && !"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                enviarRespuesta(exchange, 405, jsonMensaje(false, "Se requiere POST"));
                return;
            }
            enviarRespuesta(exchange, 200, accion.ejecutar());
        } catch (Exception e) {
            enviarRespuesta(exchange, 400, jsonMensaje(false, e.getMessage() != null ? e.getMessage() : "Error en solicitud"));
        }
    }

    private static void servirArchivoEstatico(HttpExchange exchange) throws IOException {
        String rutaUri = exchange.getRequestURI().getPath();
        if (rutaUri.equals("/")) rutaUri = "/index.html";

        Path archivo = directorioPublico.resolve(rutaUri.substring(1)).normalize();
        if (!archivo.startsWith(directorioPublico) || !Files.exists(archivo) || Files.isDirectory(archivo)) {
            enviarRespuesta(exchange, 404, "404 - Recurso no encontrado");
            return;
        }

        String nombre = archivo.toString().toLowerCase();
        String mime = "text/plain; charset=UTF-8";
        if (nombre.endsWith(".html")) mime = "text/html; charset=UTF-8";
        else if (nombre.endsWith(".css")) mime = "text/css; charset=UTF-8";
        else if (nombre.endsWith(".js")) mime = "application/javascript; charset=UTF-8";
        else if (nombre.endsWith(".png")) mime = "image/png";
        else if (nombre.endsWith(".jpg") || nombre.endsWith(".jpeg")) mime = "image/jpeg";

        byte[] bytes = Files.readAllBytes(archivo);
        exchange.getResponseHeaders().add("Content-Type", mime);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    private static String leerBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> parsearBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null) return map;
        String limpio = body.trim();
        if (limpio.startsWith("{") && limpio.endsWith("}")) limpio = limpio.substring(1, limpio.length() - 1);
        List<String> pares = new ArrayList<>();
        boolean enComillas = false;
        StringBuilder actual = new StringBuilder();
        for (char c : limpio.toCharArray()) {
            if (c == '"') enComillas = !enComillas;
            if (c == ',' && !enComillas) { pares.add(actual.toString()); actual.setLength(0); }
            else actual.append(c);
        }
        if (actual.length() > 0) pares.add(actual.toString());

        for (String par : pares) {
            int sep = par.indexOf(':');
            if (sep > 0) {
                String k = par.substring(0, sep).trim().replace("\"", "");
                String v = par.substring(sep + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) v = v.substring(1, v.length() - 1);
                map.put(k, v);
            }
        }
        return map;
    }

    private static String jsonMensaje(boolean ok, String msg) {
        String limpio = msg == null ? "" : msg.replace("\"", "'").replace("\n", " ");
        return "{\"ok\":" + ok + ",\"mensaje\":\"" + limpio + "\"}";
    }

    private static void enviarRespuesta(HttpExchange exchange, int codigo, String texto) throws IOException {
        byte[] bytes = texto.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }
}