package com.evolvlabs.multiuserchatgui.CommunicationBackend;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 02-Mar-2025
 * @description: El presente archivo permite al sistema manejar un DTO, data transfer object para
 * que la informacion de la conexion se transfiera de manera segura entre servidor y cliente. La
 * idea de esta clase es que, como se va a servir a muchas conexiones simultaneas, el sistema
 * pueda manejar a las mismas mediante los datos de conexion abstraidos dentro de esta clase. En
 * este sentido, los parametros de este record reflejan la informacion requerida para inicializar
 * una conexion, es decir los datos que manejan los hilos de ejecucion internos, username y UUID,
 * ya que los datos de los sockets se obtienen a traves del servidor TCP.
 */
public record ConnectionRequestInfoDTO(String _exUserUsername)
        implements Comparable<ConnectionRequestInfoDTO>, Serializable {

    /*! Constructor*/
    /**
     * <body style="color: white;">
     * Constructor de la clase ConnectionRequestInfoDTO. Este constructor permite crear una
     * instancia inmutable que representa los datos esenciales de una solicitud de conexion.
     * @param _exUserUsername Nombre de usuario del cliente. Este debe ser una cadena no nula que
     *                        representa el identificador util para distinguir la sesion del
     *                        usuario.
     * @throws NullPointerException Si alguno de los parametros proporcionados es
     *                              <code>null</code>.
     *                              </body>
     */
    public ConnectionRequestInfoDTO(String _exUserUsername) {
        this._exUserUsername = _exUserUsername;
    }

    /*! Getters Autorizados*/

    /**
     * <body style="color: white;">
     * Obtiene el nombre de usuario del cliente que realiza la solicitud de conexion.
     *
     * @return Una cadena que representa el nombre del usuario asociado a esta solicitud.
     * </body>
     */
    public String get_exUserUsername() {
        return _exUserUsername;
    }



    /*! Overloads Requeridos*/

    /**
     * <body style="color: white;">
     * Devuelve una representacion legible de la instancia ConnectionRequestInfoDTO. Incluye el
     * nombre de usuario y el UUID.
     *
     * @return Una cadena que describe los atributos de la solicitud de conexion.
     * </body>
     */
    @Override
    public String toString() {
        return "ConnectionRequestInfoDTO{" +
                "_exUserUsername='" + _exUserUsername + '\'' +
                '}';
    }

    /**
     * <body style="color: white;">
     * Verifica si esta instancia de ConnectionRequestInfoDTO es igual a otra. Dos objetos se
     * consideran iguales si tienen el mismo nombre de usuario (<code>_exUserUsername</code>) y el
     * mismo identificador UUID (<code>_exUserUUID</code>).
     *
     * @param obj Objeto a comparar con esta instancia.
     * @return <code>true</code> si ambos objetos son iguales, <code>false</code> en caso contrario.
     * </body>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ConnectionRequestInfoDTO that = (ConnectionRequestInfoDTO) obj;
        return Objects.equals(_exUserUsername, that._exUserUsername);
    }

    /**
     * <body style="color: white;">
     * Compara esta instancia de ConnectionRequestInfoDTO con otra basada en el nombre de usuario
     * (<code>_exUserUsername</code>), siguiendo el orden lexicografico alfabeto.
     *
     * @param o Objeto ConnectionRequestInfoDTO a comparar.
     * @return Un valor negativo, cero o positivo si el nombre de usuario de esta instancia es
     * lexicograficamente menor, igual o mayor en comparacion con el nombre de usuario de la otra
     * instancia.
     * </body>
     */
    @Override
    public int compareTo(ConnectionRequestInfoDTO o) {
        return this._exUserUsername.compareTo(o._exUserUsername);
    }
}
