package com.evolvlabs.multiuserchatgui.CommunicationBackend;

import java.io.Serializable;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 02-Mar-2025
 * @description: El presente archivo incluye uno de los ultimos DTOs que se requieren para la
 * comunicacion entre el MessageClient y el MessageServer, el DisconnectRequestInfo, que contiene
 * los datos de la conexion que se va a terminar. Cuando el usuario cierra su pestana de chat, no
 * es que su objeto se destruye y nada mas, sino que internamente su sistema handler
 * (ClientHandler) dentro del servidor, tiene que desconectarse, interrumpir su ejecucion y
 * terminar su lifetime para que los recursos se limpien. Para esto tenemos que manejar una senal
 * de desconexion enviada hacia el MessageClient (que crea esta instancia) y cuya informacion se
 * transfiere al ClientServer (recibe esta instancia y busca basandonos en el UUID), con el cual
 *  logra encontrar dentro de un arreglo de ClientHolders el que se tiene que eliminar y elimina
 *  la conexion luego de asegurarse que el estado de la aplicacion se mantenga correcto.
 * @param _exUserUsername
 * @param _exUserUUID
 */
public record DisconnectionRequestInfoDTO(String _exUserUsername, String _exUserUUID)
        implements Comparable<DisconnectionRequestInfoDTO>, Serializable {

    public DisconnectionRequestInfoDTO(String _exUserUsername, String _exUserUUID){
        this._exUserUsername = _exUserUsername;
        this._exUserUUID = _exUserUUID;
    }

    /**
     * <body style="color: white;">
     * Obtiene el nombre de usuario del cliente que realiza la solicitud de desconexion.
     *
     * @return Una cadena con el nombre de usuario del cliente.
     * </body>
     */
    public String get_exUserUsername() {
        return _exUserUsername;
    }

    /**
     * <body style="color: white;">
     * Obtiene el identificador unico universal (UUID) del cliente que realiza la solicitud de
     * desconexion.
     *
     * @return Una cadena con el identificador UUID del cliente.
     * </body>
     */
    public String get_exUserUUID() {
        return _exUserUUID;
    }

    /**
     * <body style="color: white;">
     * Compara esta instancia de DisconnectionRequestInfoDTO con otra basada en el nombre de usuario
     * (<code>_exUserUsername</code>), siguiendo el orden lexicografico alfabeto.
     *
     * @param o Objeto DisconnectionRequestInfoDTO a comparar.
     * @return Un valor negativo, cero o positivo si el nombre de usuario de esta instancia es
     * lexicograficamente menor, igual o mayor en comparacion con el nombre de usuario de la otra
     * instancia.
     * </body>
     */
    public int compareTo(DisconnectionRequestInfoDTO o) {
        if (o != null){
            return this._exUserUsername.compareTo(o._exUserUsername);
        }
        throw new NullPointerException("Error Code 0x002 - [Raised] La segunda instancia enviada " +
                                               "a comparacion en DisconnectionRequestInfoDTO es " +
                                               "nula");
    }

    /**
     * <body style="color: white;">
     * Devuelve una representacion legible de la instancia DisconnectionRequestInfoDTO. Incluye el
     * nombre de usuario y el UUID.
     *
     * @return Una cadena que describe los atributos de la solicitud de desconexion.
     * </body>
     */
    @Override
    public String toString() {
        return "DisconnectionRequestInfoDTO{" +
                "_exUserUsername='" + _exUserUsername + '\'' +
                ", _exUserUUID='" + _exUserUUID + '\'' +
                '}';
    }
}
