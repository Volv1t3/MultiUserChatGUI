package com.evolvlabs.multiuserchatgui.CommunicationBackend;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-2025
 * @description: El presente archivo es un Data Transfer Objects usado para transferir la 
 * informacion, del formato de la base de datos, hacia la UI, manteniendo la immutabilidad de los
 * datos durante el proceso. Usamos un {@link Record} de Java para representar a los datos de 
 * manera explicitamente immutable, proveyendo de getters para seguir el patron usadoe n las 
 * otras clases de datos.
 */
public record ClientDTO(String _clientUUID, String _clientUsername, String _clientPwdHash,
                        String _clientSaltHash) implements Comparable<ClientDTO>, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * <body style="color: white;">
     * Constructor de la clase ClientDTO. Este se utiliza para crear instancias inmutables de un
     * objeto que representa un cliente. Utiliza un concepto de datos inmutables al ser definido
     * como un
     * <code>record</code> en Java, garantizando que los valores asignados en el momento de la
     * construccion no puedan ser modificados posteriormente.
     *
     * @param _clientUUID     Identificador unico del cliente. No puede ser <code>null</code>.
     * @param _clientUsername Nombre de usuario del cliente. No puede ser <code>null</code>.
     * @param _clientPwdHash  Hash de la contrasena del cliente. Requiere que haya sido derivado
     *                        previamente con un algoritmo seguro. No puede ser <code>null</code>.
     * @param _clientSaltHash Salt utilizado para el hash de la contrasena. Requerido para
     *                        garantizar mayor seguridad en la derivacion de la clave. No puede ser
     *                        <code>null</code>.
     * @throws NullPointerException Si alguno de los parametros proporcionados es
     *                              <code>null</code>.
     *                              </body>
     */
    public ClientDTO(String _clientUUID, String _clientUsername, String _clientPwdHash,
                     String _clientSaltHash) {
        this._clientUUID = _clientUUID;
        this._clientUsername = _clientUsername;
        this._clientPwdHash = _clientPwdHash;
        this._clientSaltHash = _clientSaltHash;
    }

    /**
     * <body style="color: white;">
     * Obtiene el identificador unico del cliente.
     *
     * @return Una cadena con el identificador unico del cliente.
     * </body>
     */
    public String getClientUUID() {
        return _clientUUID;
    }

    /**
     * <body style="color: white;">
     * Obtiene el nombre de usuario del cliente.
     *
     * @return Una cadena con el nombre de usuario del cliente.
     * </body>
     */
    public String getClientUsername() {
        return _clientUsername;
    }

    /**
     * <body style="color: white;">
     * Obtiene el hash de la contrasena del cliente.
     *
     * @return Una cadena con el hash de la contrasena del cliente.
     * </body>
     */
    public String getClientPwdHash() {
        return _clientPwdHash;
    }

    /**
     * <body style="color: white;">
     * Obtiene el hash del salt utilizado para la derivacion de la contrasena.
     *
     * @return Una cadena con el hash del salt del cliente.
     * </body>
     */
    public String getClientSaltHash() {
        return _clientSaltHash;
    }

    /**
     * <body style="color: white;">
     * Convierte la instancia inmutable de <code>ClientDTO</code> en un arreglo de objetos
     * (<code>Object[]</code>), donde cada elemento representa un atributo de la instancia. El orden
     * de los elementos en el arreglo corresponde a:
     * <ul>
     *     <li>Identificador unico del cliente (_clientUUID).</li>
     *     <li>Nombre de usuario del cliente (_clientUsername).</li>
     *     <li>Hash de la contrasena del cliente (_clientPwdHash).</li>
     *     <li>Hash del salt (_clientSaltHash).</li>
     * </ul>
     *
     * @return Un arreglo de objetos (<code>Object[]</code>) que contiene los valores inmutables de
     * los atributos de la instancia en el orden mencionado.
     * @throws NullPointerException Si el record fue construido incorrectamente y uno de los valores
     *                              es <code>null</code>. Esto no deberia ocurrir bajo el contrato
     *                              de un record bien definido.
     *                              </body>
     */
    public Object[] toObjectArray() {
        return new Object[]{_clientUUID, _clientUsername, _clientPwdHash, _clientSaltHash};
    }

    /**
     * <body style="color: white;">
     * Retorna una representacion legible del objeto ClientDTO.
     *
     * @return Una cadena que describe los atributos del objeto.
     * </body>
     */
    @Override
    public String toString() {
        return "ClientDTO{" +
               "_clientUUID='" + _clientUUID + '\'' +
               ", _clientUsername='" + _clientUsername + '\'' +
               ", _clientPwdHash='" + _clientPwdHash + '\'' +
               ", _clientSaltHash='" + _clientSaltHash + '\'' +
               '}';
    }

    /**
     * <body style="color: white;">
     * Verifica si otro objeto es igual a esta instancia de ClientDTO. Dos objetos se consideran
     * iguales si tienen el mismo tipo y el mismo identificador unico (<code>_clientUUID</code>).
     *
     * @param o Objeto a comparar.
     * @return <code>true</code> si los objetos son iguales, <code>false</code> en caso contrario.
     * </body>
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientDTO clientDTO = (ClientDTO) o;
        return _clientUUID.equals(clientDTO._clientUUID);
    }

    /**
     * <body style="color: white;">
     * Compara dos instancias de ClientDTO basado en el nombre de usuario
     * (<code>_clientUsername</code>), utilizando la logica lexicografica de
     * <code>String.compareTo()</code>.
     *
     * @param o Objeto ClientDTO a comparar.
     * @return Un valor negativo, cero, o positivo si el nombre de usuario de esta instancia es
     * lexicograficamente menor, igual, o mayor que el de la otra instancia.
     * </body>
     */
    @Override
    public int compareTo(ClientDTO o) {
        return this._clientUsername.compareTo(o._clientUsername);
    }
    

}
