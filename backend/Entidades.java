/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez | Matrícula: Al03039306
 * ========================================================================= */

interface Mostrable {
    String obtenerInformacion();
}

class Usuario {
    private String idUsuario;
    private String nombre;
    private String correo;
    private String rol; // ORGANIZADOR, TAQUILLA, ASISTENTE

    public Usuario(String idUsuario, String nombre, String correo, String rol) {
        this.idUsuario = idUsuario;
        this.nombre = nombre;
        this.correo = correo;
        this.rol = rol;
    }

    public String getIdUsuario() { return idUsuario; }
    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
    public String getRol() { return rol; }
}

class Cliente implements Mostrable {
    private String idCliente;
    private String nombre;
    private int edad;
    private String correo;
    private String telefono;

    public Cliente(String idCliente, String nombre, int edad, String correo, String telefono) {
        this.idCliente = idCliente;
        this.nombre = nombre;
        this.edad = edad;
        this.correo = correo;
        this.telefono = telefono == null ? "" : telefono;
    }

    public String getIdCliente() { return idCliente; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getEdad() { return edad; }
    public void setEdad(int edad) { this.edad = edad; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    @Override
    public String obtenerInformacion() {
        return nombre + " (" + edad + " años | " + correo + " | Tel: " + telefono + ")";
    }
}

class Invitado implements Mostrable {
    private String nombre;
    private int edad;
    private String correo;

    public Invitado(String nombre, int edad, String correo) {
        this.nombre = nombre;
        this.edad = edad;
        this.correo = correo;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public int getEdad() { return edad; }
    public void setEdad(int edad) { this.edad = edad; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }

    @Override
    public String obtenerInformacion() {
        return nombre + " (" + edad + " años | " + correo + ")";
    }
}