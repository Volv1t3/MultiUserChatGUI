package com.evolvlabs.multiuserchatgui.ClientSideBackend;

import com.evolvlabs.multiuserchatgui.CommunicationBackend.AuthenticationRequestDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.ClientDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.UsefulCommunicationMessages;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author : Santiago Arellano
 * @date : 07-Mar-2025
 * @description: El presente archivo implementa la clase de comunicacion que se encarga de
 * establecer un socket TCP con el servido (este debe estar corriendo previamente o la aplicacion
 * no dejara pasar en la inicializacion). La idea de esta clase es la recepcion y comunicacion
 * con la base de datos y el servidor mediante protocolos definidos en
 * UsefulCommunicationMessages que se asemejan a una comunicacion HTTP en un protocolo de envio
 * de mensajes TCP. En base a esto el servidor (tristemente por la impleentacion) tendra que
 * serializar informacion, enviar y recibir, y al mismo tiempo el cliente debera implementar
 * resguardos ante errores de envio y reattempts en cada uno de sus metodos
 */
public class MessageClient {

    /*
     * Los parametros a continuacion son parte del control interno del programa, es decir, son
     * parametros que tiene que tener un cliente para su identificacion en el sistema, mas no
     * determinan el comportamiento del servidor TCP.
     */
    private String messageClient_ClientUsername;
    private String messageClient_ClientDTODataUUID;
    /*
     * Los parametros a continuacion son parte del control interno del servidor TCP al que se
     * esta escuchando en la aplicacion, en especifico se busca conectarse al local host (127.0.0
     * .1) utilizando el purto 5_000. Estos parametros se pueden configurar dependiendo del tipo
     * de sistema y servicios en la host y server machine.
     */
    private final String        messageClient_ConnAddress = "127.0.0.1";
    private Socket              messageClient_ConnectionSocket;
    private ObjectOutputStream  messageClient_OutputStream;
    private ObjectInputStream   messageClient_InputStream;
    private final AtomicBoolean messageClient_IsConnected =
            new AtomicBoolean(false);
    private final int           messageCient_MaxRetryAttempts = 3;
    private final long          messageClient_RetryDelaysInMilliSeconds = 1000;
    /*
     * Los parametros definidos a continuacion determinan diferentes configuraicones adicionales
     * para los bloques de retry de la conexion asi como algunos metodos de transmision hacia la
     * UI. En el caso de recibir informacion, la idea es que el MessageClient mantenga una serie
     * de observables con los resultados de esta informacion, de esta forma el cliente siempre
     * tiene la informacion mas actualizada
     */
    private ObservableList<ClientDTO> messageClient_ListadoDeClientes;
    private ObservableList<MessageDTO> messageClient_ListadoDeMensajesEnviados;
    private ObservableList<MessageDTO> messageClient_ListadoDeMensajesRecibidos;


    /**
     * Constructor Base Encargado de LLenar todos los campos sin ningun tipo de inicializacion
     * adicional, ni llamada a metodos adicionales. Esto se puede usar para preparar la clase
     * para la utilizxacion con respecto al cliente.
     */
    public MessageClient(){
        /*! 1. Inicializamos parametros internos del usuario*/
        this.messageClient_ClientUsername = "UnknownUsername";
        this.messageClient_ClientDTODataUUID = null;

        /*Inicializamos los parametros Observables para UI de JavaFX*/
        this.messageClient_ListadoDeClientes =
                FXCollections.observableList(new ArrayList<>());
        this.messageClient_ListadoDeMensajesEnviados =
                FXCollections.observableList(new ArrayList<>());
        this.messageClient_ListadoDeMensajesRecibidos =
                FXCollections.observableList(new ArrayList<>());
        /*
         * Dejamos el resto de campos sin inicializacion ya que estos se inicializan con una
         * conexion al servidor existosa
         */
    }



    /*! Getters Requeridos Inicialmente*/

    /**
     * Este metodo verifica el estado de conexion actual del cliente. Internamente, comprueba si el
     * socket asociado a la conexion con el servidor sigue activo.
     * <p>
     * Para determinar si el cliente sigue conectado, utiliza el metodo {@code isConnected()} del
     * objeto {@link Socket}. El resultado se almacena en una variable de tipo {@link AtomicBoolean}
     * para acceder de forma segura al estado de conexion entre hilos.
     * </p>
     *
     * @return {@link Boolean} indicando si el cliente actualmente esta conectado ({@code true}) o
     * no ({@code false}).
     * @throws NullPointerException si el socket aun no ha sido inicializado.
     */
    public Boolean getMessageClient_IsConnected() {
        this.messageClient_IsConnected.set(this.messageClient_ConnectionSocket.isConnected());
        return this.messageClient_IsConnected.get();
    }

    public String getMessageClient_ClientUsername(){
        return this.messageClient_ClientUsername;
    }
    /**
     * Este metodo permite obtener la lista observable que contiene informacion detallada de todos
     * los clientes autenticados en la aplicacion. Internamente, el metodo devuelve un objeto
     * {@link ObservableList} que asegura sincronizacion con la interfaz de usuario en tiempo real,
     * permitiendo actualizaciones dinamicas sin la necesidad de nuevas lecturas o consultas al
     * servidor TCP.
     * <p>
     * Esta lista es utilizada principalmente para reflejar el estado actual de los clientes
     * conectados, facilitando la comunicacion y la gestion de usuarios en tiempo real dentro de la
     * aplicacion.
     *
     * <p><strong>Consideraciones:</strong></p>
     * <ul>
     *   <li>El objeto retornado siempre esta inicializado, independientemente del
     *   estado de conexion del cliente.</li>
     *   <li>Si el cliente no esta conectado o la lista no ha sido actualizada,
     *   esta devolvera una lista vacia.</li>
     * </ul>
     *
     * @return {@link ObservableList} de objetos {@link ClientDTO} que representan los clientes
     * conectados.
     */
    public ObservableList<ClientDTO> getMessageClient_ListadoDeClientes() {
        return messageClient_ListadoDeClientes;
    }

    /**
     * <body style="color:white">
     * Este metodo permite acceder a la lista observable que contiene los mensajes enviados desde el
     * cliente hacia otros usuarios del sistema. Esta lista se actualiza en tiempo real mediante el
     * uso de {@link javafx.collections.ObservableList}, permitiendo reflejar automaticamente los
     * cambios en la interfaz de usuario.
     
     * <b>Consideraciones:</b><br>
     * <ul>
     * <li>La lista es inmutable desde el exterior: cualquier cambio debe realizarse a traves
     * de las operaciones internas del cliente.</li>
     * <li>Si no se han enviado mensajes, el metodo devolvera una lista vacia.</li>
     * </ul>
     * </p>
     *
     * @return {@link ObservableList} que contiene objetos {@link MessageDTO} representando los
     * mensajes enviados por el cliente.
     * </body>
     */
    public ObservableList<MessageDTO> getMessageClient_ListadoDeMensajesEnviados() {
        return messageClient_ListadoDeMensajesEnviados;
    }

    /**
     * <body style="color:white">
     * Este metodo provee acceso a la lista observable que contiene los mensajes recibidos por el
     * cliente desde otros usuarios registrados en la aplicacion.
     *
     * <p>
     * La lista esta diseñada para ser actualizada en tiempo real utilizando
     * {@link javafx.collections.ObservableList}, lo que permite a la interfaz de usuario reflejar
     * de forma dinamica cualquier cambio en los mensajes sin validaciones o consultas adicionales
     * al servidor.
     * </p>
     *
     * <b>Consideraciones:</b>
     * <ul>
     * <li>El metodo siempre retorna una lista ya inicializada, aunque puede estar vacia
     * si el cliente no ha recibido mensajes.</li>
     * <li>El acceso a esta lista por parte de otras clases es solamente de lectura,
     * y cualquier actualizacion debe realizarse exclusivamente desde los metodos internos del cliente.</li>
     * </ul>
     *
     * @return {@link ObservableList} conteniendo instancias de {@link MessageDTO} que representan
     * los mensajes recibidos por el cliente.
     * </body>
     */
    public ObservableList<MessageDTO> getMessageClient_ListadoDeMensajesRecibidos() {
        return messageClient_ListadoDeMensajesRecibidos;
    }

    /**
     * <body style="color:white">
     * Este metodo intenta realizar un proceso de autenticacion hacia el servidor TCP utilizando un
     * conjunto de credenciales externas proporcionadas por el cliente.
     *
     * <p>El proceso incluye varias etapas:
     * <ul>
     *   <li>Validar las credenciales proporcionadas: username y password deben ser validos y cumplir con
     *       restricciones como no ser vacios o demasiado largos.</li>
     *   <li>Abrir conexiones TCP y crear stream para enviar y recibir comunicacion con el servidor.</li>
     *   <li>Enviar solicitudes de autenticacion en forma de objetos serializados, manejando acknowledgment
     *       y verificando respuestas del servidor.</li>
     *   <li>Procesar resultado de la autenticacion, actualizando estado interno del cliente.</li>
     * </ul>
     * </p>
     *
     * <b>Errores manejados:</b>
     * <ul>
     *   <li>IOException para errores relacionados con la conexion del socket o streams.</li>
     *   <li>Opcionales como NullPointerException, ClassCastException y otros para errores al procesar
     *       las respuestas del servidor.</li>
     * </ul>
     *
     * <p><b>Notas:</b> Este metodo utiliza {@link java.net.Socket}, {@link ObjectOutputStream},
     * y {@link ObjectInputStream} para la comunicacion subyacente.</p>
     *
     * @param externalClientUsername {@link String} - Nombre de usuario proporcionado por el
     *                               cliente. No debe ser null o mayor de 30 caracteres.
     * @param externalClientPassword {@link String} - Password del cliente. No debe ser null o
     *                               vacio.
     * @return {@link Optional} de tipo {@link String} que contiene el UUID del cliente autenticado
     * si el proceso fue exitoso, o un Optional vacio en caso de fallo.
     * @throws NullPointerException - Si el socket o streams no pueden inicializarse o si las
     *                              credenciales son nulas.
     * @throws IOException          - Si ocurre un problema relacionado con el socket, streams o
     *                              lectura/escritura de datos.
     * @throws RuntimeException     - Si ocurre un error no manejado durante el proceso.
     *
     *                              </body>
     */
    public Optional<String> attemptClientSideAuthenticationRequest(String externalClientUsername,
                                                                   String externalClientPassword) 
    {
        /*! 1. Validacion de entradas primarias*/
        if (externalClientUsername == null || externalClientUsername.isEmpty()){
            System.out.println("[MessageSideComms] - Error During Authentication Input " +
                                       "Validation: Username is " +
                                       "null or" +
                                       " empty");
            return Optional.empty();
        }
        if (externalClientPassword == null || externalClientPassword.isEmpty()){
            System.out.println("[MessageSideComms] - Error During Authentication Input " +
                                       "Validation: Password is " +
                                       "null or" +
                                       " empty");
            return Optional.empty();
        }
        if (externalClientUsername.length() > 30){
            System.out.println("[MessageSideComms] - Error During Authentication Input " +
                                       "Validation: Username is " +
                                       "too long");
            return Optional.empty();
        }

        /* Proceso General: Inentamos tres veces de establecer una conexion, si luego de tres
        veces el sistema falla...*/
            try{
                //? 1. Creamos un nuevo socket
                this.messageClient_ConnectionSocket =
                        new Socket(InetAddress.getByName(this.messageClient_ConnAddress),
                                   100);
                System.out.println("[MessageSideComms] - Authentication Request " +
                                           "Socket Created");

                //? 2. Inicializamos los treams del socket
                this.messageClient_OutputStream =
                        new ObjectOutputStream(this.messageClient_ConnectionSocket.getOutputStream());
                this.messageClient_InputStream =
                        new ObjectInputStream(this.messageClient_ConnectionSocket.getInputStream());

                //? 3. Enviamos la flag de autentication
                this.messageClient_OutputStream.writeUTF(
                        UsefulCommunicationMessages
                                .POST_CLIENT_AUTHENTICATION_REQUEST
                                .get_message());
                this.messageClient_OutputStream.flush();
                this.messageClient_OutputStream.reset();

                //? 4. Esperamos por la flag de acknowledge
                try{
                        String messageConfirmation = this.messageClient_InputStream.readUTF();
                        UsefulCommunicationMessages messages =
                                UsefulCommunicationMessages.valueOf(messageConfirmation);
                        if (messages.equals(UsefulCommunicationMessages.
                                                    POST_CLIENT_AUTHENTICATION_REQUEST_ACKNOWLEDGEMENT)){
                            System.out.println("[MessageSideComms] - Authentication Request " +
                                                       "Acknowledgement Received");
                        }else {
                            System.out.println("[MessageSideComms] - Error During Authentication " +
                                                       "Request: " +
                                                       "Failed to read the acknowledgement message");
                            return Optional.empty();
                        }

                } catch (ClassCastException e){
                        System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the acknowledgement message");
                        extractErrorInformationAndPrint(e);
                } catch(IOException e){
                        System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the acknowledgement message due to an " +
                                                   "IOException error");
                        extractErrorInformationAndPrint(e);
                        return Optional.of("SERVER_DISCONNECT");
                } catch (Exception e) {
                        System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the acknowledgement message due to an " +
                                                   "unknown error");
                        extractErrorInformationAndPrint(e);
                }


                //? 5. Enviamos la informacion de las credenciales leidas
                AuthenticationRequestDTO requestDTO = new AuthenticationRequestDTO(new ClientPOJO(
                        externalClientUsername,
                        externalClientPassword));
                try{
                    messageClient_OutputStream.writeObject(requestDTO);
                    messageClient_OutputStream.flush();
                    messageClient_OutputStream.reset();
                } catch (IOException e){
                    System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to send the authentication request");
                    extractErrorInformationAndPrint(e);
                    return Optional.of("SERVER_DISCONNECT");
                }catch (Exception e){
                    System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to send the authentication request due to an " +
                                                   "unknown error");
                    extractErrorInformationAndPrint(e);

                }
                
                // 6. Esperamos por la verificacion del servidor
                try{
                    AuthenticationRequestDTO.AuthenticationResponseDTO responseDTO =
                            (AuthenticationRequestDTO.AuthenticationResponseDTO)
                            this.messageClient_InputStream.readObject();


                    //? 7. Revisamos el retorno del servidor
                    if (responseDTO.authenticationResponse()){
                        System.out.println("[MessageSideComms] - Authentication Succeeded: " +
                                                   "Usuario [" +
                                                   externalClientUsername + "] was authenticated " +
                                                   "and validated.");
                        this.messageClient_ClientUsername = responseDTO.clientUsername();


                        //8. Esperamos lectura del DTO real
                        String clientDTOUUID =
                                this.messageClient_InputStream.readUTF();
                        if (clientDTOUUID != null){
                            this.messageClient_ClientDTODataUUID = clientDTOUUID;
                            this.messageClient_ClientUsername = externalClientUsername;
                            System.out.println(clientDTOUUID);
                        }else {
                            System.out.println("[MessageSideComms] - Error During Authentication " +
                                                   "Request: " +
                                                   "Failed to read the authentication " +
                                                       "clientDTO response as a " +
                                                   "DTO due to an " +
                                                   "unknown error");
                        }

                        //9. Actualizamos el estado de conexion
                        this.messageClient_IsConnected.set(true);
                        System.out.println("Connection Correct");
                        return Optional.of(this.messageClient_ClientDTODataUUID);
                    }
                    else {
                        System.out.println("[MessageSideComms] - Authentication Failed: " +
                                                   "Usuario [" +
                                                   externalClientUsername + "] was not " +
                                                   "authenticated and validated.");
                        return Optional.empty();
                    }
                } catch (ClassNotFoundException e){
                    System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the authentication response as a " +
                                               "responseDTO");
                    extractErrorInformationAndPrint(e);
                } catch (InvalidClassException e){
                    System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the authentication response as a " +
                                               "responseDTO due to an InvalidClassException");
                    extractErrorInformationAndPrint(e);
                }
                catch (IOException e){
                    System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the authentication response as a " +
                                               "responseDTO due to an IOException");
                    extractErrorInformationAndPrint(e);
                    return Optional.of("SERVER_DISCONNECT");
                } catch(Exception e){
                    System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to read the authentication response as a " +
                                               "responseDTO due to an unknown exception");
                    extractErrorInformationAndPrint(e);
                }
                
            }catch(IOException e){
                System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to create socket due to an IOException");
                extractErrorInformationAndPrint(e);
                return Optional.of("SERVER_DISCONNECT");
            }catch(Exception e){
                System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to create socket due to an unknown exception");
                extractErrorInformationAndPrint(e);

            }

        return Optional.empty();
    }


    /*Metodos POST ONLY parecidos a los del servidor*/


    /**
     * <body style="color:white">
     * Este metodo intenta enviar un mensaje desde la aplicacion cliente hacia un servidor TCP.
     * Realiza validaciones de entrada, gestiona el envio del mensaje y procesa las respuestas
     * recibidas por el servidor.
     *
     * <p><b>Fases del metodo:</b></p>
     * <ul>
     *   <li>Validacion de los datos proporcionados {@link MessagePOJO}.
     *       El mensaje debe tener un emisor y un receptor validos.</li>
     *   <li>Gestion de solicitudes y respuestas TCP:
     *       <ul>
     *         <li>Envia el mensaje con una señal correspondiente al servidor</li>
     *         <li>Espera y evalua un mensaje de confirmacion (ACKNOWLEDGE)</li>
     *         <li>Gestiona una cola de mensajes adicionales de actualizacion si es necesario</li>
     *       </ul>
     *   </li>
     *   <li>Reintenta la solicitud en caso de fallo usando un maximo predefinido de
     *       intentos ({@code messageCient_MaxRetryAttempts}).</li>
     * </ul>
     *
     * <p><b>Consideraciones:</b></p>
     * <ul>
     *   <li>Debido al manejo de sockets, este metodo es sensible a errores relacionados
     *       con la red (IOException).</li>
     *   <li>El metodo puede interrumpirse si recibe excepciones no previstas,
     *       devolviendo {@code false}.</li>
     *   <li>Si la conexion con el servidor no fue satisfactoria luego de varios intentos,
     *       se imprime un mensaje indicando el fallo.</li>
     * </ul>
     *
     * @param externalMessagePOJO {@link MessagePOJO} - Objeto que contiene la informacion del
     *                            mensaje a ser enviado. No debe ser {@code null}.
     * @return {@code true} si el mensaje fue enviado exitosamente al servidor, de lo contrario
     * {@code false}.
     * @throws NullPointerException Si el mensaje o alguno de sus atributos necesarios son nulos.
     * @throws IOException          Si ocurre un error relacionado con la conexion de red o I/O.
     * @throws RuntimeException     Si se produce un error desconocido durante el proceso del
     *                              envio.
     *                              </body>
     */
    public boolean postMessageFromClientInterface(MessagePOJO externalMessagePOJO) 
    {
        //? 1. Validacion de los datos internos
        if (externalMessagePOJO == null) {
            System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                       "Message is null");
            return false;
        } else if (externalMessagePOJO.get_receiverUUID() == null) {
            System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                       "Message receiver is null");
            return false;
        } else if (externalMessagePOJO.get_senderUUID() == externalMessagePOJO.get_receiverUUID()) {
            System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                       "Message sender and receiver are the same");
            return false;
        }

        ConcurrentLinkedDeque<String> processMessagesQueue = new ConcurrentLinkedDeque<>();
        //? 2. Intentamos enviar el mensaje a la base de datos
        int currAttempt = 0;
        while (currAttempt < this.messageCient_MaxRetryAttempts) {
            currAttempt++;
            try {
                //? 3. Enviamos la senal de que queremos enviar un mensaje al servidor
                this.messageClient_OutputStream.writeUTF(
                        UsefulCommunicationMessages.POST_MESSAGE_BROADCAST_REQUEST
                                .get_message());
                this.messageClient_OutputStream.flush();

                //? 4. Esperamos por el ACKNOWLEDGE del servidor
                try {
                    String messageConfirmation = this.messageClient_InputStream.readUTF();
                    UsefulCommunicationMessages messages =
                            UsefulCommunicationMessages.valueOf(messageConfirmation);
                    if (messages.equals(UsefulCommunicationMessages.
                                                POST_MESSAGE_BROADCAST_ACKNOWLEDGEMENT)) {
                        System.out.println("[MessageSideComms] - Message " +
                                                   "Broadcasting Acknowledgement Request " +
                                                   "Acknowledgement Received");
                    } else if (messages
                            .equals(UsefulCommunicationMessages
                                            .POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE)) {
                        System.out.println("[MessageSideComms] - Message " +
                                                   "Broadcasting Acknowledgement Request " +
                                                   "Acknowledgement Received");
                        processMessagesQueue.add(UsefulCommunicationMessages
                                                         .POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE
                                                         .get_message());
                    } else if (messages.equals(
                            UsefulCommunicationMessages.POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE)) {
                        System.out.println("[MessageSideComms] - Message " +
                                                   "Broadcasting Acknowledgement Request " +
                                                   "Acknowledgement Received");
                        processMessagesQueue.add(UsefulCommunicationMessages
                                                         .POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE
                                                         .get_message());
                    } else {
                        System.out.println("[MessageSideComms] - Error During message sending " +
                                                   "Request: " +
                                                   "Failed to read the acknowledgement message");
                    }

                } catch (ClassCastException e) {
                    System.out.println("[MssageSideComms] - Eror during message sending request:" +
                                               " failed to read a vaid acknowledge signal");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (IOException e) {
                    System.out.println("[MessageSideComms] - Error During message sending " +
                                               "Request: " +
                                               "Failed to read the acknowledgement message due to an " +
                                               "IOException error");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (Exception e) {
                    System.out.println("[MessageSideComms] - Error During message sending " +
                                               "request: " +
                                               "Failed to read the acknowledgement message due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                    continue;
                }

                //? 5. Enviamos el mensaje correctamente hacia el servidor.
                try {
                    this.messageClient_OutputStream
                            .writeObject(externalMessagePOJO.transformToMessageDTO());
                    this.messageClient_OutputStream.flush();

                    for (var value : processMessagesQueue) {
                        if (value == "POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE") {
                            this.postClientUpdateListRequest();
                        } else if (value == "POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE") {
                            this.postMessageUpdateListRequest();
                        } else {
                            System.out.println("[MessageSideComms] - Error During message sending " +
                                                       "phase: " +
                                                       "Failed to send the message to the server");
                        }
                    }
                    return true;
                } catch (IOException e) {
                    System.out.println("[MessageSideComms] - Error During message sending phase: " +
                                               "Failed to send the message to the server");
                    extractErrorInformationAndPrint(e);

                } catch (Exception e) {
                    System.out.println("[MessageSideComms] - Error During message sending phase: " +
                                               "Failed to send the message to the server due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                }
            } catch (IOException e) {
                System.out.println("[MessageSideComms] - Error During Message Broadcast Request: " +
                                           "Failed to send the message due to an IOException");
                extractErrorInformationAndPrint(e);
            } catch (Exception e) {
                System.out.println("[MessageSideComms] - Error During Message Broadcast Request: " +
                                           "Failed to sendthe message due to an unknown error.");
                extractErrorInformationAndPrint(e);
            }
        }
        System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                   "Failed to send the message to the server");
        return false;
    }

    /**
     * <body style="color:white">
     * Metodo que permite desconectar el cliente del servidor TCP de manera controlada. Este metodo
     * realiza un intento de notificar al servidor sobre la desconexion del cliente, esperando una
     * respuesta de confirmacion (ACKNOWLEDGE). Si no se obtiene dicha confirmacion tras los
     * intentos maximos permitidos, el cliente limpia los recursos localmente.
     *
     * <p><b>Fases del metodo:</b></p>
     * <ul>
     *   <li>1. Envia una solicitud de desconexion al servidor.</li>
     *   <li>2. Espera una respuesta ACKNOWLEDGE del servidor que valide la desconexion.</li>
     *   <li>3. Si el ACKNOWLEDGE es recibido, limpia los recursos locales asociados a
     *          la conexion.</li>
     *   <li>4. Si no hay respuesta tras varios intentos, realiza la limpieza de recursos localmente.</li>
     * </ul>
     *
     * <b>Consideraciones:</b>
     * <ul>
     *   <li>Si el servidor no responde, el metodo maneja la situacion limpiando los recursos
     *       manualmente y marcando la conexion como cerrada.</li>
     *   <li>La limpieza de recursos incluye el cierre de streams de entrada y salida y del
     *       socket del cliente.</li>
     *   <li>El metodo realiza un maximo de {@code messageCient_MaxRetryAttempts} intentos
     *       para recibir el ACKNOWLEDGE del servidor antes de proceder a la limpieza local.</li>
     * </ul>
     *
     * @return {@link Boolean} {@code true} si el cliente se desconecta correctamente del servidor,
     * {@code false} en caso de fallo.
     * @throws IOException      Si se produce algun error I/O relacionado con la comunicacion
     *                          durante la desconexion.
     * @throws RuntimeException Si ocurre un error desconocido durante la limpieza de recursos.
     *                          </body>
     */
    public boolean postDisconnectionRequestFromClientInterface() {
        int currentAttempt = 0;
        boolean serverHasBeenNotified = false;

        while (currentAttempt < this.messageCient_MaxRetryAttempts && !serverHasBeenNotified) {
            currentAttempt++;
            try {
                //? 1. Enviamos notificacion de desconexion al servidor
                System.out.println("[MessageSideComms] Attempting to send message list update request");
                System.out.println("[MessageSideComms] Connection status: " + messageClient_IsConnected.get());
                this.messageClient_OutputStream.writeUTF(
                        UsefulCommunicationMessages.POST_CLIENT_DISCONNECTION_REQUEST.get_message());
                this.messageClient_OutputStream.flush();

                //? 2.Esperamos a un ACKNOWLEDGE del servidor
                try {
                    String messageConfirmation = this.messageClient_InputStream.readUTF();
                    UsefulCommunicationMessages messages =
                            UsefulCommunicationMessages.valueOf(messageConfirmation);

                    if (messages.equals(UsefulCommunicationMessages
                                                .POST_CLIENT_DISCONNECTION_REQUEST_ACKNOWLEDGEMENT)) {

                        System.out.println("[MessageSideComms] - Disconnection request " +
                                                   "acknowledged by server");

                        //? 3. Limpiamos los recursos del sistema
                        try {
                            cleanupClientResources();
                            return true;
                        } catch (Exception e) {
                            System.out.println("[MessageSideComms] - Warning: Error during " +
                                                       "client cleanup: " + e.getMessage());
                            return true;
                        }
                    } else {
                        System.out.println("[MessageSideComms] - Error: Invalid " +
                                                   "acknowledgment received from server");
                    }
                } catch (IOException e) {
                    System.out.println("[MessageSideComms] - Error reading server " +
                                               "acknowledgment: " + e.getMessage());
                    extractErrorInformationAndPrint(e);
                }
            } catch (IOException e) {
                System.out.println("[MessageSideComms] - Error sending disconnect " +
                                           "request (Attempt " + currentAttempt + "/" +
                                           this.messageCient_MaxRetryAttempts + "): "
                                           + e.getMessage());
                extractErrorInformationAndPrint(e);
            }

        }
        //? 4. Si es servidor (dios no quiera) no responde, entonces limpiamos los recursos
        // por nuestra cuenta
        try {
            cleanupClientResources();
            System.out.println("[MessageSideComms] Local cleanup completed successfully");
            return true;
        } finally {
            // Ensure we mark the client as no longer running
            this.messageClient_IsConnected.set(false);
        }

    }

    /*Metodos POST que retornan informacion al cliente tambien */

    /**
     * <body style="color:white">
     * Metodo utilizado para solicitar del servidor TCP una lista actualizada de clientes conectados
     * al sistema. Este metodo sigue un proceso iterativo para manejar posibles errores en la
     * comunicacion y utiliza una estrategia de reintentos limitada en caso de fallos.
     *
     * <p><b>Proceso del metodo:</b></p>
     * <ul>
     *     <li>1. Validacion del estado de conexion del cliente. Si el cliente no esta conectado,
     *            el metodo termina sin ejecutar ninguna operacion adicional.</li>
     *     <li>2. Intento de envio del mensaje de solicitud utilizando streams de salida
     *            asociados al {@link Socket} creado previamente.</li>
     *     <li>3. Espera por un mensaje de confirmacion (ACKNOWLEDGE) por parte del servidor indicando
     *            que la solicitud ha sido recibida correctamente.</li>
     *     <li>4. Recepcion de la lista de clientes proporcionada por el servidor como un objeto
     *            deserializado de tipo {@link List}&lt;{@link ClientDTO}&gt;.</li>
     *     <li>5. Actualizacion dinamica de la interfaz grafica del cliente mediante
     *            {@link Platform#runLater(Runnable)} para reflejar los datos obtenidos.</li>
     *     <li>6. Si ocurre un error, el metodo sigue ejecutando hasta alcanzar el limite maximo
     *            de reintentos definido en {@code MAX_ATTEMPTS_FOR_QUERY}.</li>
     * </ul>
     *
     * <p><b>Consideraciones:</b></p>
     * <ul>
     *     <li>El metodo puede lanzar errores manejados relacionados con la comunicacion
     *         ({@link IOException}), problemas de deserializacion ({@link ClassCastException},
     *         {@link ClassNotFoundException}), o errores inesperados ({@link Exception}).</li>
     *     <li>En caso de agotar los intentos permitidos, el metodo imprime un mensaje de log
     *         indicando el fallo sin detener la aplicacion.</li>
     * </ul>
     *
     * @throws IOException          Si ocurre un error durante la comunicacion con el servidor.
     * @throws InterruptedException Si ocurre un fallo al aplicar retrasos entre reintentos.
     * @throws NullPointerException Si los recursos usados (streams o socket) estan mal
     *                              inicializados.
     *                              </body>
     */
    public void postClientUpdateListRequest() {
        //?1. Validacion de estado: si el servidor no esta corriendo todavia, no podemos hacer
        //? esta llamada
        System.out.println("messageClient_IsConnected = " + messageClient_IsConnected.get());
        if (!messageClient_IsConnected.get()) {
            return;
        }
        final int MAX_ATTEMPTS_FOR_QUERY = 9;
        int currAttempt = 0;
        do {
            currAttempt++;
            try {
                //? 1. Iniciamos enviando una request flag al servidor
                messageClient_OutputStream.reset();
                messageClient_OutputStream.flush();
                this.messageClient_OutputStream.writeUTF(UsefulCommunicationMessages
                                                                 .POST_MANDATORY_CLIENT_LIST_UPDATE_REQUEST
                                                                 .get_message());
                this.messageClient_OutputStream.flush();

                //? 2. Esperamos por un ACKNOWLEDGE DEL SERVIDOR
                try {
                    String messageConfirmation = this.messageClient_InputStream.readUTF();
                    UsefulCommunicationMessages messages =
                            UsefulCommunicationMessages.valueOf(messageConfirmation);
                    if (messages.equals(UsefulCommunicationMessages.
                                                POST_MANDATORY_CLIENT_LIST_UPDATE_ACKNOWLEDGMENT)) {
                        System.out.println("[MessageSideComms] - Client List Update Acknowledgement " +
                                                   "Received");
                    } else {
                        System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                                   "Failed to read the acknowledgement message");
                        continue;
                    }
                } catch (ClassCastException e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid acknowledge signal due to " +
                                               "a ClassCastException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (IOException e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read the acknowledgement message due to an " +
                                               "IOException error");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (Exception e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read the acknowledgement message due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                    continue;
                }

                //? 3. Recibimos la informacion del servidor, una lista de clientes
                List<ClientDTO> resultadoDeLlamada;
                try {
                    resultadoDeLlamada =
                            (List<ClientDTO>) this.messageClient_InputStream.readObject();

                    //? 4. Enviamos una actuaizacion a las listas del sistema para que se pongan
                    //? en la pantalla del usuario
                    Platform.runLater(() -> {
                        messageClient_ListadoDeClientes.clear();
                        messageClient_ListadoDeClientes.addAll(resultadoDeLlamada);
                    });

                    System.out.println("[MessageSideComms] - Client List Update Request: " +
                                               "Client list updated successfully");
                    break;
                } catch /*Llamada sobrante de MessageList*/ (ClassCastException e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to a " +
                                               "ClassCastException");
                    System.out.println("This is most likey the result of a leftover message " +
                                               "update transaction");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch /*Valor sobrante de lllamadas anteriores*/ (OptionalDataException e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to an " +
                                               "OptionalDataException");
                    System.out.println("This is most likey the result of a leftover message " +
                                               "update transaction");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (ClassNotFoundException e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to a " +
                                               "ClassNotFoundException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (NullPointerException e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to a " +
                                               "NullPointerException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (Exception e) {
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                    continue;
                }
            } catch (IOException e) {
                System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                           "Failed to send the request to the server due to an " +
                                           "IOException");
                extractErrorInformationAndPrint(e);
                continue;
            } catch (Exception e) {
                System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                           "Failed to send the request to the server due to an " +
                                           "unknown error");
                extractErrorInformationAndPrint(e);
            }

            if (currAttempt < MAX_ATTEMPTS_FOR_QUERY) {
                try {
                    Thread.sleep(this.messageClient_RetryDelaysInMilliSeconds);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        } while (currAttempt < MAX_ATTEMPTS_FOR_QUERY);

        if (currAttempt >= MAX_ATTEMPTS_FOR_QUERY) {
            System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                       "Failed to send the request to the server after " +
                                       MAX_ATTEMPTS_FOR_QUERY + " attempts");
            return;
        }
    }


    /**
     * <body style="color:white">
     * Metodo que solicita al servidor TCP una actualizacion de la lista de mensajes enviados y
     * recibidos por el cliente. Este metodo sigue un proceso iterativo en caso de errores de
     * comunicacion, utilizando reintentos hasta un limite predefinido.
     *
     * <p><b>Proceso del metodo:</b></p>
     * <ul>
     *     <li>1. Valida el estado de conexion del cliente al servidor. Si no existe
     *            conexion, el metodo no realiza ninguna operacion adicional y retorna.</li>
     *     <li>2. Envia una solicitud al servidor usando streams de salida, indicando
     *            que requiere una actualizacion de la lista de mensajes.</li>
     *     <li>3. Espera por un mensaje de confirmacion (ACKNOWLEDGE) que valida la recepcion
     *            de la solicitud por parte del servidor.</li>
     *     <li>4. Recibe una lista de mensajes enviada por el servidor, separada en mensajes
     *            enviados y recibidos.</li>
     *     <li>5. Actualiza las listas observables internas para sincronizar la interface
     *            grafica del cliente utilizando {@link Platform#runLater(Runnable)}.</li>
     *     <li>6. Si ocurre un error durante el proceso, realiza reintentos hasta alcanzar
     *            el limite configurado ({@code MAX_ATTEMPTS_FOR_QUERY}).</li>
     * </ul>
     *
     * <p><b>Consideraciones:</b></p>
     * <ul>
     *     <li>El metodo puede lanzar errores manejados como {@link IOException} si existen
     *         problemas de red, o problemas de deserializacion como {@link ClassCastException},
     *         {@link ClassNotFoundException}.</li>
     *     <li>En casos de excepciones inesperadas, el metodo sigue intentando hasta
     *         completar el limite maximo de intentos configurado.</li>
     * </ul>
     *
     * @throws IOException          Si ocurren problemas de red o fallo al enviar/recibir datos al
     *                              servidor.
     * @throws InterruptedException Si hay un fallo al aplicar retrasos entre reintentos.
     * @throws NullPointerException Si alguno de los recursos utilizados (como streams o socket) no
     *                              esta inicializado correctamente.
     *                              </body>
     */
    public void postMessageUpdateListRequest() 
    {
        //?1. Validacion de estado: si el servidor no esta corriendo todavia, no podemos hacer
        //? esta llamada
        if (!messageClient_IsConnected.get()){
            return;
        }
        final int MAX_ATTEMPTS_FOR_QUERY = 9;
        int currAttempt =0;
        do {
            currAttempt++;
            try {
                //? 1. Iniciamos enviando una request flag al servidor
                this.messageClient_OutputStream.writeUTF(UsefulCommunicationMessages
                                                                 .POST_MANDATORY_MESSAGE_LIST_UPDATE_REQUEST
                                                                 .get_message());
                this.messageClient_OutputStream.flush();

                //? 2. Esperamos por un ACKNOWLEDGE DEL SERVIDOR
                try{
                    String messageConfirmation = this.messageClient_InputStream.readUTF();
                    UsefulCommunicationMessages messages =
                            UsefulCommunicationMessages.valueOf(messageConfirmation);
                    if (messages.equals(UsefulCommunicationMessages.
                                                POST_MANDATORY_MESSAGE_LIST_UPDATE_ACKNOWLEDGMENT)){
                        System.out.println("[MessageSideComms] - Client Message LIst Update " +
                                                   "Acknowledgement " +
                                                   "Received");
                    }else {
                        System.out.println("[MessageSideComms] - Error During Client Message List" +
                                                   " Update Request: " +
                                                   "Failed to read the acknowledgement message");
                        continue;
                    }
                } catch (ClassCastException e){
                    System.out.println("[MessageSideComms] - Error During Message List Update " +
                                               "Request: " +
                                               "Failed to read a valid acknowledge signal due to " +
                                               "a ClassCastException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch(IOException e){
                    System.out.println("[MessageSideComms] - Error During Message List Update " +
                                               "Request: " +
                                               "Failed to read the acknowledgement message due to an " +
                                               "IOException error");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (Exception e) {
                    System.out.println("[MessageSideComms] - Error During Message  List Update " +
                                               "Request: " +
                                               "Failed to read the acknowledgement message due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                    continue;
                }

                //? 3. Recibimos la informacion del servidor, una lista de clientes
                Map<String,List<MessageDTO>> resultadoDeLlamada;
                try{
                    resultadoDeLlamada =
                            (Map<String,List<MessageDTO>>) this.messageClient_InputStream.readObject();

                    //? 4. Enviamos una actuaizacion a las listas del sistema para que se pongan
                    //? en la pantalla del usuario
                    Platform.runLater(() -> {
                        try {
                            //? 4.1 Extraemos la informacion de la llamada
                            List<MessageDTO> receivedMessages = resultadoDeLlamada.getOrDefault(
                                    "receivedMessages", new ArrayList<>());
                            List<MessageDTO> sentMessages = resultadoDeLlamada.getOrDefault("sentMessages", new ArrayList<>());

                            //? 4.2 Cargamos la informacion de la llamada en ambos observables

                            messageClient_ListadoDeMensajesRecibidos.clear();
                            messageClient_ListadoDeMensajesRecibidos.addAll(receivedMessages);

                            messageClient_ListadoDeMensajesEnviados.clear();
                            messageClient_ListadoDeMensajesEnviados.addAll(sentMessages);


                            System.out.println("[MessageSideComms] - Updated received messages: " + receivedMessages.size() +
                                                       ", sent messages: " + sentMessages.size());
                        } catch (Exception e) {
                            System.out.println("[MessageSideComms] - Error updating message lists: " + e.getMessage());
                        }
                    });


                    System.out.println("[MessageSideComms] - Client Message Lists Update Request:" +
                                               " " +
                                               "Message Lists updated successfully");
                    break;
                }catch /*Llamada sobrante de MessageList*/(ClassCastException e){
                    System.out.println("[MessageSideComms] - Error During Message List Update " +
                                               "Request: " +
                                               "Failed to read a valid message list due to a " +
                                               "ClassCastException");
                    System.out.println("This is most likey the result of a leftover message " +
                                               "update transaction");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch /*Valor sobrante de lllamadas anteriores*/(OptionalDataException e){
                    System.out.println("[MessageSideComms] - Error During Messae List Update " +
                                               "Request: " +
                                               "Failed to read a valid client list due to an " +
                                               "OptionalDataException");
                    System.out.println("This is most likey the result of a leftover message " +
                                               "update transaction");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (ClassNotFoundException e){
                    System.out.println("[MessageSideComms] - Error During Client Message List " +
                                               "Update Request: " +
                                               "Failed to read a valid message list due to a " +
                                               "ClassNotFoundException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch(NullPointerException e){
                    System.out.println("[MessageSideComms] - Error During Client Message List " +
                                               "Update Request: " +
                                               "Failed to read a message list due to a " +
                                               "NullPointerException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (Exception e) {
                    System.out.println("[MessageSideComms] - Error During Message List Update " +
                                               "Request: " +
                                               "Failed to read a valid message list due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                    continue;
                }
            } catch (IOException e) {
                System.out.println("[MessageSideComms] - Error During Message List Update Request: " +
                                           "Failed to send the request to the server due to an " +
                                           "IOException");
                extractErrorInformationAndPrint(e);
                continue;
            } catch (Exception e) {
                System.out.println("[MessageSideComms] - Error During Message List Update Request: " +
                                           "Failed to send the request to the server due to an " +
                                           "unknown error");
                extractErrorInformationAndPrint(e);
            }

            if (currAttempt < MAX_ATTEMPTS_FOR_QUERY){
                try {
                    Thread.sleep(this.messageClient_RetryDelaysInMilliSeconds);
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    break;
                }
            }

        }while(currAttempt < MAX_ATTEMPTS_FOR_QUERY);

        if (currAttempt >= MAX_ATTEMPTS_FOR_QUERY){
            System.out.println("[MessageSideComms] - Error During Message List Update Request: " +
                                       "Failed to send the request to the server after " +
                                       MAX_ATTEMPTS_FOR_QUERY + " attempts");
            return;
        }
    }


    /**
     * <body style="color:white">
     * Metodo utilizado para extraer informacion detallada sobre un error proporcionado como
     * argumento. Este metodo imprime en consola el mensaje del error, su causa subyacente y la pila
     * de excepciones para ayudar en la depuracion de problemas.
     *
     * <p><b>Proceso del metodo:</b></p>
     * <ul>
     *     <li>1. Obtiene el mensaje descriptivo del error utilizando {@code getMessage()}.</li>
     *     <li>2. Extrae y muestra la causa subyacente del error, si existe, utilizando {@code getCause()}.</li>
     *     <li>3. Imprime la pila completa de la excepcion mediante {@link Throwable#printStackTrace()}</li>
     * </ul>
     *
     * <p><b>Conceptos clave:</b></p>
     * <ul>
     *     <li>Este metodo es especialmente util en escenarios de depuracion y logging para
     *         entender las excepciones ocurridas durante la ejecucion.</li>
     *     <li>Es una herramienta de diagnostico que no lanza las excepciones, simplemente
     *         las registra y las documenta en la salida estandar.</li>
     * </ul>
     *
     * @param extractedError {@link Exception} Instancia de la excepcion que sera analizada e
     *                       impresa. Si es {@code null}, el metodo no realizara ninguna accion.
     *                       </body>
     */
    private static void extractErrorInformationAndPrint(Exception extractedError) {
        System.out.println("e.getMessage() = " + extractedError.getMessage());
        System.out.println("e.getCause() = " + extractedError.getCause());
    }

    /**
     * <body style="color:white">
     * Metodo responsable de liberar y limpiar todos los recursos asociados al cliente. Este metodo
     * cierra de manera segura los streams de entrada y salida, asi como el socket de conexion con
     * el servidor.
     *
     * <p><b>Proceso del metodo:</b></p>
     * <ul>
     *     <li>1. Verifica si los streams de entrada ({@link ObjectInputStream}) y salida
     *         ({@link ObjectOutputStream}) estan inicializados. Si es asi, los cierra y los reinicia a {@code null}.</li>
     *     <li>2. Verifica si el socket de conexion ({@link Socket}) esta inicializado y abierto
     *         ({@code !isClosed()}). Si cumple, lo cierra y lo reinicia a {@code null}.</li>
     *     <li>3. Imprime un mensaje indicando que la limpieza de recursos fue exitosa.</li>
     *     <li>4. Si ocurre un error durante la liberacion de recursos, este es capturado,
     *         registrado en logs, y se lanza un {@link RuntimeException} indicando que no se pudo
     *         completar la limpieza.</li>
     * </ul>
     *
     * <p><b>Consideraciones:</b></p>
     * <ul>
     *     <li>El metodo es idempotente e intenta limpiar recursos aunque algunos ya hayan sido
     *         cerrados previamente.</li>
     *     <li>En caso de un error inesperado, el metodo arroja la excepcion capturada para
     *         manejos adicionales en capas superiores.</li>
     * </ul>
     *
     * @throws RuntimeException Si ocurre un fallo inesperado al cerrar los streams de
     *                          entrada/salida o el socket.
     *                          </body>
     */
    private void cleanupClientResources() {
        try {
            if (this.messageClient_InputStream != null) {
                this.messageClient_InputStream.close();
                this.messageClient_InputStream = null;
            }
            if (this.messageClient_OutputStream != null) {
                this.messageClient_OutputStream.close();
                this.messageClient_OutputStream = null;
            }
            if (this.messageClient_ConnectionSocket != null
                    && !this.messageClient_ConnectionSocket.isClosed()) {
                this.messageClient_ConnectionSocket.close();
                this.messageClient_ConnectionSocket = null;
            }
            System.out.println("[MessageSideComms] - Client resources cleaned up successfully");
        } catch (IOException e) {
            System.out.println("[MessageSideComms] - Error during resource cleanup: " +
                                       e.getMessage());
            extractErrorInformationAndPrint(e);
        }
    }

}
