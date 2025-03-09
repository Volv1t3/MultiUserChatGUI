package com.evolvlabs.multiuserchatgui.CommunicationBackend;

import com.evolvlabs.multiuserchatgui.ClientSideBackend.ClientPOJO;

import java.io.Serializable;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @description: El presente archivo muestra la implementacion de un Record (Data-Oriented
 * Programming) para la transferencia de informacion entre el
 * {@link com.evolvlabs.multiuserchatgui.ClientSideBackend.MessageClient } y el
 * {@link com.evolvlabs.multiuserchatgui.ServerSideBackend.MessageServer}. La idea es que esta
 * clases deben permitir el enviar informacion que sea recopilado dentro de la GUI en el cliente,
 * hacia el servidor de manera immutable. El servidor, para validar la existencia de un cliente
 * requiere su nombre de usuario y la contrasena que el usuario decide ponerle a su cuenta.
 * Internamente esto se valida y una respuesta booleana se espera para la llamada.
 * <br>
 * <br>
 * En este sentido, esta clase simplemente sirve como un puente de transporte de la informacion
 * para que no se vea afectado por terceros, ya que los Records garantizan la immutabilidad de
 * los datos.
 * <br><br>
 * Para garantizar que el trabajo de esta clase sea correcto, la clase utiliza el tipo de dato
 * ClientPOJO que se retorna del analisis de entrada de datos del usuario, de esta forma logramos
 * mantener una estructura normal sin desempaquetar los datos. No obstante, a la hora de acceder
 * se recomienda que se utilizen solo los metodos get definidos.
 * @date : 02-Mar-2025
 */
public record AuthenticationRequestDTO(ClientPOJO clientPOJO) implements Serializable {


    /*! Constructor Del Record*/

    /**
     * <body style="color:white">
     * Constructor de la clase record AuthenticationRequestDTO. Este constructor recibe como
     * parametro una instancia de {@link ClientPOJO} para encapsular los datos del cliente.
     *
     * @param clientPOJO Encapsula el estado del cliente, incluyendo su UUID, nombre de usuario y
     *                   contrasena en texto claro. Este objeto es impuesto por la GUI y contiene
     *                   dichos datos validados previamente.
     * @throws NullPointerException Si el parametro clientPOJO es nulo.
     *                              </body>
     */
    public AuthenticationRequestDTO(ClientPOJO clientPOJO) {
        this.clientPOJO = clientPOJO;
    }

    /*! Getters Autorizados*/


    /**
     * <body style="color:white">
     * Retorna el nombre de usuario del cliente. Este metodo utiliza el getter de
     * {@link ClientPOJO#get_clientUsername()} para acceder al dato encapsulado.
     *
     * @return Nombre de usuario del cliente como un String.
     * </body>
     */
    public String getClientUsername() {
        return this.clientPOJO.get_clientUsername();
    }

    /**
     * <body style="color:white">
     * Devuelve la contrasena en texto claro asociada al cliente. A pesar de que generalmente las
     * contrasenas estan encriptadas, este metodo permite acceso al texto sin formato por razones
     * especificas internas del sistema.
     *
     * @return Contrasena en texto claro como un String.
     * </body>
     */
    public String getClientClearPwd() {
        return this.clientPOJO.get_clientClearPassword();
    }

    /*! Overloads Requeridos*/

    /**
     * <body style="color:white">
     * Proporciona una representacion en String del objeto AuthenticationRequestDTO. Este metodo
     * delega la responsabilidad a {@link ClientPOJO#toString()}.
     *
     * @return Representacion en String del ClientPOJO encapsulado.
     * </body>
     */
    @Override
    public String toString() {
        return clientPOJO.toString();
    }

    /**
     * <body style="color:white">
     * Compara este AuthenticationRequestDTO con otro basado en el estado del {@link ClientPOJO}
     * encapsulado. Utiliza {@link ClientPOJO#compareTo(ClientPOJO)} para realizar la comparacion.
     *
     * @param other Otro objeto AuthenticationRequestDTO a comparar.
     * @return Un entero indicando si este objeto es menor, igual o mayor que el otro.
     * </body>
     */
    public int compareTo(AuthenticationRequestDTO other) {
        return this.clientPOJO.compareTo(other.clientPOJO);
    }

    /**
     * <body style="color:white">
     * Determina si este objeto AuthenticationRequestDTO es igual a otro objeto. La comparacion se
     * basa en los datos internos del {@link ClientPOJO} utilizando su metodo equals.
     *
     * @param obj El objeto a comparar.
     * @return {@code true} si los objetos son iguales; {@code false} en caso contrario.
     * </body>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        AuthenticationRequestDTO that = (AuthenticationRequestDTO) obj;
        return this.clientPOJO.equals(that.clientPOJO);
    }


    public record AuthenticationResponseDTO(boolean authenticationResponse,
                                            String clientUsername, String errorMessageIfAny) implements Serializable {


        /**
         * <body style="color:white">
         * Crea una instancia de AuthenticationResponseDTO indicando un exito en la autenticacion.
         * Este metodo de fabrica se asegura de establecer el UUID del cliente cuando la
         * autenticacion es exitosa, mientras que el mensaje de error es nulo.
         *
         * @return Una nueva instancia de AuthenticationResponseDTO con un valor booleano de exito,
         * el UUID del cliente asociado, y un mensaje de error nulo.
         * @throws NullPointerException Si el parametro clientUUID es nulo.
         *                              </body>
         */
        public static AuthenticationResponseDTO success(String clientUsername) {
            return new AuthenticationResponseDTO(true, clientUsername, null);
        }

        /**
         * <body style="color:white">
         * Crea una instancia de AuthenticationResponseDTO indicando un fallo en la autenticacion.
         * Este metodo de fabrica se asegura de proporcionar un mensaje de error detallo mientras el
         * UUID del cliente se mantiene como nulo.
         *
         * @param errorMessage Mensaje descriptivo del error ocurrido durante la autenticacion. Este
         *                     String deberia proporcionar detalles relevantes del fallo.
         * @return Una nueva instancia de AuthenticationResponseDTO con un valor booleano de fallo,
         * un UUID de cliente nulo, y el mensaje de error proporcionado.
         * @throws NullPointerException Si el parametro errorMessage es nulo.
         *                              </body>
         */
        public static AuthenticationResponseDTO failure(String errorMessage) {
            return new AuthenticationResponseDTO(false, null, errorMessage);
        }

    }
}

