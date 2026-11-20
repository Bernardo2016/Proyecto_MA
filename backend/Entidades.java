/* =========================================================================
 * Proyecto: EventGo - Sistema Centralizado de Gestión de Eventos
 * Autores:
 *  - Bernardo Velasco Ortega | Matrícula: AL07127004
 *  - Andrea Lizeth Sanchez Gomez
 * ========================================================================= */

interface Mostrable {
    String obtenerInformacion();
}

class Usuario {
    private String nombre;
    private String correo;

    public Usuario(String nombre, String correo) {
        this.nombre = nombre;
        this.correo = correo;
    }

    public String getNombre() { return nombre; }
    public String getCorreo() { return correo; }
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