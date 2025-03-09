package com.evolvlabs.multiuserchatgui.CommunicationBackend;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 08-Mar-2025
 * @description: El presente archivo incluye un Data Transfer Object utilizado para manejar la 
 * transferencia de informacion de la UI, hacia el servidor y al final hacia la base de datos, se
 * utiliza este tipo de objetos por su capacidad de mantener un tipo de comunicacion immutable y 
 * su facilidad de seralizacion al implementar Serializable.
 */
public record MessageDTO(String _senderUUID,
                         String _receiverUUID,
                         String _messageContent,
                         Timestamp _messageTimestamp,
                         Boolean _senderConfirmation,
                         Boolean _receiverConfirmation) implements Comparable<MessageDTO>, Serializable {

    /**
     * <body style="color: white;">
     * Constructor para inicializar un objeto <code>MessageDTO</code>. Este constructor utiliza
     * valores inmutables para inicializar cada uno de los atributos que representan la
     * transferencia de datos de un mensaje.
     *
     * @param _senderUUID           Identificador unico que corresponde al remitente del mensaje.
     * @param _receiverUUID         Identificador unico que corresponde al receptor del mensaje.
     * @param _messageContent       El contenido textual del mensaje que se desea transmitir.
     * @param _messageTimestamp     Marca de tiempo que indica el momento en que se creo el
     *                              mensaje.
     * @param _senderConfirmation   Estado booleano que confirma si el mensaje fue procesado
     *                              exitosamente por el remitente.
     * @param _receiverConfirmation Estado booleano que confirma si el mensaje fue recibido
     *                              exitosamente por el receptor.
     * @throws NullPointerException Si alguno de los parametros proporcionados es <code>null</code>.
     *                              Esto garantiza que los atributos esenciales del mensaje nunca
     *                              sean nulos.
     *                              </body>
     */
    public MessageDTO(String _senderUUID,
                      String _receiverUUID,
                      String _messageContent,
                      Timestamp _messageTimestamp,
                      Boolean _senderConfirmation,
                      Boolean _receiverConfirmation) {
        this._senderUUID = _senderUUID;
        this._receiverUUID = _receiverUUID;
        this._messageContent = _messageContent;
        this._messageTimestamp = _messageTimestamp;
        this._senderConfirmation = _senderConfirmation;
        this._receiverConfirmation = _receiverConfirmation;
    }


    /**
     * <body style="color: white;">
     * Convierte esta instancia de <code>MessageDTO</code> en un arreglo de objetos
     * (<code>Object[]</code>), donde cada elemento representa un atributo de la instancia. El orden
     * de los elementos en el arreglo corresponde a los siguientes atributos:
     * <ul>
     *     <li>Identificador unico del remitente (<code>_senderUUID</code>).</li>
     *     <li>Identificador unico del receptor (<code>_receiverUUID</code>).</li>
     *     <li>Contenido del mensaje (<code>_messageContent</code>).</li>
     *     <li>Marca de tiempo del mensaje (<code>_messageTimestamp</code>).</li>
     *     <li>Confirmacion del remitente (<code>_senderConfirmation</code>).</li>
     *     <li>Confirmacion del receptor (<code>_receiverConfirmation</code>).</li>
     * </ul>
     *
     * @return Un arreglo de objetos (<code>Object[]</code>) que contiene los atributos inmutables
     * de esta instancia en el mismo orden que se describio.
     * @throws NullPointerException Si alguno de los atributos dentro del record contiene
     *                              <code>null</code>. Esto no deberia ocurrir si el objeto fue
     *                              construido adecuadamente.
     *                              </body>
     */
    public Object[] toObjectArray() {
        return new Object[]{
                _senderUUID,
                _receiverUUID,
                _messageTimestamp,
                _messageContent,
                _senderConfirmation,
                _receiverConfirmation
        };
    }

    /**
     * Indicates whether some other object is "equal to" this one.  In addition to the general
     * contract of {@link Object#equals(Object) Object.equals}, record classes must further obey the
     * invariant that when a record instance is "copied" by passing the result of the record
     * component accessor methods to the canonical constructor, as follows:
     * <pre>
     *     R copy = new R(r.c1(), r.c2(), ..., r.cn());
     * </pre>
     * then it must be the case that {@code r.equals(copy)}.
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if this record is equal to the argument; {@code false} otherwise.
     * @implSpec The implicitly provided implementation returns {@code true} if and only if the
     * argument is an instance of the same record class as this record, and each component of this
     * record is equal to the corresponding component of the argument; otherwise, {@code false} is
     * returned. Equality of a component {@code c} is determined as follows:
     * <ul>
     *
     * <li> If the component is of a reference type, the component is
     * considered equal if and only if {@link
     * Objects#equals(Object, Object)
     * Objects.equals(this.c, r.c)} would return {@code true}.
     *
     * <li> If the component is of a primitive type, using the
     * corresponding primitive wrapper class {@code PW} (the
     * corresponding wrapper class for {@code int} is {@code
     * java.lang.Integer}, and so on), the component is considered
     * equal if and only if {@code
     * PW.compare(this.c, r.c)} would return {@code 0}.
     *
     * </ul>
     * <p>
     * Apart from the semantics described above, the precise algorithm
     * used in the implicitly provided implementation is unspecified
     * and is subject to change. The implementation may or may not use
     * calls to the particular methods listed, and may or may not
     * perform comparisons in the order of component declaration.
     * @see Objects#equals(Object, Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MessageDTO messageDTO = (MessageDTO) obj;
        return this._senderUUID().equals(messageDTO._senderUUID())
               && this._receiverUUID().equals(messageDTO._receiverUUID());
    }

    /**
     * Returns a string representation of the record. In accordance with the general contract of
     * {@link Object#toString()}, the {@code toString} method returns a string that "textually
     * represents" this record. The result should be a concise but informative representation that
     * is easy for a person to read.
     * <p>
     * In addition to this general contract, record classes must further participate in the
     * invariant that any two records which are {@linkplain Record#equals(Object) equal} must
     * produce equal strings.  This invariant is necessarily relaxed in the rare case where
     * corresponding equal component values might fail to produce equal strings for themselves.
     *
     * @return a string representation of the object.
     * @implSpec The implicitly provided implementation returns a string which contains the name of
     * the record class, the names of components of the record, and string representations of
     * component values, so as to fulfill the contract of this method. The precise format produced
     * by this implicitly provided implementation is subject to change, so the present syntax should
     * not be parsed by applications to recover record component values.
     * @see Object#toString()
     */
    @Override
    public String toString() {
        return "MessageDTO{" +
                "_senderUUID='" + _senderUUID + '\'' + "\n" +
                ", _receiverUUID='" + _receiverUUID + '\'' + "\n" +
                ", _messageContent='" + _messageContent + '\'' + "\n" +
                ", _messageTimestamp=" + _messageTimestamp + "\n" +
                ", _senderConfirmation=" + _senderConfirmation + "\n" +
                ", _receiverConfirmation=" + _receiverConfirmation +
                '}';
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
    public int compareTo(MessageDTO o) {
        if (o == null){
            throw new NullPointerException("Error Code 0x002 - [Raised] El parametro a comparar " +
                                           "en compareTo de MessageDTO no puede ser nulo");
        } else {
            return this._messageTimestamp.compareTo(o._messageTimestamp);
        }
    }
}
