/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez
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
    private static Evento eventoActual;
    private static Path directorioPublico;

    public static void main(String[] args) throws IOException {
        Path base = Paths.get("").toAbsolutePath();
        if (Files.exists(base.resolve("public"))) {
            directorioPublico = base.resolve("public");
            eventoActual = new Evento(base.resolve("backend").resolve("datos_evento.json"));
        } else {
            directorioPublico = base.getParent().resolve("public");
            eventoActual = new Evento(base.resolve("datos_evento.json"));
        }

        int puerto = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(puerto), 0);

        // API REST (CRUD & Lógica de Negocio)
        server.createContext("/api/estado", exchange -> manejarApi(exchange, () -> eventoActual.obtenerEstadoJson(), true));

        server.createContext("/api/evento", exchange -> manejarApi(exchange, () -> {
            Map<String, String> data = parsearBody(leerBody(exchange));
            eventoActual.configurarEvento(
                data.get("nombreEvento"),
                data.get("fechaEvento"),
                data.get("recinto"),
                Integer.parseInt(data.get("numFilas")),
                Integer.parseInt(data.get("numColumnas")),
                Integer.parseInt(data.get("numFilasVIP")),
                Double.parseDouble(data.get("costoGeneral")),
                Double.parseDouble(data.get("costoVIP"))
            );
            return jsonMensaje(true, "Evento configurado y guardado exitosamente.");
        }, false));

        server.createContext("/api/vender", exchange -> manejarApi(exchange, () -> {
            Map<String, String> data = parsearBody(leerBody(exchange));
            String asientosStr = data.getOrDefault("asientos", "");
            List<String> listaAsientos = new ArrayList<>();
            for (String s : asientosStr.split(",")) {
                if (!s.trim().isEmpty()) listaAsientos.add(s.trim());
            }
            String msg = eventoActual.registrarCompraGrupal(
                data.get("nombre"),
                Integer.parseInt(data.get("edad")),
                data.get("correo"),
                data.get("tipo"),
                listaAsientos
            );
            return jsonMensaje(true, msg);
        }, false));

        server.createContext("/api/pagar", exchange -> manejarApi(exchange, () -> {
            Map<String, String> data = parsearBody(leerBody(exchange));
            String msg = eventoActual.procesarPago(
                data.get("referencia"),
                Double.parseDouble(data.get("monto"))
            );
            return jsonMensaje(true, msg);
        }, false));

        server.createContext("/api/checkin", exchange -> manejarApi(exchange, () -> {
            Map<String, String> data = parsearBody(leerBody(exchange));
            String msg = eventoActual.registrarCheckInGrupal(data.get("referencia"));
            return jsonMensaje(true, msg);
        }, false));

        server.createContext("/api/boleto/actualizar", exchange -> manejarApi(exchange, () -> {
            Map<String, String> data = parsearBody(leerBody(exchange));
            String msg = eventoActual.actualizarBoleto(
                Integer.parseInt(data.get("idBoleto")),
                data.get("nombre"),
                Integer.parseInt(data.get("edad")),
                data.get("correo")
            );
            return jsonMensaje(true, msg);
        }, false));

        server.createContext("/api/boleto/eliminar", exchange -> manejarApi(exchange, () -> {
            Map<String, String> data = parsearBody(leerBody(exchange));
            String msg = eventoActual.eliminarBoleto(Integer.parseInt(data.get("idBoleto")));
            return jsonMensaje(true, msg);
        }, false));

        // Servir archivos estáticos Web (HTML, CSS, JS, Assets)
        server.createContext("/", ServidorWeb::servirArchivoEstatico);

        server.setExecutor(null);
        server.start();
        System.out.println("==========================================================");
        System.out.println(" Servidor EventGo Web Activo en: http://localhost:" + puerto);
        System.out.println(" Persistencia JSON: " + directorioPublico.getParent());
        System.out.println("==========================================================");
    }

    @FunctionalInterface
    interface AccionApi {
        String ejecutar() throws Exception;
    }

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
            String respuesta = accion.ejecutar();
            enviarRespuesta(exchange, 200, respuesta);
        } catch (Exception e) {
            enviarRespuesta(exchange, 400, jsonMensaje(false, e.getMessage() != null ? e.getMessage() : "Error procesando solicitud"));
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
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
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
        if (limpio.startsWith("{") && limpio.endsWith("}")) {
            limpio = limpio.substring(1, limpio.length() - 1);
        }
        // Divide por comas fuera de comillas
        List<String> pares = new ArrayList<>();
        boolean enComillas = false;
        StringBuilder actual = new StringBuilder();
        for (char c : limpio.toCharArray()) {
            if (c == '"') enComillas = !enComillas;
            if (c == ',' && !enComillas) {
                pares.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        if (actual.length() > 0) pares.add(actual.toString());

        for (String par : pares) {
            int sep = par.indexOf(':');
            if (sep > 0) {
                String k = par.substring(0, sep).trim().replace("\"", "");
                String v = par.substring(sep + 1).trim();
                if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                    v = v.substring(1, v.length() - 1);
                }
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
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}