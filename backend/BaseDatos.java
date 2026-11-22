/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez | Matrícula: Al03039306
 * Descripción: Persistencia relacional en JSON para Roles, Clientes y Eventos.
 * ========================================================================= */

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BaseDatos {
    private Path rutaArchivo;
    private List<Usuario> usuarios;
    private List<Cliente> clientes;
    private List<Evento> eventos;
    private int seqEvento = 1;
    private int seqCliente = 1;

    public BaseDatos(Path rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
        this.usuarios = new ArrayList<>();
        this.clientes = new ArrayList<>();
        this.eventos = new ArrayList<>();

        if (!cargar()) {
            precargarDatosIniciales();
            guardar();
        }
    }

    private void precargarDatosIniciales() {
        // 1. Tabla de Roles y Usuarios del Sistema
        usuarios.add(new Usuario("USR-1", "Bernardo Velasco", "bernardo@eventgo.mx", "ORGANIZADOR"));
        usuarios.add(new Usuario("USR-2", "Andrea Sanchez", "andrea@eventgo.mx", "TAQUILLA"));
        usuarios.add(new Usuario("USR-3", "Público General", "asistente@eventgo.mx", "ASISTENTE"));

        // 2. Tabla de Clientes Precargados
        clientes.add(new Cliente("CLI-1", "Carlos Mendoza", 24, "carlos.mendoza@correo.com", "55-1234-5678"));
        clientes.add(new Cliente("CLI-2", "Sofía Ramírez", 22, "sofia.ramirez@correo.com", "55-8765-4321"));
        clientes.add(new Cliente("CLI-3", "Diego Torres", 27, "diego.torres@correo.com", "55-4433-2211"));
        clientes.add(new Cliente("CLI-4", "Valeria Cruz", 21, "valeria.cruz@correo.com", "55-9988-7766"));
        seqCliente = 5;

        // 3. Múltiples Eventos Precargados con Operaciones Demo
        Evento ev1 = new Evento("EVT-1", "Concierto Sinfónico Tecmi 2026", "2026-11-28 20:00",
                "Auditorio Principal Campus", "Concierto", 6, 8, 2, 500.0, 1500.0);
        ev1.registrarCompraGrupal("Carlos Mendoza", 24, "carlos.mendoza@correo.com", "VIP", Arrays.asList("A-3", "A-4", "A-5"));
        ev1.procesarPago("GRP-1", 5000.0);
        ev1.registrarCheckInGrupal("GRP-1");
        ev1.registrarCompraGrupal("Sofía Ramírez", 22, "sofia.ramirez@correo.com", "GENERAL", Arrays.asList("D-2", "D-3"));
        eventos.add(ev1);

        Evento ev2 = new Evento("EVT-2", "Cumbre de Ciberseguridad & Cloud", "2026-12-05 10:00",
                "Centro de Convenciones CDMX", "Conferencia", 5, 10, 2, 350.0, 950.0);
        ev2.registrarCompraGrupal("Diego Torres", 27, "diego.torres@correo.com", "VIP", Arrays.asList("B-5", "B-6"));
        ev2.procesarPago("GRP-1", 2000.0);
        eventos.add(ev2);

        Evento ev3 = new Evento("EVT-3", "Noche de Stand-Up & Comedia", "2026-12-12 21:00",
                "Teatro Universitario", "Entretenimiento", 7, 8, 2, 250.0, 600.0);
        eventos.add(ev3);

        seqEvento = 4;
    }

    public synchronized Evento buscarEvento(String idEvento) {
        for (Evento e : eventos) if (e.getIdEvento().equalsIgnoreCase(idEvento)) return e;
        return eventos.isEmpty() ? null : eventos.get(0);
    }

    public synchronized String crearEvento(String nombre, String fecha, String recinto, String categoria,
                                           int filas, int cols, int filasVIP, double cGen, double cVip) {
        String id = "EVT-" + seqEvento++;
        Evento nuevo = new Evento(id, nombre, fecha, recinto, categoria, filas, cols, filasVIP, cGen, cVip);
        eventos.add(nuevo);
        guardar();
        return "Evento '" + nombre + "' creado con ID " + id + ".";
    }

    public synchronized String eliminarEvento(String idEvento) {
        if (eventos.size() <= 1) throw new IllegalArgumentException("Debe existir al menos un evento activo en el sistema.");
        Evento e = buscarEvento(idEvento);
        if (e == null || !e.getIdEvento().equalsIgnoreCase(idEvento)) throw new IllegalArgumentException("Evento no encontrado.");
        eventos.remove(e);
        guardar();
        return "Evento '" + e.getNombreEvento() + "' eliminado.";
    }

    public synchronized String registrarVenta(String idEvento, String nombre, int edad, String correo, String telefono, String tipo, List<String> asientos) {
        Evento ev = buscarEvento(idEvento);
        if (ev == null) throw new IllegalArgumentException("Evento no encontrado.");
        String res = ev.registrarCompraGrupal(nombre, edad, correo, tipo, asientos);
        registrarOActualizarClienteAutomatico(nombre, edad, correo, telefono);
        guardar();
        return res;
    }

    private void registrarOActualizarClienteAutomatico(String nombre, int edad, String correo, String telefono) {
        for (Cliente c : clientes) {
            if (c.getCorreo().equalsIgnoreCase(correo.trim())) {
                c.setNombre(nombre.trim());
                c.setEdad(edad);
                if (telefono != null && !telefono.trim().isEmpty()) c.setTelefono(telefono.trim());
                return;
            }
        }
        clientes.add(new Cliente("CLI-" + seqCliente++, nombre.trim(), edad, correo.trim(), telefono));
    }

    // CRUD CLIENTES
    public synchronized String guardarClienteManual(String idCliente, String nombre, int edad, String correo, String telefono) {
        if (nombre == null || nombre.trim().isEmpty()) throw new IllegalArgumentException("El nombre del cliente es obligatorio.");
        if (edad < 12 || edad > 110) throw new IllegalArgumentException("Edad inválida (12 a 110).");
        if (correo == null || !correo.contains("@")) throw new IllegalArgumentException("Correo electrónico inválido.");

        if (idCliente != null && !idCliente.trim().isEmpty()) {
            for (Cliente c : clientes) {
                if (c.getIdCliente().equalsIgnoreCase(idCliente.trim())) {
                    c.setNombre(nombre.trim());
                    c.setEdad(edad);
                    c.setCorreo(correo.trim());
                    c.setTelefono(telefono == null ? "" : telefono.trim());
                    guardar();
                    return "Cliente " + c.getIdCliente() + " actualizado.";
                }
            }
        }
        String nuevoId = "CLI-" + seqCliente++;
        clientes.add(new Cliente(nuevoId, nombre.trim(), edad, correo.trim(), telefono == null ? "" : telefono.trim()));
        guardar();
        return "Cliente registrado con ID " + nuevoId + ".";
    }

    public synchronized String eliminarCliente(String idCliente) {
        Cliente encontrado = null;
        for (Cliente c : clientes) if (c.getIdCliente().equalsIgnoreCase(idCliente)) encontrado = c;
        if (encontrado == null) throw new IllegalArgumentException("Cliente no encontrado.");
        clientes.remove(encontrado);
        guardar();
        return "Cliente " + encontrado.getNombre() + " eliminado del directorio.";
    }

    public synchronized String procesarPago(String idEvento, String referencia, double monto) {
        Evento ev = buscarEvento(idEvento);
        String res = ev.procesarPago(referencia, monto);
        guardar();
        return res;
    }

    public synchronized String registrarCheckIn(String idEvento, String referencia) {
        Evento ev = buscarEvento(idEvento);
        String res = ev.registrarCheckInGrupal(referencia);
        guardar();
        return res;
    }

    public synchronized String actualizarBoleto(String idEvento, int idBoleto, String nombre, int edad, String correo) {
        Evento ev = buscarEvento(idEvento);
        String res = ev.actualizarBoleto(idBoleto, nombre, edad, correo);
        guardar();
        return res;
    }

    public synchronized String eliminarBoleto(String idEvento, int idBoleto) {
        Evento ev = buscarEvento(idEvento);
        String res = ev.eliminarBoleto(idBoleto);
        guardar();
        return res;
    }

    public synchronized String exportarTodoJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"usuarios\": [");
        for (int i = 0; i < usuarios.size(); i++) {
            Usuario u = usuarios.get(i);
            sb.append("{\"idUsuario\":\"").append(escapar(u.getIdUsuario())).append("\",")
              .append("\"nombre\":\"").append(escapar(u.getNombre())).append("\",")
              .append("\"correo\":\"").append(escapar(u.getCorreo())).append("\",")
              .append("\"rol\":\"").append(escapar(u.getRol())).append("\"}");
            if (i < usuarios.size() - 1) sb.append(",");
        }
        sb.append("],\n");

        sb.append("  \"clientes\": [");
        for (int i = 0; i < clientes.size(); i++) {
            Cliente c = clientes.get(i);
            int boletosTotales = 0;
            for (Evento ev : eventos) {
                for (Boleto b : ev.getListaBoletos()) {
                    if (b.getInvitado().getCorreo().equalsIgnoreCase(c.getCorreo())) boletosTotales++;
                }
            }
            sb.append("{\"idCliente\":\"").append(escapar(c.getIdCliente())).append("\",")
              .append("\"nombre\":\"").append(escapar(c.getNombre())).append("\",")
              .append("\"edad\":").append(c.getEdad()).append(",")
              .append("\"correo\":\"").append(escapar(c.getCorreo())).append("\",")
              .append("\"telefono\":\"").append(escapar(c.getTelefono())).append("\",")
              .append("\"boletosHistorial\":").append(boletosTotales).append("}");
            if (i < clientes.size() - 1) sb.append(",");
        }
        sb.append("],\n");

        sb.append("  \"eventos\": [\n");
        for (int i = 0; i < eventos.size(); i++) {
            sb.append("    ").append(eventos.get(i).toJson());
            if (i < eventos.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}");
        return sb.toString();
    }

    private void guardar() {
        try {
            Files.write(rutaArchivo, exportarTodoJson().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            System.err.println("Error al persistir base de datos: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private boolean cargar() {
        try {
            File f = rutaArchivo.toFile();
            if (!f.exists()) return false;
            String raw = new String(Files.readAllBytes(rutaArchivo), StandardCharsets.UTF_8);
            Map<String, Object> root = (Map<String, Object>) new JsonParser(raw).parseValue();

            List<Object> uList = (List<Object>) root.get("usuarios");
            if (uList != null) {
                for (Object o : uList) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    usuarios.add(new Usuario((String) m.get("idUsuario"), (String) m.get("nombre"),
                            (String) m.get("correo"), (String) m.get("rol")));
                }
            }

            List<Object> cList = (List<Object>) root.get("clientes");
            if (cList != null) {
                for (Object o : cList) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    String idC = (String) m.get("idCliente");
                    clientes.add(new Cliente(idC, (String) m.get("nombre"),
                            ((Double) m.get("edad")).intValue(), (String) m.get("correo"),
                            (String) m.get("telefono")));
                    try {
                        int n = Integer.parseInt(idC.replace("CLI-", ""));
                        if (n >= seqCliente) seqCliente = n + 1;
                    } catch (Exception ignored) {}
                }
            }

            List<Object> eList = (List<Object>) root.get("eventos");
            if (eList != null) {
                for (Object o : eList) {
                    Map<String, Object> m = (Map<String, Object>) o;
                    String idE = (String) m.get("idEvento");
                    Evento ev = new Evento(
                        idE,
                        (String) m.get("nombreEvento"),
                        (String) m.get("fechaEvento"),
                        (String) m.get("recinto"),
                        (String) m.get("categoria"),
                        ((Double) m.get("numFilas")).intValue(),
                        ((Double) m.get("numColumnas")).intValue(),
                        ((Double) m.get("numFilasVIP")).intValue(),
                        (Double) m.get("costoGeneral"),
                        (Double) m.get("costoVIP")
                    );
                    List<Object> bList = (List<Object>) m.get("boletos");
                    if (bList != null) {
                        for (Object bo : bList) {
                            Map<String, Object> bm = (Map<String, Object>) bo;
                            ev.restaurarBoletoDesdeBD(
                                ((Double) bm.get("idBoleto")).intValue(),
                                (String) bm.get("idGrupo"),
                                (String) bm.get("tipo"),
                                (String) bm.get("asientoId"),
                                (String) bm.get("nombre"),
                                ((Double) bm.get("edad")).intValue(),
                                (String) bm.get("correo"),
                                (Double) bm.get("precio"),
                                (Boolean) bm.get("pagado"),
                                (Boolean) bm.get("yaEntro"),
                                (String) bm.get("fechaVenta")
                            );
                        }
                    }
                    eventos.add(ev);
                    try {
                        int n = Integer.parseInt(idE.replace("EVT-", ""));
                        if (n >= seqEvento) seqEvento = n + 1;
                    } catch (Exception ignored) {}
                }
            }
            return !eventos.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public static String escapar(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ").replace("\r", "");
    }

    // Parser JSON nativo ligero sin dependencias externas
    private static class JsonParser {
        private final String s;
        private int pos = 0;

        JsonParser(String s) { this.s = s; }

        Object parseValue() {
            skipWs();
            if (pos >= s.length()) return null;
            char c = s.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't') { pos += 4; return Boolean.TRUE; }
            if (c == 'f') { pos += 5; return Boolean.FALSE; }
            if (c == 'n') { pos += 4; return null; }
            return parseNumber();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWs();
            if (s.charAt(pos) == '}') { pos++; return map; }
            while (pos < s.length()) {
                skipWs();
                String key = parseString();
                skipWs();
                pos++; // ':'
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                if (s.charAt(pos) == '}') { pos++; break; }
                pos++; // ','
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWs();
            if (s.charAt(pos) == ']') { pos++; return list; }
            while (pos < s.length()) {
                list.add(parseValue());
                skipWs();
                if (s.charAt(pos) == ']') { pos++; break; }
                pos++; // ','
            }
            return list;
        }

        String parseString() {
            pos++; // '"'
            StringBuilder sb = new StringBuilder();
            while (pos < s.length()) {
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < s.length()) {
                    char next = s.charAt(pos++);
                    sb.append(next);
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Double parseNumber() {
            int start = pos;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.' || s.charAt(pos) == '-')) pos++;
            return Double.parseDouble(s.substring(start, pos));
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }
    }
}