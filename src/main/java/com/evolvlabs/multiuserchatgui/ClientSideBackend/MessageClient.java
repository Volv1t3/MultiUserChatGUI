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

    public Boolean getMessageClient_IsConnected(){
        this.messageClient_IsConnected.set(this.messageClient_ConnectionSocket.isConnected());
        return this.messageClient_IsConnected.get();
    }
    public ObservableList<ClientDTO> getMessageClient_ListadoDeClientes() {
        return messageClient_ListadoDeClientes;
    }
    public ObservableList<MessageDTO> getMessageClient_ListadoDeMensajesEnviados() {
        return messageClient_ListadoDeMensajesEnviados;
    }
    public ObservableList<MessageDTO> getMessageClient_ListadoDeMensajesRecibidos() {
        return messageClient_ListadoDeMensajesRecibidos;
    }

    /*! Metodo base lineal de validacion*/
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

            }catch(Exception e){
                System.out.println("[MessageSideComms] - Error During Authentication Request: " +
                                           "Failed to create socket due to an unknown exception");
                extractErrorInformationAndPrint(e);

            }

        return Optional.empty();
    }


    /*Metodos POST ONLY parecidos a los del servidor*/
    public boolean postMessageFromClientInterface(MessagePOJO externalMessagePOJO)
    {
        //? 1. Validacion de los datos internos
        if (externalMessagePOJO == null){
            System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                       "Message is null");
            return false;
        } else if (externalMessagePOJO.get_receiverUUID() == null){
            System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                       "Message receiver is null");
            return false;
        } else if (externalMessagePOJO.get_senderUUID() == externalMessagePOJO.get_receiverUUID()){
            System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                       "Message sender and receiver are the same");
            return false;
        }

        ConcurrentLinkedDeque<String> processMessagesQueue = new ConcurrentLinkedDeque<>();
        //? 2. Intentamos enviar el mensaje a la base de datos
        int currAttempt = 0;
        while (currAttempt < this.messageCient_MaxRetryAttempts){
            currAttempt++;
            try{
                //? 3. Enviamos la senal de que queremos enviar un mensaje al servidor
                this.messageClient_OutputStream.writeUTF(
                        UsefulCommunicationMessages.POST_MESSAGE_BROADCAST_REQUEST
                        .get_message());
                this.messageClient_OutputStream.flush();

                //? 4. Esperamos por el ACKNOWLEDGE del servidor
                try{
                    String messageConfirmation = this.messageClient_InputStream.readUTF();
                    UsefulCommunicationMessages messages =
                            UsefulCommunicationMessages.valueOf(messageConfirmation);
                    if (messages.equals(UsefulCommunicationMessages.
                                                POST_MESSAGE_BROADCAST_ACKNOWLEDGEMENT)){
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
                    }else {
                        System.out.println("[MessageSideComms] - Error During message sending " +
                                                   "Request: " +
                                                   "Failed to read the acknowledgement message");
                    }

                } catch (ClassCastException e){
                    System.out.println("[MssageSideComms] - Eror during message sending request:" +
                                               " failed to read a vaid acknowledge signal");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch(IOException e){
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
                try{
                    this.messageClient_OutputStream
                            .writeObject(externalMessagePOJO.transformToMessageDTO());
                    this.messageClient_OutputStream.flush();

                    for(var value : processMessagesQueue){
                        if (value == "POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE"){
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
                } catch (IOException e){
                    System.out.println("[MessageSideComms] - Error During message sending phase: " +
                                               "Failed to send the message to the server");
                    extractErrorInformationAndPrint(e);

                }
                catch (Exception e){
                    System.out.println("[MessageSideComms] - Error During message sending phase: " +
                                               "Failed to send the message to the server due to an " +
                                               "unknown error");
                    extractErrorInformationAndPrint(e);
                }
            }catch(IOException e){
                System.out.println("[MessageSideComms] - Error During Message Broadcast Request: " +
                                           "Failed to send the message due to an IOException");
                extractErrorInformationAndPrint(e);
            }
            catch(Exception e){
                System.out.println("[MessageSideComms] - Error During Message Broadcast Request: " +
                                           "Failed to sendthe message due to an unknown error.");
                extractErrorInformationAndPrint(e);
            }
        }
        System.out.println("[MessageSideComms] - Error During Message Posting: " +
                                   "Failed to send the message to the server");
        return false;
    }

    public boolean  postDisconnectionRequestFromClientInterface()
    {
        int currentAttempt = 0;
        boolean serverHasBeenNotified = false;

        while (currentAttempt < this.messageCient_MaxRetryAttempts && !serverHasBeenNotified) {
            currentAttempt++;
            try {
                //? 1. Enviamos notificacion de desconexion al servidor
                System.out.println("[MessageSideComms] Attempting to send message list update request");
                System.out.println("[MessageSideComms] Connection status: " +  messageClient_IsConnected.get());
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

    public void postClientUpdateListRequest(){
        //?1. Validacion de estado: si el servidor no esta corriendo todavia, no podemos hacer
        //? esta llamada
        System.out.println("messageClient_IsConnected = " + messageClient_IsConnected.get());
        if (!messageClient_IsConnected.get()){
            return;
        }
        final int MAX_ATTEMPTS_FOR_QUERY = 9;
        int currAttempt =0;
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
                try{
                    String messageConfirmation = this.messageClient_InputStream.readUTF();
                    UsefulCommunicationMessages messages =
                            UsefulCommunicationMessages.valueOf(messageConfirmation);
                    if (messages.equals(UsefulCommunicationMessages.
                                                POST_MANDATORY_CLIENT_LIST_UPDATE_ACKNOWLEDGMENT)){
                        System.out.println("[MessageSideComms] - Client List Update Acknowledgement " +
                                                   "Received");
                    }else {
                        System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                                   "Failed to read the acknowledgement message");
                        continue;
                    }
                } catch (ClassCastException e){
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid acknowledge signal due to " +
                                               "a ClassCastException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch(IOException e){
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
                try{
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
                }catch /*Llamada sobrante de MessageList*/(ClassCastException e){
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to a " +
                                               "ClassCastException");
                    System.out.println("This is most likey the result of a leftover message " +
                                               "update transaction");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch /*Valor sobrante de lllamadas anteriores*/(OptionalDataException e){
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to an " +
                                               "OptionalDataException");
                    System.out.println("This is most likey the result of a leftover message " +
                                               "update transaction");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch (ClassNotFoundException e){
                    System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                               "Failed to read a valid client list due to a " +
                                               "ClassNotFoundException");
                    extractErrorInformationAndPrint(e);
                    continue;
                } catch(NullPointerException e){
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
            System.out.println("[MessageSideComms] - Error During Client List Update Request: " +
                                       "Failed to send the request to the server after " +
                                       MAX_ATTEMPTS_FOR_QUERY + " attempts");
            return;
        }
    }

    public void postMessageUpdateListRequest(){
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

    /*Implementacion de un metodo purely GET para escuchar a los mensajes del servidor*/




    private static void extractErrorInformationAndPrint(Exception extractedError) {
        System.out.println("e.getMessage() = " + extractedError.getMessage());
        System.out.println("e.getCause() = " + extractedError.getCause());
        extractedError.printStackTrace();
    }

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
            throw new RuntimeException("Failed to cleanup client resources", e);
        }
    }

}
