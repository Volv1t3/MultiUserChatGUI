package com.evolvlabs.multiuserchatgui.ClientSideBackend;

import com.evolvlabs.multiuserchatgui.CommunicationBackend.ClientDTO;
import com.evolvlabs.multiuserchatgui.ServerSideBackend.AuthenticatorEngine;
import com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine.EncryptionEngine;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Objects;


/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-25
 * @description: Este archivo contiene la clase base del backend relacionadol con el Cliente,
 * presentando una clase POJO (Plain Old Java Object) que puede ser utilizada para representar un
 * cliente dentro del sistema. La clase presenta mecanismos de construccion manual, serializacion
 * a SQL Query y construccion con base en un ResultSet.
 */
public class ClientPOJO implements Comparable<ClientPOJO>, Serializable {

    /*! Parametros Internos*/
    /**
     * String representativa del UUID del usuario, que se calcula en una clase API para seguridad
     * informatica de los datos del cliente.
     */
    private String _clientUUID;
    /**
     * String representativa del nombre de usuario del cliente, que se establece en la creacion del
     * objeto.
     */
    private String _clientUsername;
    /**
     * String representativa de la contrasena en texto plano del cliente, que se establece en la
     * creacion del objeto. Este password no es el mismo que se guarda en la base de datos. Este
     * es simplemente un password que permite al sistema guardar la informacion de la UI en su
     * camino hacia el sistema original.
     */
    private String _clientClearPassword;

    /*! Constructors*/

    /**
     * <body style="color:white">
     * Constructor que inicializa una instancia de la clase ClientPOJO, permitiendo establecer el
     * nombre de usuario y la contrasena en texto plano del cliente. Este constructor utiliza los
     * metodos {@link #set_clientUsername(String)} y {@link #set_clientClearPassword(String)} para
     * garantizar que los parametros establecidos cumplan con las validaciones estipuladas.
     *
     * @param clientUsername      El nombre de usuario del cliente. Debe ser no nulo, no vacio, y
     *                            con un maximo de 30 caracteres. Si no se cumplen estas
     *                            condiciones, se lanza una excepcion.
     * @param clientClearPassword La contrasena en texto plano del cliente. Debe ser no nula. Si el
     *                            parametro es nulo, se lanza una excepcion.
     * @throws IllegalArgumentException Si el nombre de usuario excede los 30 caracteres.
     * @throws NullPointerException     Si el nombre de usuario es nulo o vacio, o si la contrasena
     *                                  es nula.
     *                                  </body>
     */
    public ClientPOJO(String clientUsername, String clientClearPassword)
            throws IllegalArgumentException, NullPointerException{
        this.set_clientUsername(clientUsername);
        this.set_clientClearPassword(clientClearPassword);
        //? Generar UUID
        AuthenticatorEngine.getUsernameUUID(this._clientUsername);
    }



    public ClientPOJO(){;}

    /*! Getters and Setters*/

    /**
     * <body style="color:white">
     * Retorna el UUID unico del cliente.
     *
     * @return Un String que representa el UUID del cliente.
     * </body>
     */
    public String get_clientUUID() {
        if (this._clientUUID == null){
            this._clientUUID = AuthenticatorEngine.getUsernameUUID(this._clientUsername);
        }
        return this._clientUUID;
    }

    /**
     * <body style="color:white">
     * Establece el UUID del cliente mediante una cadena de texto proporcionada. Este metodo realiza
     * una validacion para asegurar que el UUID no sea nulo ni vacio y, si supera la validacion,
     * utiliza la clase {@link AuthenticatorEngine} para generar un UUID valido basado en el
     * parametro recibido.
     *
     * @param clientUUID El UUID proporcionado como cadena de texto. Debe ser no nulo y no vacio.
     * @throws NullPointerException Si el parametro clientUUID es nulo o vacio.
     *                              </body>
     */
    public void set_clientUUID(String clientUUID) throws NullPointerException {
        if (!clientUUID.isEmpty() && clientUUID != null) {
            this._clientUUID = AuthenticatorEngine.getUsernameUUID(clientUUID);
        } else {
            throw new NullPointerException("Error Code 0x002 - [Raised] Parametro clientUUID no puede " +
                                           "ser nulo o vacio.");
        }
    }

    /**
     * <body style="color:white">
     * Retorna el nombre de usuario del cliente.
     *
     * @return Un String que contiene el nombre de usuario del cliente.
     * </body>
     */
    public String get_clientUsername() {
        return this._clientUsername;
    }

    /**
     * <body style="color:white">
     * Establece el nombre de usuario para el cliente. Este metodo verifica que el parametro no sea
     * nulo o vacio y que no exceda los 30 caracteres. Si no se cumplen estas condiciones, lanza una
     * excepcion.
     *
     * @param clientUsername El nuevo nombre de usuario para el cliente (no debe ser nulo, vacio o
     *                       mayor de 30 caracteres).
     * @throws IllegalArgumentException Si el nombre de usuario excede los 30 caracteres.
     * @throws NullPointerException     Si el nombre de usuario es nulo o vacio.
     *                                  </body>
     */
    public void set_clientUsername(String clientUsername) throws IllegalArgumentException, NullPointerException {
        if (!clientUsername.isEmpty() && clientUsername != null) {
            if (clientUsername.length() <= 30) {
                this._clientUsername = clientUsername;
            } else {
                throw new IllegalArgumentException("Error Code 0x002 - [Raised] Parametro " +
                                                           "clientUsername no puede ser mayor de 30 " +
                                                           "caracteres en longitud");
            }
        } else {
            throw new NullPointerException("Error Code 0x002 - [Raised] Parametro clientUsername" +
                                                   " no puede ser nulo o vacio.");
        }
    }

    /**
     * <body style="color:white">
     * Retorna la contrasena en texto plano del cliente.
     *
     * @return Un String que contiene la contrasena en texto plano del cliente.
     * </body>
     */
    public String get_clientClearPassword() {
        return this._clientClearPassword;
    }

    /**
     * <body style="color:white">
     * Establece la contrasena en texto plano para el cliente. Este metodo verifica que el parametro
     * no sea nulo. Si el parametro es nulo, lanza una excepcion.
     *
     * @param clientClearPassword La nueva contrasena en texto plano para el cliente.
     * @throws NullPointerException Si la contrasena es nula.
     *                              </body>
     */
    public void set_clientClearPassword(String clientClearPassword) {
        if (clientClearPassword != null) {
            this._clientClearPassword = clientClearPassword;
        } else {
            throw new NullPointerException("Error Code 0x002 - [Raised] Parametro " +
                                                   "clientClearPassword no puede ser nulo.");
        }
    }


    /**
     * <body style="color:white">
     * Este metodo se utiliza para transformar una instancia de {@link ClientPOJO} en una instancia
     * inmutaqe de {@link ClientDTO}, asegurando que los datos del cliente se procesen y aseguren
     * adecuadamente antes de pasarlos a la capa de comunicacion.
        <br><br>
     * El metodo funciona invocando {@link AuthenticatorEngine#encryptProvidedClearPassword(String)},
     * que recibe la contrasena limpia y devuelve un objeto con el hash y el salt en formato Base64.
     * Este hash, junto con los demas atributos del cliente, se utiliza para crear la instancia de
     * {@link ClientDTO}.
     *
     * @return Un objeto de tipo {@link ClientDTO} que contiene los datos inmutables del cliente,
     * incluyendo su UUID, nombre de usuario, hash de la contrasena en Base64 y hash de su salt en
     * Base64.
     * </body>
     */
    public final ClientDTO transformIntoDTO() {
        EncryptionEngine.HashedPasswordDTO resultFromHashing =
                AuthenticatorEngine.encryptProvidedClearPassword(this._clientClearPassword);
        this.set_clientUUID(AuthenticatorEngine.getUsernameUUID(this.get_clientUsername()));
        return new ClientDTO(this._clientUUID,
                             this._clientUsername,
                             resultFromHashing._base64PWDHash(),
                             resultFromHashing._base64SaltingHash()
        );
    }

    /*! Overwrites para este tipo de objetos*/

    /**
     * {@return a string representation of the object}
     *
     * @apiNote In general, the {@code toString} method returns a string that "textually represents"
     * this object. The result should be a concise but informative representation that is easy for a
     * person to read. It is recommended that all subclasses override this method. The string output
     * is not necessarily stable over time or across JVM invocations.
     * @implSpec The {@code toString} method for class {@code Object} returns a string consisting of
     * the name of the class of which the object is an instance, the at-sign character `{@code @}',
     * and the unsigned hexadecimal representation of the hash code of the object. In other words,
     * this method returns a string equal to the value of:
     * {@snippet lang = java:
     * getClass().getName() + '@' + Integer.toHexString(hashCode())
     *} The {@link Objects#toIdentityString(Object) Objects.toIdentityString} method returns the
     * string for an object equal to the string that would be returned if neither the
     * {@code toString} nor {@code hashCode} methods were overridden by the object's class.
     */
    @Override
    public String toString() {
        return "ClientPOJO{" +
               "_clientUUID='" + _clientUUID + '\'' + "\n" +
               ", _clientUsername='" + _clientUsername + '\'' + "\n" +
               ", _clientClearPassword='" + _clientClearPassword + '\'' +
               '}';
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p>
     * The {@code equals} method implements an equivalence relation on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value
     *     {@code x}, {@code x.equals(x)} should return
     *     {@code true}.
     * <li>It is <i>symmetric</i>: for any non-null reference values
     *     {@code x} and {@code y}, {@code x.equals(y)}
     *     should return {@code true} if and only if
     *     {@code y.equals(x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any non-null reference values
     *     {@code x}, {@code y}, and {@code z}, if
     *     {@code x.equals(y)} returns {@code true} and
     *     {@code y.equals(z)} returns {@code true}, then
     *     {@code x.equals(z)} should return {@code true}.
     * <li>It is <i>consistent</i>: for any non-null reference values
     *     {@code x} and {@code y}, multiple invocations of
     *     {@code x.equals(y)} consistently return {@code true}
     *     or consistently return {@code false}, provided no
     *     information used in {@code equals} comparisons on the
     *     objects is modified.
     * <li>For any non-null reference value {@code x},
     *     {@code x.equals(null)} should return {@code false}.
     * </ul>
     *
     * <p>
     * An equivalence relation partitions the elements it operates on
     * into <i>equivalence classes</i>; all the members of an
     * equivalence class are equal to each other. Members of an
     * equivalence class are substitutable for each other, at least
     * for some purposes.
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj argument; {@code false} otherwise.
     * @implSpec The {@code equals} method for class {@code Object} implements the most
     * discriminating possible equivalence relation on objects; that is, for any non-null reference
     * values {@code x} and {@code y}, this method returns {@code true} if and only if {@code x} and
     * {@code y} refer to the same object ({@code x == y} has the value {@code true}).
     * <p>
     * In other words, under the reference equality equivalence relation, each equivalence class
     * only has a single element.
     * @apiNote It is generally necessary to override the {@link #hashCode() hashCode} method
     * whenever this method is overridden, so as to maintain the general contract for the
     * {@code hashCode} method, which states that equal objects must have equal hash codes.
     * <p>The two-argument {@link Objects#equals(Object,
     * Object) Objects.equals} method implements an equivalence relation on two possibly-null object
     * references.
     * @see #hashCode()
     * @see HashMap
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null  || !obj.getClass().equals(this.getClass())){
            return false;
        } else if (obj == this){
            return true;
        } else {
            ClientPOJO other = (ClientPOJO) obj;
            return this._clientUsername.equals(other._clientUsername) &&
                    this._clientClearPassword.equals(other._clientClearPassword);
        }
    }

    /**
     * Compares this object with the specified object for order.  Returns a negative integer, zero,
     * or a positive integer as this object is less than, equal to, or greater than the specified
     * object.
     *
     * <p>The implementor must ensure {@link Integer#signum
     * signum}{@code (x.compareTo(y)) == -signum(y.compareTo(x))} for all {@code x} and {@code y}.
     * (This implies that {@code x.compareTo(y)} must throw an exception if and only if
     * {@code y.compareTo(x)} throws an exception.)
     *
     * <p>The implementor must also ensure that the relation is transitive:
     * {@code (x.compareTo(y) > 0 && y.compareTo(z) > 0)} implies {@code x.compareTo(z) > 0}.
     *
     * <p>Finally, the implementor must ensure that {@code
     * x.compareTo(y)==0} implies that {@code signum(x.compareTo(z)) == signum(y.compareTo(z))}, for
     * all {@code z}.
     *
     * @param o the object to be compared.
     * @return a negative integer, zero, or a positive integer as this object is less than, equal
     * to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     * @throws ClassCastException   if the specified object's type prevents it from being compared
     *                              to this object.
     * @apiNote It is strongly recommended, but <i>not</i> strictly required that
     * {@code (x.compareTo(y)==0) == (x.equals(y))}.  Generally speaking, any class that implements
     * the {@code Comparable} interface and violates this condition should clearly indicate this
     * fact.  The recommended language is "Note: this class has a natural ordering that is
     * inconsistent with equals."
     */
    @Override
    public int compareTo(ClientPOJO o) {
        if (o == null){
            throw new NullPointerException("Error Code 0x002 - [Raised] El parametro a comparar " +
                                           "en compareTo de ClientPOJO no puede ser nulo");
        } else {
            return this._clientUsername.compareTo(o.get_clientUsername());
        }
    }
}
