package com.evolvlabs.multiuserchatgui.ClientSideBackend;

import com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-2025
 * @description: El presente archivo muestra el POJO (Plain Old Java Object) requerido para
 * representar en el contexto de nuestra aplicacion un Mensaje. La clase es una clase base de
 * nuestro backednd para el cliente, y se usa de manera reducida pero con importancia grave en
 * las clases del backend de
 * {@link com.evolvlabs.multiuserchatgui.ServerSideBackend.DatabaseManagementSystem}. En si la
 * clase presenta varios campos privados con getters y setters usados para crear un objeto de
 * tipo mensaje durante la recepcion de datos de la base de datos, o el envio de datos a la
 * misma, asi como la carga a la UI
 */
public class MessagePOJO implements Comparable<MessagePOJO>, Serializable {

    /*! Parametros Internos*/
    private String      _senderUUID;
    private String      _receiverUUID;
    private String      _messageContent;
    private Timestamp   _messageTimestamp;
    private Boolean     _messageSenderAcknowledge;
    private Boolean     _messageReceiverAcknowledge;
    private String      _senderUsername;
    private String      _receiverUsername;

    /*! Constructores */
    public MessagePOJO(String senderUUID,
                       String receiverUUID,
                       String messageContent,
                       Timestamp messageTimestamp,
                       Boolean messageSenderAcknowledge,
                       Boolean messageReceiverAcknowledge){
        this.set_senderUUID(senderUUID);
        this.set_receiverUUID(receiverUUID);
        this.set_messageContent(messageContent);
        this.set_messageTimestamp(messageTimestamp);
        this.set_messageSenderAcknowledge(messageSenderAcknowledge);
        this.set_messageReceiverAcknowledge(messageReceiverAcknowledge);
    }

    public MessagePOJO(String senderUUID, String receiverUUID, String messageContent,
                       Timestamp messageTimestamp){
        this.set_senderUUID(senderUUID);
        this.set_receiverUUID(receiverUUID);
        this.set_messageContent(messageContent);
        this.set_messageTimestamp(messageTimestamp);
        this.set_messageSenderAcknowledge(false);
        this.set_messageReceiverAcknowledge(false);
    }
    public MessagePOJO(){;}

    /*! Getters y Setters*/

    /**
     * Este metodo devuelve el valor almacenado en el campo interno {@code _senderUUID} de la
     * clase.
     *
     * <p>El campo {@code _senderUUID} representa el identificador unico universal (UUID) del
     * remitente del mensaje. Este identificador debe haber sido definido previamente ya sea al
     * inicializar la instancia de la clase {@link MessagePOJO} o mediante el uso del metodo
     * {@link #set_senderUUID(String)}.</p>
     *
     * @return Una cadena de texto ({@code String}) que contiene el UUID del remitente, o
     * {@code null} si el campo no ha sido inicializado.
     * @see #set_senderUUID(String)
     */
    public String get_senderUUID() {
        return _senderUUID;
    }


    /**
     * Este metodo establece el valor del campo interno {@code _senderUUID}, el cual representa el
     * identificador unico universal (UUID) del remitente de un mensaje.
     *
     * <p>El parametro {@code senderUUID} debe ser una cadena de texto no nula y no vacia.
     * En caso contrario, el metodo genera una excepcion {@link IllegalArgumentException} explicando
     * el error.</p>
     *
     * <p>Este metodo valida el valor del parametro antes de asignarlo al campo interno,
     * asegurando que cumple con los requisitos necesarios para identificar al remitente de manera
     * adecuada.</p>
     *
     * @param senderUUID El UUID del remitente que sera asignado al campo interno. Debe ser una
     *                   cadena de texto no nula y no vacia.
     * @throws IllegalArgumentException Si el valor del parametro es {@code null} o una cadena
     *                                  vacia.
     * @see #get_senderUUID()
     */
    public void set_senderUUID(String senderUUID) throws IllegalArgumentException {
        if (senderUUID != null && !senderUUID.isEmpty()) {
            this._senderUUID = senderUUID;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "senderUUID del metodo set_senderUUID no puede " +
                                               "ser nulo");
        }
    }

    /**
     * Este metodo devuelve el valor almacenado en el campo interno {@code _receiverUUID} de la
     * clase.
     *
     * <p>El campo {@code _receiverUUID} representa el identificador unico universal (UUID) del
     * destinatario del mensaje. Este UUID es asignado cuando una instancia de la clase
     * {@link MessagePOJO} se inicializa o mediante el uso del metodo
     * {@link #set_receiverUUID(String)}.</p>
     *
     * @return Una cadena de texto ({@code String}) que contiene el UUID del destinatario o
     * {@code null} si el campo no ha sido inicializado.
     * @see #set_receiverUUID(String)
     */
    public String get_receiverUUID() {
        return _receiverUUID;
    }

    /**
     * <body style="color:white;">
     * Este metodo establece el valor del campo interno {@code _receiverUUID}, el cual representa el
     * identificador unico universal (UUID) del receptor o destinatario de un mensaje dentro del
     * sistema.
     *
     * <p>El metodo utiliza verificaciones basicas para garantizar que el UUID proporcionado sea una
     * cadena
     * de texto no vacia ni nula. Esto asegura la integridad de los datos al evitar valores
     * invalidos dentro del campo interno.</p>
     * @param receiverUUID El UUID del destinatario a guardar en el campo interno. No debe ser nulo
     *                     ni vacio.
     * @throws IllegalArgumentException Cuando el parametro {@code receiverUUID} es nulo o una
     *                                  cadena vacia.
     * @see #get_receiverUUID()
     * </body>
     */
    public void set_receiverUUID(String receiverUUID) throws IllegalArgumentException {
        if (receiverUUID != null && !receiverUUID.isEmpty()) {
            this._receiverUUID = receiverUUID;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "receiverUUID del metodo set_receiverUUID no puede " +
                                               "ser nulo");
        }
    }

    /**
     * <body style="color:white;">
     * Este metodo recupera el contenido del mensaje almacenado en el campo interno
     * {@code _messageContent}.
     *
     * <p>El campo interno {@code _messageContent} contiene el texto del mensaje enviado o recibido
     * dentro del sistema de mensajeria.
     * Este dato puede haberse inicializado al momento de crear la instancia de la clase
     * {@link MessagePOJO} o mediante el uso del metodo {@link #set_messageContent(String)}.</p>
     *
     * @return Una cadena de texto ({@code String}) que contiene el contenido del mensaje, o
     * {@code null} si el campo no ha sido inicializado.
     * @see #set_messageContent(String)
     * </body>
     */
    public String get_messageContent() {
        return _messageContent;
    }

    /**
     * <body style="color:white;">
     * Este metodo establece el valor del campo interno {@code _messageContent}, el cual representa
     * el contenido textual de un mensaje dentro del sistema.
     *
     * <p>El metodo verifica que el parametro proporcionado no sea nulo ni una cadena vacia, con lo
     * cual
     * valida la integridad del contenido del mensaje antes de asignarlo. En caso de que estas
     * condiciones no se cumplan, se lanza una excepcion del tipo
     * {@link IllegalArgumentException}.</p>
    
     * @param messageContent El texto del mensaje que sera asignado al campo interno. No puede ser
     *                       nulo ni una cadena vacia.
     * @throws IllegalArgumentException Si el parametro {@code messageContent} es {@code null} o una
     *                                  cadena vacia.
     * @see #get_messageContent()
     * </body>
     */
    public void set_messageContent(String messageContent) throws IllegalArgumentException {
        if (messageContent != null && !messageContent.isEmpty()) {
            this._messageContent = messageContent;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "messageContent del metodo set_messageContent no " +
                                               "puede ser nulo");
        }
    }

    /**
     * <body style="color:white;">
     * Este metodo devuelve el valor almacenado en el campo interno {@code _messageTimestamp} de la
     * clase.
     *
     * <p>El campo {@code _messageTimestamp} es un objeto de tipo {@link java.sql.Timestamp} que
     * representa el momento exacto en el que se envio el mensaje. Es una marca de tiempo utilizada
     * para operaciones como la ordenacion de mensajes por fecha.</p>
     *
     * <p>El valor de este campo puede inicializarse al configurar una instancia de
     * {@link MessagePOJO} mediante su constructor o el metodo setter relacionado
     * {@link #set_messageTimestamp(Timestamp)} antes de ser accedido.</p>
     * @return El valor del campo interno {@code _messageTimestamp}, de tipo
     * {@link java.sql.Timestamp}, o {@code null} si el campo no ha sido inicializado.
     * @see #set_messageTimestamp(Timestamp)
     * </body>
     */
    public Timestamp get_messageTimestamp() {
        return _messageTimestamp;
    }

    /**
     * <body style="color:white;">
     * Este metodo establece el valor del campo interno {@code _messageTimestamp}, que representa la
     * marca temporal exacta del momento en el que se crea, envia o recibe un mensaje dentro del
     * sistema.
     *
     * <p>El parametro {@code messageTimestamp} debe ser un objeto valido de la clase
     * {@link java.sql.Timestamp}. Si este parametro es nulo, el metodo lanza una excepcion del tipo
     * {@link IllegalArgumentException}, asegurando que nunca se almacenen marcas de tiempo
     * invalidas dentro del objeto {@link MessagePOJO}.</p>
     * @param messageTimestamp La marca temporal a asignar al campo interno, representada como un
     *                         objeto {@link java.sql.Timestamp}. No debe ser nulo.
     * @throws IllegalArgumentException Si el parametro {@code messageTimestamp} es nulo.
     * @see java.sql.Timestamp
     * </body>
     */
    public void set_messageTimestamp(Timestamp messageTimestamp) throws IllegalArgumentException {
        if (messageTimestamp != null) {
            this._messageTimestamp = messageTimestamp;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "messageTimestamp del metodo set_messageTimestamp " +
                                               "no puede ser nulo");
        }
    }

    /**
     * <body style="color:white;">
     * Este metodo obtiene el valor almacenado en el campo interno
     * {@code _messageSenderAcknowledge}.
     *
     * <p>El campo interno {@code _messageSenderAcknowledge} representa si el remitente de un
     * mensaje
     * ha reconocido o no la operacion asociada al mensaje. Este campo es de tipo {@link Boolean}, y
     * puede contener {@code true} para indicar el reconocimiento, {@code false} para la falta de
     * reconocimiento, o {@code null} si no ha sido inicializado.</p>
     *
     * @return Un valor de tipo {@link Boolean} que indica el estado de reconocimiento del
     * remitente, o {@code null} si el campo no ha sido inicializado.
     * </body>
     */
    public Boolean get_messageSenderAcknowledge() {
        return _messageSenderAcknowledge;
    }

    /**
     * <body style="color:white;">
     * Este metodo establece el valor del campo interno {@code _messageSenderAcknowledge}.
     *
     * <p>El parametro {@code messageSenderAcknowledge} indica si el remitente de un mensaje ha
     * reconocido o no la operacion correspondiente al mensaje. Este campo debe ser de tipo
     * {@link Boolean} y no puede ser {@code null}, ya que esto provocaria una excepcion del tipo
     * {@link IllegalArgumentException}.</p>
     *
     * <p>El metodo valida el valor del parametro antes de asignarlo al campo interno para evitar
     * datos inconsistentes dentro del sistema que maneja los mensajes.</p>
     *
     * @param messageSenderAcknowledge Un valor de tipo {@link Boolean} que indica si el remitente
     *                                 reconoce o no el mensaje. No puede ser {@code null}.
     * @throws IllegalArgumentException Si el parametro {@code messageSenderAcknowledge} es
     *                                  {@code null}.
     *                                  </body>
     */
    public void set_messageSenderAcknowledge(Boolean messageSenderAcknowledge) throws IllegalArgumentException {
        if (messageSenderAcknowledge != null) {
            this._messageSenderAcknowledge = messageSenderAcknowledge;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "messageSenderAcknowledge del metodo " +
                                               "set_messageSenderAcknowledge no puede ser nulo");
        }
    }

    /**
     * <body style="color:white;">
     * Este metodo obtiene el valor almacenado en el campo interno
     * {@code _messageReceiverAcknowledge}.
     *
     * <p>El campo interno {@code _messageReceiverAcknowledge} representa si el destinatario de un
     * mensaje ha reconocido o no la operacion asociada al mensaje. Este campo puede contener
     * valores de tipo {@link Boolean}: {@code true} para indicar el reconocimiento, {@code false}
     * para la falta de reconocimiento, o {@code null} si no ha sido inicializado.</p>
     *
     * @return El estado de reconocimiento del destinatario, un valor de tipo {@link Boolean}, o
     * {@code null} si el campo no ha sido inicializado.
     * </body>
     */
    public Boolean get_messageReceiverAcknowledge() {
        return _messageReceiverAcknowledge;
    }

    public String get_senderUsername() {
        return _senderUsername;
    }

    public void set_senderUsername(String _senderUsername) {
        if (!_senderUsername.isEmpty()){
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "_senderUsername del metodo set_senderUsername " +
                                               "no puede ser nulo");
        }
        this._senderUsername = _senderUsername;
    }

    public String get_receiverUsername() {
        return _receiverUsername;
    }

    public void set_receiverUsername(String _receiverUsername) {
        if (!_receiverUsername.isEmpty()){
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "_receiverUsername del metodo set_receiverUsername " +
                                               "no puede ser nulo");
        }
        this._receiverUsername = _receiverUsername;
    }

    /**
     * <body style="color:white;">
     * Este metodo establece el valor del campo interno {@code _messageReceiverAcknowledge}.
     *
     * <p>El parametro {@code messageReceiverAcknowledge} debe ser un valor de tipo {@link Boolean}
     * que indica si el destinatario de un mensaje ha reconocido o no la operacion correspondiente
     * al mensaje. Este parametro no puede ser {@code null}, de lo contrario, el metodo lanzara una
     * excepcion {@link IllegalArgumentException}.</p>
     *
     * <p>El metodo realiza una validacion preliminar para garantizar la consistencia de los datos
     * en
     * los que se basa el sistema de mensajes.</p>
     *
     * @param messageReceiverAcknowledge Un valor de tipo {@link Boolean} que especifica si el
     *                                   receptor ha reconocido el mensaje. No puede ser
     *                                   {@code null}.
     * @throws IllegalArgumentException Si el parametro {@code messageReceiverAcknowledge} es
     *                                  {@code null}.
     *                                  </body>
     */
    public void set_messageReceiverAcknowledge(Boolean messageReceiverAcknowledge)
                                                 throws IllegalArgumentException {
        if (messageReceiverAcknowledge != null) {
            this._messageReceiverAcknowledge = messageReceiverAcknowledge;
        } else {
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] El parametro " +
                                               "messageReceiverAcknowledge del metodo " +
                                               "set_messageReceiverAcknowledge no puede ser nulo");
        }
    }

    /**
     * <body style="color:white;">
     * Este metodo transforma una instancia de la clase {@link MessagePOJO} en un objeto
     * {@link com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO} que representa los
     * mismos datos pero adaptados a un formato DTO (Data Transfer Object) para ser utilizado en el
     * contexto de la comunicacion entre sistemas o capas.
     *
     * <p>El metodo sigue un enfoque basado en el concepto de encapsulamiento y transferencia
     * de datos. Utiliza los valores internos de los campos presentes en {@link MessagePOJO}, estos
     * valores son pasados directamente al constructor de la clase {@link MessageDTO}. Esto asegura
     * consistencia en los datos transformados y facilita la modularidad y claridad en la
     * comunicacion entre diferentes subsistemas.</p>
     *
     * @return Una nueva instancia de {@link MessageDTO} que contiene los valores actuales de los
     * campos del objeto {@link MessagePOJO}.
     * </body>
     */
    public final MessageDTO transformToMessageDTO() {
        return new MessageDTO(this._senderUUID,
                              this._receiverUUID,
                              this._messageContent,
                              this._messageTimestamp,
                              this._messageSenderAcknowledge,
                              this._messageReceiverAcknowledge);
    }

    /*! Sobrecargas requeridas para este tipo de objetos*/

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
        return "MessagePOJO{" +
               "_messageSender='" + this._senderUUID + "',\n" +
               "_messageReceiver='" + this._receiverUUID + "',\n" +
               "_messageContent='" + _messageContent + "',\n" +
               "_messageTimestamp=" + _messageTimestamp + ",\n" +
               "_messageSenderAcknowledge=" + _messageSenderAcknowledge + ",\n" +
               ", _messageReceiverAcknowledge=" + _messageReceiverAcknowledge +
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
            MessagePOJO other = (MessagePOJO) obj;
            return (this._senderUUID.equals(other._senderUUID) &&
                    this._receiverUUID.equals(other._receiverUUID) &&
                    this._messageContent.equals(other._messageContent) &&
                    this._messageTimestamp.equals(other._messageTimestamp) &&
                    this._messageSenderAcknowledge.equals(other._messageSenderAcknowledge) &&
                    this._messageReceiverAcknowledge.equals(other._messageReceiverAcknowledge));
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
    public int compareTo(MessagePOJO o) {
        if (o == null){
            throw new NullPointerException("Error Code 0x002 - [Raised] El parametro a comparar " +
                                           "en compareTo de MessagePOJO no puede ser nulo");
        } else {
            return this._messageTimestamp.compareTo(o.get_messageTimestamp());
        }
    }
}
