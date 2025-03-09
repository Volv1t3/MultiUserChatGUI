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

    /**
     * <body style="color: white;">
     * Constructor para crear una instancia de la clase <code>DisconnectionRequestInfoDTO</code>.
     * Recibe el nombre de usuario y el UUID unico del cliente que solicita la desconexion.
     * <p>
     * Este constructor inicializa los dos atributos de la clase:
     * <ul>
     *     <li><code>_exUserUsername</code>: Nombre del usuario que solicita la desconexion.</li>
     *     <li><code>_exUserUUID</code>: Un identificador unico de usuario para localizar al cliente.</li>
     * </ul>
     * <p>
     * Utilidad del constructor:
     * Este metodo se invoca en el cliente para crear una representacion de los datos necesarios
     * para completar la desconexion en el servidor. Una vez instanciado, el objeto es transferido
     * al MessageServer que ubica y elimina la conexion del cliente correspondiente.
     *
     * @param _exUserUsername Nombre del usuario solicitando la desconexion. No debe ser nulo.
     * @param _exUserUUID     Identificador unico del cliente solicitado para desconectarse. No debe
     *                        ser nulo.
     * @throws NullPointerException Si cualquiera de los parametros es <code>null</code>.
     *                              </body>
     */
    public DisconnectionRequestInfoDTO(String _exUserUsername, String _exUserUUID) {
        if (_exUserUsername == null || _exUserUUID == null) {
            throw new NullPointerException("Error Code 0x001 - Uno o ambos parametros del constructor son nulos");
        }
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
