package com.evolvlabs.multiuserchatgui.ServerSideBackend;

import com.evolvlabs.multiuserchatgui.ClientSideBackend.ClientPOJO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.AuthenticationRequestDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.ClientDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO;
import com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine.EncryptionEngine;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.UsefulCommunicationMessages;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class MessageServer {

    /*! Private Parameters*/
    /*
     ? Todos los parametros definidos entre este bloque y el siguiente son parametros de
     ? conexion e informativos que el servidorGUI requiere durante su ejecucion.
     */
    private final String  ex_ServerConnectionAddress = "127.0.0.1";
    private Integer ex_ServerConnectionPort;
    private String ex_ServerConnectionPortString;
    private Integer       ex_ServerConnectedUsers = 0;
    private Integer       ex_ServerRegisteredUsers = 0;
    /*
     ? Los parametros definidos entre este bloque y el siguiente son parametros de conexion
     ? finales, como el servidor, la flag si esta encendido o no, etc.
     */
    private AtomicBoolean _ServerIsRunning = new AtomicBoolean(false);
    private ServerSocket  _ConnectionServer;
    /*
     ? Los parametros definidos entre este bloque y el siguiente son parametros de manejo de
     ? conexiones y de hilos de ejecucion para updates. El primero, un cached executor thread se
     ? usa para menejar un hilo por cliente mientras estos llegan a la aplicacion. El segundo, un
     ? single thread executor se usa para manejar el blocking queue de la base de datos. Y el
     ? blocking queue se usa para manejar los mensajes de fuera hacia la base de datos.
     */
    private final ExecutorService            _serviceForClients = Executors.newCachedThreadPool();
    private final ExecutorService            _serviceForDatabase =
            Executors.newFixedThreadPool(20);
    /*
     ? Los parametros definidos entre este bloque y el codigo principal son utilizados para el
     ? manejo de eventos y de listeners para cada cliente. La idea interna es no tener una nueva
     ? interface, varios libros de patrones de diseno sugieren usar algun patron para comunicar
     ? el servidor con metodos prestablecidos, pero en nuestro caso nos manejamos con certain
     ? flags por lo que no es necesario.
     */
    private DatabaseManagementSystem _DatabaseManagementSystem;
    private final Map<String, ClientHandler> _connectedClients = new ConcurrentHashMap<>(); //
    private final Map<String, String> _usernameToUuidMap = new ConcurrentHashMap<>();
    /*
     ? Los siguientes parametros fueron anadidos para garantizar listeners que escuchen desde la
     ? UI a los cambios en los valores de la base de datos
    */
    private  SimpleStringProperty ex_ConnectionAddressProperty;
    private  SimpleStringProperty ex_ConnectionPortProperty;
    private  SimpleStringProperty ex_ConnectedUsersProperty;
    private  SimpleStringProperty ex_RegisteredUsersProperty;
    private ObservableList<MessageDTO> ex_AllSentMessagesProperty;
    private ObservableList<ClientDTO> ex_AllUsernamesProperty;
    private ObservableList<MessageDTO> ex_FilteredMessagesProperty;

    /**
     * <body style="color:white;">
     * Constructor de la clase {@link MessageServer} utilizado para inicializar y configurar el
     * servidor de mensajes. Este constructor realiza las siguientes operaciones básicas:
     * <ul>
     *     <li>Intentar establecer la conexión para el servidor de mensajes mediante
     *     {@link #initMessageServerConnection()}.</li>
     *     <li>Inicializar la conexión con la base de datos utilizando el sistema de gestión de base de datos
     *     {@link DatabaseManagementSystem}.</li>
     *     <li>Cargar todos los mensajes enviados y usuarios registrados desde la base de datos y asignarlos a
     *     propiedades observables para sincronizar con la interfaz de usuario.</li>
     *     <li>Actualizar el contador de usuarios registrados desde la base de datos.</li>
     * </ul>
     * El constructor utiliza excepciones para manejar errores críticos durante la inicialización
     * del servidor o la base de datos.
     *
     * @throws RuntimeException     si ocurre un error al inicializar la conexión del servidor.
     * @throws NullPointerException si la base de datos devuelve valores nulos inesperados.
     *                              </body>
     */
    public MessageServer() {
        //? 1. Intentamos inicializar la conexion al servidor
        try {
            initMessageServerConnection();
        } catch (Exception eOver) {
            eOver.printStackTrace();
            System.err.println("Fatal Error 0x000 - [Raised] No se pudo inicializar la conexion " +
                                       "al servidor del lado del server para los mensajes.");
        }
        //? 2. Intentamos inicializar la conexion con la base de datos
        try {
            this._DatabaseManagementSystem = new DatabaseManagementSystem();
            this.ex_AllSentMessagesProperty =
            FXCollections.observableList(
                        this._DatabaseManagementSystem.pollAllMessagesInDatabase());
            this.ex_AllUsernamesProperty = FXCollections.observableList(
                        this._DatabaseManagementSystem.pollAllRegisteredUsersInDatabase());
            this.ex_RegisteredUsersProperty =
                    new SimpleStringProperty( "Server Registered Users: "
                                                      + ex_AllUsernamesProperty.size());
            this.ex_ConnectedUsersProperty =
                    new SimpleStringProperty("Server Connected Users: " +
                                                      ex_ServerConnectedUsers);
            this.ex_FilteredMessagesProperty = FXCollections.observableList(new ArrayList<>());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Fatal Error 0x000 - [Raised] No se pudo inicializar la conexion " +
                                       "con la base de datos del lado del server para los " +
                                       "mensajes.");
        }
        //? 3. Inicializamos el contador de usuarios registrados
        try {
            this.ex_ServerRegisteredUsers =
                    this._DatabaseManagementSystem
                            .pollAllClientUsernamesInDatabase()
                            .size();
        } catch (Exception e) {
            System.err.println("Error initializing server: " + e.getMessage());
            this.ex_ServerRegisteredUsers = 0;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                _serviceForDatabase.shutdown();
                if (!_serviceForDatabase.awaitTermination(60, TimeUnit.SECONDS)) {
                    _serviceForDatabase.shutdownNow();
                }
            } catch (InterruptedException e) {
                _serviceForDatabase.shutdownNow();
            }
        }));

    }

    /**
     * <body style="color:white;">
     * Este metodo privado y final se utiliza para inicializar la conexion del servidor de mensajes.
     * La funcionalidad principal consiste en crear un objeto {@link ServerSocket} que escucha
     * conexiones entrantes en la direccion IP y puerto previamente definidos.
     * <p>
     * La direccion y el puerto se obtienen de las propiedades {@link #ex_ServerConnectionAddress} y
     * {@link #ex_ServerConnectionPort} respectivamente. El metodo usa las capacidades de la clase
     * {@link InetAddress} para obtener una representacion de la direccion IP en uso.
     * <p>
     * En caso de que ocurra una excepcion de entrada/salida ({@link IOException}) al intentar
     * inicializar el {@link ServerSocket}, esta sera capturada y reempaquetada en una excepcion
     * {@link RuntimeException}, deteniendo la ejecucion del servidor debido a un error critico.
     * @throws RuntimeException si no es posible inicializar el {@link ServerSocket}. Esta excepcion
     *                          encapsula una {@link IOException}.
     *                          </body>
     */
    public final void initMessageServerConnection() {
        try {
            this._ConnectionServer =
                    new ServerSocket(100,
                                     100,
                                     InetAddress
                                             .getByName(this.ex_ServerConnectionAddress));
            if (_ConnectionServer.isBound()){
                this.ex_ServerConnectionPort = _ConnectionServer.getLocalPort();
                this.ex_ServerConnectionPortString = ex_ServerConnectionPort.toString();
                this.ex_ConnectionAddressProperty =
                        new SimpleStringProperty("Server Connection Address :"
                             + this.ex_ServerConnectionAddress);
                this.ex_ConnectionPortProperty =
                        new SimpleStringProperty("Server Connection Port :"
                             + this.ex_ServerConnectionPortString);
                this._ServerIsRunning.set(true);
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Fatal Error 0x001 - [Raised] No se pudo inicializar la conexion " +
                                       "al servidor del lado del server para los mensajes.");
            throw new RuntimeException(e);
        }
    }

    /*! Getters requeridos con binding a la propiedad del sistema*/

    /**
     * <body style="color:white;">
     * Devuelve la propiedad {@link SimpleStringProperty} que almacena la direccion del servidor.
     * Utiliza el concepto de JavaFX Properties para realizar binding entre la interfaz de usuario y
     * los valores del modelo subyacente. Este metodo es particularmente util para observar cambios
     * en tiempo real en la interfaz grafica respecto a la direccion del servidor.
     *
     * @return {@link SimpleStringProperty} que representa la direccion del servidor.
     * </body>
     */
    public SimpleStringProperty getEx_ConnectionAddressProperty() {
        return this.ex_ConnectionAddressProperty;
    }
    /**
     * <body style="color:white;">
     * Devuelve la direccion del servidor como un String simple.
     *
     * @return {@link String} que representa la direccion del servidor.
     * </body>
     */
    public String getEx_ServerConnectionAddress() {
        return this.ex_ServerConnectionAddress;
    }
    /**
     * <body style="color:white;">
     * Devuelve la propiedad {@link SimpleIntegerProperty} que almacena el puerto del servidor. Este
     * metodo permite realizar binding con la interfaz grafica para que los cambios sean reflejados
     * automaticamente.
     *
     * @return {@link SimpleIntegerProperty} que representa el puerto del servidor.
     * </body>
     */
    public SimpleStringProperty getEx_ConnectionPortProperty() {
        return this.ex_ConnectionPortProperty;
    }
    /**
     * <body style="color:white;">
     * Devuelve el puerto del servidor como un Integer.
     *
     * @return {@link Integer} que representa el puerto del servidor.
     * </body>
     */
    public Integer getEx_ServerConnectionPort() {
        return this.ex_ServerConnectionPort;
    }
    /**
     * <body style="color:white;">
     * Devuelve la propiedad {@link SimpleIntegerProperty} que contiene el numero de usuarios
     * conectados al servidor en tiempo real. Ideal para hacer binding con la UI y mostrar
     * visualmente el estado del servidor.
     *
     * @return {@link SimpleIntegerProperty} que representa el numero de usuarios conectados.
     * </body>
     */
    public SimpleStringProperty getEx_ConnectedUsersProperty() {
        return this.ex_ConnectedUsersProperty;
    }
    /**
     * <body style="color:white;">
     * Devuelve el numero de usuarios actualmente conectados al servidor como un Integer.
     *
     * @return {@link Integer} que representa el numero actual de usuarios conectados.
     * </body>
     */
    public Integer getEx_ServerConnectedUsers() {
        return this.ex_ServerConnectedUsers;
    }
    /**
     * <body style="color:white;">
     * Devuelve la propiedad {@link SimpleIntegerProperty} que contiene el numero de usuarios
     * registrados en la base de datos del servidor. Esta propiedad puede ser observada para
     * mantener sincronizacion con la UI.
     *
     * @return {@link SimpleIntegerProperty} que representa el numero de usuarios registrados.
     * </body>
     */
    public SimpleStringProperty getEx_RegisteredUsersProperty() {
        return this.ex_RegisteredUsersProperty;
    }
    /**
     * <body style="color:white;">
     * Devuelve el numero total de usuarios registrados en la base de datos del servidor como un
     * Integer. Este dato es obtenido al inicializar el servidor y se actualiza conforme los
     * usuarios se registran.
     *
     * @return {@link Integer} que representa el numero total de usuarios registrados.
     * </body>
     */
    public Integer getEx_ServerRegisteredUsers() {
        return this.ex_ServerRegisteredUsers;
    }
    /**
     * <body style="color:white;">
     * Devuelve una propiedad observable {@link SimpleObjectProperty} que contiene una lista de
     * todos los mensajes enviados actualmente almacenados en la base de datos del servidor. Este
     * metodo utiliza el concepto de JavaFX Properties, lo que permite realizar binding entre los
     * datos del servidor y la interfaz de usuario. Esto garantiza que cualquier cambio en los datos
     * subyacentes se vea reflejado en la UI en tiempo real.
     * <p>
     * El metodo se basa en referencias directas a objetos de tipo {@link List} para encapsular
     * objetos de transferencia de datos ({@link MessageDTO}) que representan los mensajes enviados
     * almacenados hasta el momento.
     *
     * @return {@link SimpleObjectProperty} que encapsula una lista de mensajes enviados
     * ({@link MessageDTO}).
     * @throws NullPointerException si la propiedad fue inicializada como null antes de ser
     *                              utilizada.
     *                              </body>
     */
    public ObservableList<MessageDTO> getEx_AllSentMessagesProperty() {
        return this.ex_AllSentMessagesProperty;
    }
    /**
     * <body style="color:white;">
     * Devuelve una propiedad observable {@link SimpleObjectProperty} que contiene una lista de
     * todos los usuarios registrados en el servidor. Este metodo utiliza el mecanismo de JavaFX
     * Properties para habilitar el binding directo con la interfaz de usuario, lo que facilita el
     * reflejo de cambios en tiempo real en la aplicacion cliente o del servidor.
     * <p>
     * La lista encapsula instancias de {@link ClientDTO}, que son objetos de transferencia de datos
     * disenados para contener informacion relevante sobre los usuarios registrados, como nombres de
     * usuario y UUIDs.
     *
     * @return {@link SimpleObjectProperty} que encapsula una lista de usuarios registrados
     * ({@link ClientDTO}).
     * @throws NullPointerException si la propiedad fue inicializada como null antes de ser
     *                              utilizada.
     *                              </body>
     */
    public ObservableList<ClientDTO> getEx_AllUsernamesProperty() {
        return this.ex_AllUsernamesProperty;
    }


    /**
     * <body style="color:white;">
     * Metodo que devuelve una propiedad observable {@link ObservableList} que contiene una lista de
     * los mensajes filtrados basados en ciertos criterios especificos. La lista observable permite
     * realizar actualizaciones en tiempo real entre el modelo y la interfaz de usuario (UI),
     * manteniendo los datos sincronizados sin necesidad de actualizaciones manuales
     * independientes.
     * @return {@link ObservableList} que contiene instancias de {@link MessageDTO}, cada una
     * representando un mensaje específico filtrado.
     * @throws NullPointerException si la propiedad {@link #ex_FilteredMessagesProperty} no se ha
     *                              inicializado correctamente antes de su uso.
     *                              </body>
     */
    public ObservableList<MessageDTO> getEx_FileteredMessagesProperty() {
        return this.ex_FilteredMessagesProperty;
    }

    /*! Funciones internas para el manejo de usuarios por secciones*/
    /*
     ? Los metodos detallados a continuacion, permiten al server manejar conexiones de usuarios,
     ? y la autentificacion de los mismos.
     */

    /**
     * <body style="color:white;">
     * Metodo que maneja la aceptación de conexiones de clientes al servidor. Su objetivo es
     * escuchar continuamente por nuevas conexiones a traves del socket del servidor y delegar el
     * proceso de autenticacion y registro de clientes. Los clientes autenticados con exito se
     * procesan a traves de una arquitectura multihilo para permitir una mayor concurrencia.
     * <p>
     * La logica del flujo incluye:
     * <ul>
     *     <li>Esperar que el servidor este activo y aceptar conexiones entrantes.</li>
     *     <li>Registrar la direccion y puerto del cliente entrante.</li>
     *     <li>Delegar la autenticacion del cliente a un subproceso independiente.</li>
     *     <li>Si la autenticacion se logra, iniciar el ClientHandler para gestionar
     *     comunicaciones.</li>
     *     <li>Si la autenticacion <b>no se logra, el programa cierra ese socket
     *     informandole al usuario del error. En este caso el usuario debe reintentar la
     *     conexion al socket, creando otro objeto en su parte.</b></li>
     * </ul>
     * Este metodo utiliza un {@link ExecutorService} de tipo Cached para la
     * administracion
     * dinamica de esas tareas separadas.
     * </p>
     *
     * @throws IOException      si ocurre un problema al aceptar la conexion del cliente.
     * @throws RuntimeException si el servidor deja de estar en funcionamiento de forma inesperada.
     *                          <p>
     *                          La conexión del cliente se delega a
     *                          {@link #attemptToAuthenticateAClient(Socket)}, que ejecuta el flujo
     *                          de autenticación usando streams de entrada/salida y validaciones
     *                          lineales.
     *                          <p>
     *                          Requisitos previos: El servidor debe estar encendido y en un estado
     *                          funcional (reflejados en {@link #_ServerIsRunning}).
     *                          </body>
     */
    public final void attemptToAcceptClientConnectionRequests() {
        while (this._ServerIsRunning.get()) {
            Socket clientConnectionSocket;
            try {
                clientConnectionSocket = this._ConnectionServer.accept();
                System.out.println("[ServerSideCommns] Socket Connection: Se registro una nueva " +
                                  "conexion desde " +
                                  "[" + clientConnectionSocket.getInetAddress() + "]" +
                                  " con puerto [" +
                                  clientConnectionSocket.getPort()
                                  + "] hacia el servidor!");

                final Socket finalClientSocket = clientConnectionSocket;
                _serviceForClients.submit(() -> {
                    try {
                        ClientHandler handler =
                                attemptToAuthenticateAClient(finalClientSocket);
                        updateServerSideClientCount();
                        if (handler != null) {
                            handler.run();
                        } else {
                            try {
                                ObjectOutputStream out =
                                        new ObjectOutputStream(finalClientSocket.getOutputStream());
                                out.writeObject("[Authentication Result] Autenticacion Fallida : "
                                                        + "Please try connecting again.");
                                out.flush();
                                finalClientSocket.close();
                            } catch (IOException e) {
                                System.err.println("[ServerSideCommns] Error closing failed " +
                                                           "authentication socket: "
                                                           + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[ServerSideComms] Error al procesar el socket del " +
                                                   "cliente:" +
                                                   " " + e.getMessage());
                        e.printStackTrace();
                        try {
                            finalClientSocket.close();
                        } catch (IOException closeError) {
                            System.err.println("[ServerSideComms] Error al cerrar el socket del " +
                                                       "cliente: "
                                                       + closeError.getMessage());
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                System.out.println("[ServerSideComms] Fatal Error 0x0001 - [Raised] El servidor " +
                                           "encontro un" +
                                           " " +
                                           "error al conectarse con el socket del cliente.");
            }
        }
    }


    /**
     * <body style="color:white;">
     * Metodo que intenta autenticar a un cliente que ha establecido conexion con el servidor.
     * Utiliza flujos de entrada/salida para comunicarse con el cliente y valida las credenciales
     * proporcionadas en base a los datos almacenados en la base de datos.
     * <p>
     * El flujo general sigue estos pasos:
     * <ul>
     *     <li>Se establece un canal de comunicacion bidireccional con el cliente usando
     *     {@link ObjectOutputStream} y {@link ObjectInputStream}.</li>
     *     <li>Se lee el comando inicial del cliente para verificar si se trata de una solicitud de
     *     autenticacion valida. Si no es valida, se envia un mensaje de error y la conexion se
     *     cierra.</li>
     *     <li>Se genera un "ACKNOWLEDGE" al cliente confirmando la recepcion de la solicitud de
     *     autenticacion y se procede a leer las credenciales proporcionadas.</li>
     *     <li>Se sincroniza el acceso a la base de datos para buscar la informacion del cliente
     *     basada en el nombre de usuario proporcionado. Si no se encuentra, la autenticacion
     *     falla.</li>
     *     <li>Se valida la contrasena proporcionada usando {@link AuthenticatorEngine} y los datos
     *     encriptados del cliente almacenados en la base de datos.</li>
     *     <li>Si la autenticacion es exitosa, se devuelve un {@link ClientHandler} configurado para
     *     manejar las futuras comunicaciones del cliente en el servidor.</li>
     *     <li>Si la autenticacion falla en cualquier punto del flujo, se envia un mensaje de error
     *     al cliente antes de cerrar el socket.</li>
     * </ul>
     * </p>
     *
     * @param externalClientSocket {@link Socket} del cliente que solicita la autenticacion. Debe
     *                             estar previamente inicializado y conectado al servidor.
     * @return {@link ClientHandler} configurado para gestionar las comunicaciones del cliente si la
     * autenticacion fue exitosa; {@code null} si la autenticacion fallo.
     * @throws IOException            si ocurre un problema al leer o escribir en el canal de
     *                                comunicacion entre el cliente y el servidor.
     * @throws ClassNotFoundException si ocurre un problema al leer un objeto de los flujos de
     *                                entrada debido a que la clase no se encuentra.
     * @see ObjectOutputStream
     * @see ObjectInputStream
     * @see AuthenticationRequestDTO
     * @see ClientDTO
     * </body>
     */
    private ClientHandler attemptToAuthenticateAClient(Socket externalClientSocket) {
        try {
            ObjectOutputStream clientOutputStream =
                    new ObjectOutputStream(externalClientSocket.getOutputStream());
            ObjectInputStream clientInputStream =
                    new ObjectInputStream(externalClientSocket.getInputStream());

            //? Leemos el mensaje del cliente que debe ser especificamente un mensaje de 
            //? AUTHENTICATION REQUEST
            String clientCommand = clientInputStream.readUTF();
            System.out.println("[ServerSideComms] Comando Recibido: " + clientCommand);

            if (!clientCommand.equals(
                    UsefulCommunicationMessages
                            .POST_CLIENT_AUTHENTICATION_REQUEST
                            .get_message())) {
                System.err.println("[ServerSideComms] Error en la recepcion del comando de " +
                                  "autenticacion, se espereba un REQUEST_AUTHENTICATION, " +
                                           "se obtuvo: " + clientCommand);
                retornarUnResponseDTODeFailure(clientOutputStream,
                                  "Error en la recepcion del " +
                                           "comando de autenticacion," +
                                           " se espereba un REQUEST_AUTHENTICATION, se obtuvo: " 
                                           + clientCommand);
                return null;
            }

            //? 2. Escribimos hacia el usuario el ACKNOWLEDGE de la conexion y de la request de 
            //? autenticacion
            clientOutputStream.writeUTF(
                    UsefulCommunicationMessages
                            .POST_CLIENT_AUTHENTICATION_REQUEST_ACKNOWLEDGEMENT
                            .get_message());
            clientOutputStream.flush();

            AuthenticationRequestDTO readInAuthRequest = (AuthenticationRequestDTO) 
                    clientInputStream.readObject();
            System.out.println("[ServerSideComms] Informacion Recibida Del Cliente :: " 
                                       + readInAuthRequest.clientPOJO());

            //? 3. Como el proceso de autenticacion es la unica parte lineal del sistema, 
            //? sincronizamos el accesso a la base de datos directamente
            Optional<ClientDTO> resultOpt;
            synchronized (_DatabaseManagementSystem) {
                resultOpt = _DatabaseManagementSystem
                        .pollAllRegisteredInformationPerUsernameInDatabase(
                        readInAuthRequest.getClientUsername());
            }

            if (resultOpt.isEmpty()) {
                retornarUnResponseDTODeFailure(clientOutputStream, 
                                  "[ServerSideComms] Authentication Failure:" +
                                  " El usuario no fue encontrado en " +
                                  "la base de datos para su validacion.");
                return null;
            }

            ClientDTO clientDTO = resultOpt.get();
            boolean isValidClient = AuthenticatorEngine.validateProvidedClearPassword(
                      readInAuthRequest.getClientClearPwd(),
                    new EncryptionEngine.HashedPasswordDTO(
                        clientDTO._clientPwdHash(),
                       clientDTO._clientSaltHash()));

            if (!isValidClient) {
                System.out.println("[ServerSideComms] Autenticacion Fallilda: Contrasena " +
                                           "Incorrecta" +
                                           " " +
                                           "para cliente [" 
                                           + readInAuthRequest.getClientUsername() 
                                           + "]");
                retornarUnResponseDTODeFailure(clientOutputStream,
                                               "Contrasena o Usuario Incorrecto, " +
                                                       "Intentar de nuevo.");
                return null;
            }

            //? 4. Si el cliente es valido, entonces debemos retornar la informacion del success
            //? en forma de un objeto tambien
            System.out.println("[ServerSideComms] Autenticacion Pasada para  " +
                                       "ssuario [" 
                                       + readInAuthRequest.getClientUsername() 
                                       + "] autenticado y habilitado!");
            AuthenticationRequestDTO.AuthenticationResponseDTO success = 
                    AuthenticationRequestDTO.AuthenticationResponseDTO
                            .success(readInAuthRequest.getClientUsername());
            clientOutputStream.writeObject(success);
            clientOutputStream.flush();

            System.out.println("[ServerSideComms] Printing DTO from db ");
            System.out.println(clientDTO);

            /*
             ? Por motivos de sincroniazacion de los hilos, al parecer el objeto del DTO completo
             ? con los hashes sobrecargaba al socket y causaba que este no respondiera 
             ? correctamente, por tanto se envia solo el UUID para la conformacion de mensajes en 
             ? lugar del objeto
             */
            clientOutputStream.writeUTF(clientDTO._clientUUID());
            clientOutputStream.flush();

            ClientHandler handlerForClientConnection = new ClientHandler(
                    externalClientSocket,
                    clientInputStream,
                    clientOutputStream,
                    clientDTO._clientUUID(),
                    readInAuthRequest.getClientUsername()
            );

            _connectedClients.put(clientDTO._clientUUID(), 
                                  handlerForClientConnection);
            _usernameToUuidMap.put(readInAuthRequest.getClientUsername(), 
                                   clientDTO._clientUUID());
            
            
            return handlerForClientConnection;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("[ServerSideComms] Error Durante Autenticacion " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }


    /**
     * <body style="color:white;">
     * Este metodo se utiliza para enviar una respuesta de autenticacion fallida al cliente a traves
     * de su {@link ObjectOutputStream}. El objetivo principal es encapsular el mensaje de error en
     * un objeto {@link AuthenticationRequestDTO.AuthenticationResponseDTO} y enviarlo al cliente,
     * asegurando la sincronia en la comunicacion entre cliente y servidor.
     *
     * <p>
     * El metodo realiza tres operaciones en secuencia:
     * <ul>
     *     <li>Crea un objeto de respuesta de fallo utilizando la fabrica de metodos
     *     {@link AuthenticationRequestDTO.AuthenticationResponseDTO#failure(String)}.</li>
     *     <li>Escribe el objeto en el stream de comunicacion usando {@link ObjectOutputStream#writeObject(Object)}.</li>
     * </ul>
     * </p>
     *
     * @param outputStream El stream de salida utilizado para enviar mensajes al cliente. Debe estar
     *                     inicializado y no ser null.
     * @param message      El mensaje de error que sera enviado al cliente como parte de la
     *                     respuesta de fallo.
     * @throws IOException Si ocurre un problema al escribir en el {@link ObjectOutputStream} o al
     *                     intentar resetearlo.
     * @see AuthenticationRequestDTO.AuthenticationResponseDTO#failure(String)
     * @see ObjectOutputStream#writeObject(Object)
     * @see ObjectOutputStream#reset()
     * </body>
     */
    private void retornarUnResponseDTODeFailure(ObjectOutputStream outputStream,
                                                String message) throws IOException {
        outputStream.writeObject(AuthenticationRequestDTO
                                         .AuthenticationResponseDTO
                                         .failure(message));
        outputStream.flush();
        outputStream.reset();
    }


    /**
     * <body style="color:white;">
     * Este metodo es utilizado para actualizar la cantidad de usuarios conectados en el servidor en
     * tiempo real. Utiliza la herramienta {@link Platform#runLater(Runnable)} de JavaFX para
     * realizar la actualizacion en el hilo de la interfaz grafica, asegurando que no existan
     * conflictos al modificar la propiedad observable {@link SimpleIntegerProperty} usada para el
     * conteo de usuarios.
     * <p>
     * Este metodo debe ser invocado cada vez que exista una conexion exitosa de un nuevo cliente al servidor.
     * </p>
     */
    private void updateServerSideClientCount() {
        //  La idea de este metodo es que el servidor solo tiene que actualizar dos partes de la
        //  UI en cualquier momento, si se registra un cliente o si entra un nuevo cliente. Este
        //  metodo se encarga de la segunda parte, si un cliente entra, enviamos una update a la
        //  UI desde aqui
        String updatedCount = "Server Connected Users: " + _connectedClients.size();
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> {
                ex_ConnectedUsersProperty.set(updatedCount);
            });
        } else {
            ex_ConnectedUsersProperty.set(updatedCount);
        }
    }


    /**
     * <body style="color:white;">
     * Este metodo se encarga de enviar notificaciones de actualizacion a la interfaz de usuario (UI) de uno
     * o varios clientes especificados mediante sus UUIDs.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Valida la entrada para asegurarse de que los UUIDs proporcionados no sean nulos ni vacios.</li>
     *     <li>Itera por cada UUID proporcionado, buscando un {@link ClientHandler} asociado al cliente que este conectado.</li>
     *     <li>Si se encuentra un {@link ClientHandler}, se llama al metodo {@code processCommand} del manejador para enviar
     *     las notificaciones requeridas. Estas notificaciones informan a la UI del cliente que la lista
     *     de mensajes y la lista de usuarios han sido actualizadas.</li>
     *     <li>Captura y registra cualquier excepcion que ocurra durante el envio de los comandos para evitar que
     *     errores con un cliente especifico interfieran con los demas.</li>
     * </ul>
     * @param clientUUIDs Uno o mas UUIDs de los clientes (identificadores unicos) a los que se desea
     *                    enviar la notificacion de actualizacion de UI. Puede ser un arreglo vacio o {@code null},
     *                    caso en el cual el metodo simplemente termina su ejecucion sin realizar cambios.
     *
     * @throws NullPointerException Si alguno de los UUIDs proporcionados como parametro no es valido o no existe en el
     * mapa de clientes conectados. (Solo se lanza internamente en caso de errores inesperados, pero no es manejado directamente).
     *
     * @implNote Este metodo por el momento no se utiliza, se deja implementado y tiene utilidad,
     * pero para el proyecto, el proceso de comunicar los mensajes de actualizacion al cliente se
     * transforma en un metodo de comunicacion adicional mucho mas completo, por tanto se deja
     * implementadas las bases pero no el metodo completamente.
     * @see ClientHandler
     * @see UsefulCommunicationMessages
     *
     * </body>
     */
    private void dispatchClientUIUpdateMessage(String... clientUUIDs) {
        /*! Revision Base: Si no tenemos clientes, o si la longitud de aquellos UUIDs a modificar
         * es en realidad nula, no realizamos nada*/
        if (clientUUIDs == null || clientUUIDs.length == 0) {
            return;
        }

        // 1. Por cada UUID buscamos si tiene un handler en el mapa concurrente, si este existe
        // entonces comunicamos la actualizacion de estos hacia ellos, si no esta entonces no
        // realizamos nada.
        for (String clientUUID : clientUUIDs) {
            ClientHandler handler = this._connectedClients.get(clientUUID);
            if (handler != null) {
                try {
                    //? 2. Enviamos el mensaje de actualizacion de cada una de las UIs, como no
                    // se ha implementado estos metodos no hacen llegar ninguna comunicacion al
                    // cliente
                    handler.processCommand(
                            UsefulCommunicationMessages
                                    .POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE
                                    .get_message());
                    handler.processCommand(
                            UsefulCommunicationMessages
                                    .POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE
                                    .get_message());
                } catch (Exception e) {
                    System.out.println("[ServerSideComms] Error sending UI update " +
                                               "notification to client "
                                               + clientUUID + ": " + e.getMessage());
                }
            }
        }
    }


    /**
     * <body style="color:white;">
     * Este metodo actualiza la interfaz de usuario (UI) del servidor con la informacion mas reciente
     * almacenada en la base de datos. Realiza peticiones asincronas para obtener los datos y actualiza los
     * elementos observables vinculados a la UI de JavaFX.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Ejecuta el metodo dentro del hilo de JavaFX con {@link Platform#runLater(Runnable)} para
     *     garantizar la seguridad del hilo al actualizar la UI.</li>
     *     <li>Primero realiza peticiones concucrrentes y asincronas a la base de datos mediante la API de
     *     {@link CompletableFuture} para obtener:
     *         <ul>
     *             <li>La lista de mensajes almacenados en la base de datos.</li>
     *             <li>La lista de usuarios registrados en la base de datos.</li>
     *         </ul>
     *     </li>
     *     <li>Ambas peticiones son procesadas en un grupo de hilos configurado mediante {@code _serviceForDatabase}.</li>
     *     <li>Cuando ambas peticiones concluyen (sincronia lograda con {@link CompletableFuture#allOf}),
     *     la respuesta se procesa y los datos obtenidos se asignan a propiedades observables de la interfaz:
     *         <ul>
     *             <li>{@code ex_AllUsernamesProperty}: Se actualiza con los usuarios registrados.</li>
     *             <li>{@code ex_AllSentMessagesProperty}: Se actualiza con los mensajes almacenados.</li>
     *             <li>{@code ex_RegisteredUsersProperty}: Se actualiza con el conteo de usuarios registrados.</li>
     *         </ul>
     *     </li>
     *     <li>En caso de error, se maneja con {@link CompletableFuture#exceptionally} para registrar el problema
     *     y evitar la interrupcion del sistema.</li>
     * </ul>
     * @throws Exception Este metodo puede capturar y manejar internamente cualquier excepcion relacionada
     * con fallos en la obtencion de datos desde la base de datos o con la actualizacion de la UI. Igualmente
     * maneja errores imprevistos mediante manejo de excepciones en bloques {@code try-catch}.
     *
     * @see Platform#runLater(Runnable)
     * @see CompletableFuture
     * @see CompletableFuture#supplyAsync(Supplier, Executor)
     * @see CompletableFuture#join()
     * @see CompletableFuture#exceptionally(Function)
     * </body>
     */
    private void dispatchServerUIUpdateMessage() {
        Platform.runLater(() -> {
            try {
                //? 1. Para trabajar con la base de datos, las lecturas usualmente pueden ser
                // concurrentes y asincronas, ya que derby soporta mas de un hilo a la vez
                // intentando agarrar informacion. Para probar se trabajo con clientes
                // sequenciales porque es imposible escribir al mismo tiempo en la misma
                // computadora. No obstante este metodo deberia de funcionar sin problemas para
                // acceso concurrente al ser solo lecturas.
                CompletableFuture<List<MessageDTO>> messagesFuture =
                        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return _DatabaseManagementSystem.pollAllMessagesInDatabase();
                            } catch (Exception e) {
                                System.err.println("[ServerSideComms] Error al retornar valores " +
                                                           "de la base de datos: "
                                                           + e.getMessage());
                                System.out.println("e.getMessage() = " + e.getMessage());
                                System.out.println("e.getCause() = " + e.getCause());
                                return List.of();
                            }
                        }, _serviceForDatabase);

                //? 2. Hacemos la misma llamada pero ahora para los usuarios registrados
                CompletableFuture<List<ClientDTO>> clientsFuture = CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return _DatabaseManagementSystem.pollAllRegisteredUsersInDatabase();
                            } catch (Exception e) {
                                System.err.println("Error fetching clients: " + e.getMessage());
                                System.out.println("e.getMessage() = " + e.getMessage());
                                System.out.println("e.getCause() = " + e.getCause());
                                return List.of(); // Return empty list on error
                            }
                        }, _serviceForDatabase);

                //? 3. Esperamos a que los dos terminen y retornamos un grupo de completable
                // future, es decir retornamos un grupo en donde los resultados anteriores se
                // agrupan en un mismo retorno
                CompletableFuture.allOf(messagesFuture, clientsFuture)
                        .thenAccept(new Consumer<Void>() {
                            @Override
                            public void accept(Void unused) {
                                List<MessageDTO> latestMessages = messagesFuture.join();
                                List<ClientDTO> latestClients = clientsFuture.join();
                                //? 4. Enviamos la actualizacion a la UI del servidor, en
                                // realidad si este comando se usa desde consola el problema se
                                // da que no es un hilo de javafx, pero no tiene problemas en la
                                // aplicacion de JavaFX.
                                Platform.runLater(() ->{
                                    /*! Usernames Property para menus y selectores*/
                                    ex_AllUsernamesProperty.clear();
                                    ex_AllUsernamesProperty.addAll(latestClients);
                                    /*! Messages property para la lista de mensajes principal*/
                                    ex_AllSentMessagesProperty.clear();
                                    ex_AllSentMessagesProperty.addAll(latestMessages);
                                    //! Usuarios registrados en caso de que se trate de una
                                    //   actualizacion de clientes
                                    ex_RegisteredUsersProperty.set("Server Registered Uses: "
                                       + latestClients.size());
                                    System.out.println("[ServerSideUI] Actualizacion de " +
                                                               "Informacion: " +
                                                               latestMessages.size() + " " +
                                                               "mensajes, " +
                                                               latestClients.size()
                                                               + " clientes");
                                });
                            }
                        })
                        /*
                         * Utilizamos exceptionally para manejar cualquier excepcion lanzada
                         * dentro de los completable futures, esto nos permite manejar cualquier
                         * salida erronea y obtener la excepcion lanzada, asi en debug mode
                         * podemos ver que sucedio internamente
                         */
                        .exceptionally(throwable -> {
                            Platform.runLater( () -> {
                                System.err.println("[ServerSideUI] Actualizacion de la UI fallo: "
                                                           + throwable.getMessage());
                                System.out.println("throwable.getMessage() = "
                                                           + throwable.getMessage());
                                System.out.println("throwable.getCause() = "
                                                           + throwable.getCause());
                                    throwable.printStackTrace();
                            });
                            return null;
                        });
            } catch (Exception e) {
                System.err.println("[ServerSideUI] Fallo critico durante actualizacion de la UI: "
                                           + e.getMessage());
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                e.printStackTrace();
            }
        });
    }

    /**
     * <body style="color:white;">
     * Este metodo registra un nuevo usuario en la base de datos de manera asincrona utilizando
     * un {@link CompletableFuture}. Se asegura de que las operaciones de registro no interfieran
     * con otras funcionalidades del servidor, como el manejo de clientes, mensajes o filtrado.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Se inicia un {@link CompletableFuture#supplyAsync(Supplier)} que ejecuta la logica de registro en un hilo
     *     diferente, gestionado por el pool de hilos configurado para la base de datos.</li>
     *     <li>Se convierte el objeto {@link ClientPOJO} recibido como parametro en un objeto {@link ClientDTO} para
     *     que sea compatible con el sistema de gestion de base de datos.</li>
     *     <li>Se verifica si el usuario ya esta registrado en la base de datos mediante una consulta:
     *         <ul>
     *             <li>Si el usuario ya existe, se registra un mensaje en consola y el metodo retorna {@code false}.</li>
     *             <li>Si el usuario no existe, se realiza un intento de insercion en la base de datos.</li>
     *         </ul>
     *     </li>
     *     <li>Si la insercion en la base de datos es exitosa, se ejecutan metodos asociados para actualizar la
     *     interfaz de usuario, tales como {@code dispatchServerUIUpdateMessage} y
     *     {@code dispatchClientUIUpdateMessage}, entre otros.</li>
     *     <li>En caso de error, se captura la excepcion y se lanza una {@link CompletionException} para su manejo
     *     en el flujo del {@link CompletableFuture}.</li>
     *     <li>Se utiliza {@link CompletableFuture#exceptionally(Function)} para capturar y registrar detalles de errores
     *     ocurridos durante el proceso asincrono.</li>
     * </ul>
     * @param externalClientPOJOFromUI El {@link ClientPOJO} que representa los datos del cliente a registrar.
     *                                 Este objeto proviene de la interfaz de usuario.
     *
     * @return Un {@link CompletableFuture} que se completa con un valor {@code true} si el registro fue exitoso,
     *         o {@code false} si el usuario ya existia en la base de datos o si ocurrieron errores durante el registro.
     *
     * @throws CompletionException Si ocurre un error durante el proceso de registro, ya sea por una falla en la
     * base de datos o por otra excepcion inesperada. Esta excepcion se envuelve y se lanza desde el flujo
     * del {@link CompletableFuture}.
     *
     * @see CompletableFuture
     * @see ClientPOJO
     * @see ClientDTO
     * </body>
     */
    public final CompletableFuture<Boolean> registerNewUserInDatabase(
            ClientPOJO externalClientPOJOFromUI){
        //? 1. Este metodo se encarga de registrar un nuevo usuario en la base de datos
        // Para registrar usamos un completable future de tipo supplyAsync ya que el registro no
        // debe interferir con ninguna parte del servidor, como por ejemplo una request adicional
        // de ingreso de clientes, filtrado, o el registro de un mensaje. De este modo, usamos la
        // base de datos de manera asincrona controlada.
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        //! 2. Convertimos de ClientPOJO a Client DTO para el sistema de la db
                        ClientDTO clientDTOFromParsedPOJO =
                                externalClientPOJOFromUI.transformIntoDTO();

                        Optional<ClientDTO> isUserAlreadyInDatabase =
                                _DatabaseManagementSystem
                                .pollAllRegisteredInformationPerUsernameInDatabase(
                                        externalClientPOJOFromUI
                                                .get_clientUsername());
                        if (isUserAlreadyInDatabase.isPresent()){
                            System.out.println("[ServerSideComms] Usuario Ya registrado en la " +
                                                       "base de datos, no se ha procedido con el " +
                                                       "registro");
                            return false;
                        }
                        //? 3. Intentamos enviarlo a la db
                        boolean registrationWasSuccessful =
                                _DatabaseManagementSystem
                                        .insertClientCreatedIntoDatabase(
                                                clientDTOFromParsedPOJO);

                        if (!registrationWasSuccessful){
                            throw new CompletionException("[ServerSideComms] Error Durante " +
                                                                  "Registro de Cliente: no se han" +
                                                                  " realizado cambios en el " +
                                                                  "sistema de la db", null);
                        }

                        System.out.println("[ServerSideComms] Usuario registrado correctamente : "
                                                   + clientDTOFromParsedPOJO.getClientUsername());

                        //? 4. Realizamos llamadas a todos los metodos de actualizacion de la UI
                        dispatchServerUIUpdateMessage();
                        dispatchClientUIUpdateMessage();
                        updateServerSideClientCount();
                        return registrationWasSuccessful;
                    } catch (Exception e) {
                        System.out.println("[ServerSideComms] Error durante el registro del " +
                                                   "cliente a la base de datos de tipo inesperado" +
                                                   " : " +
                                                   e.getMessage() + "\n");
                        System.out.println("e.getCause() = " + e.getCause());
                        throw new CompletionException(e.getMessage(), e);
                    }
                }).exceptionally(new Function<Throwable, Boolean>() {
                        @Override
                        public Boolean apply(Throwable throwable) {
                            System.out.println("[ServerSideComms] Error during registration of user : " +
                                                       throwable.getMessage() + "\n");
                            return false;
                        }
        });
    }



    /**
     * <body style="color:white;">
     * Este metodo elimina un usuario del sistema, incluyendo sus datos en la base de datos y su
     * conexion activa si existiera. La operacion se realiza de manera asincrona para no bloquear
     * el hilo principal del servidor.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Ejecuta la operacion de eliminacion de forma asincrona utilizando
     *     {@link CompletableFuture#supplyAsync(Supplier, Executor)} con un ejecutor dedicado
     *     ({@code _serviceForDatabase}).</li>
     *     <li>Primero verifica si el nombre de usuario proporcionado es nulo, en cuyo caso
     *     arroja una excepcion.</li>
     *     <li>Si el usuario esta actualmente conectado (existe en {@code _usernameToUuidMap}):
     *         <ul>
     *             <li>Cierra la conexion del cliente usando {@code cierreDeRecursosInesperado()}.</li>
     *             <li>Elimina la referencia del cliente conectado de las estructuras de datos internas.</li>
     *         </ul>
     *     </li>
     *     <li>Procede a eliminar los datos del usuario de la base de datos mediante dos operaciones:
     *         <ul>
     *             <li>Eliminacion de todos los mensajes asociados al usuario.</li>
     *             <li>Eliminacion de la informacion personal del usuario.</li>
     *         </ul>
     *     </li>
     *     <li>Si alguna de las operaciones de eliminacion tuvo exito, actualiza las interfaces de
     *     usuario tanto del servidor como de los clientes.</li>
     *     <li>Cuenta con un sistema robusto de manejo de errores que captura, registra y propaga
     *     excepciones de manera apropiada.</li>
     * </ul>
     * @param externalClientUsernae El nombre de usuario del cliente que se desea eliminar del sistema.
     *                           Este parametro no debe ser nulo.
     *
     * @return Un {@link CompletableFuture} que se completa con:
     *         <ul>
     *             <li>{@code true} si la operacion de eliminacion fue exitosa (se eliminaron mensajes
     *             y/o informacion del usuario).</li>
     *             <li>{@code false} si la operacion fallo o se produjo alguna excepcion durante el proceso.</li>
     *         </ul>
     *
     * @throws CompletionException Si ocurre un error durante la operacion de eliminacion. Esta excepcion
     *                           puede producirse por diversos motivos:
     *                           <ul>
     *                              <li>El parametro de entrada es nulo.</li>
     *                              <li>Error de acceso o comunicacion con la base de datos.</li>
     *                              <li>Ninguna operacion de eliminacion tuvo exito.</li>
     *                              <li>Otros errores inesperados durante el proceso.</li>
     *                           </ul>
     *
     * @see CompletableFuture
     * @see CompletionException
     * </body>
     */
    public final CompletableFuture<Boolean> deleteUserFromDatabase(String externalClientUsernae)
    {
        //? 1. Este metodo se encarga de eliminar un usuario de la base de datos
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        //!2.  Verificamos edge case, valor ingresado a eliminar es nulo
                        if (externalClientUsernae == null){
                            throw new
                                CompletionException("[ServerSideComms] Error durante lectura de " +
                                                            "parametros para el metodo " +
                                                            "deleUserFromDatabase, el parametro " +
                                                            "ingresado fue nulo",
                                                    null);
                        }

                        //? 3. Intentamos eliminar el usuario de la base de datos.
                        System.out.println("[ServerSideComms] Inicializacion de proceso de " +
                                                   "eliminacion para el usuario: " +
                                                   " " + externalClientUsernae);

                        if (this._usernameToUuidMap.containsKey(externalClientUsernae)){
                            String uuid =
                                    this._usernameToUuidMap.get(externalClientUsernae);
                            this._connectedClients.get(uuid).cierreDeRecursosInesperado();
                            this._connectedClients.remove(uuid);
                            this._usernameToUuidMap.remove(externalClientUsernae);
                        }

                        boolean messageDeletionWasSuccessful =
                                _DatabaseManagementSystem.dropAllRegisteredMessagesByClient(
                                        externalClientUsernae);
                        boolean userDeletionWasSuccessful =
                                _DatabaseManagementSystem.dropAllRegisteredClientInformation(
                                        externalClientUsernae);
                        //? 4. Verificamos elminiacion
                        if (messageDeletionWasSuccessful || userDeletionWasSuccessful){
                            System.out.println("[ServerSideComms] Succeeded at deleting user : " +
                                                       externalClientUsernae);
                            dispatchServerUIUpdateMessage();
                            dispatchClientUIUpdateMessage();
                            updateServerSideClientCount();
                            return true;
                        } else{
                            throw new CompletionException(
                                    "[ServerSideComms] Error: No se pudo eliminar el usuario '"
                                            + externalClientUsernae
                                            + "'. No se realizaron cambios en la base de datos",
                                    null
                            );
                        }
                    }catch (Exception e){
                        //! 6. Manejo detallado de errores
                        System.out.println("[ServerSideComms] Error durante la eliminación del usuario: "
                                                   + externalClientUsernae);
                        System.out.println("Mensaje de error: " + e.getMessage());
                        System.out.println("Causa: " + (e.getCause() != null ? e.getCause().getMessage() : "Desconocida"));
                        e.printStackTrace();
                        throw new CompletionException("Error 0x002 - No se pudo eliminar el usuario de la base de datos: "
                                                              + e.getMessage(), e);
                    }
                }, _serviceForDatabase).exceptionally(new Function<Throwable, Boolean>() {
            @Override
            public Boolean apply(Throwable throwable) {
                //! 7. Manejo de excepciones finales
                System.out.println("[ServerSideComms] Error fatal en el proceso de eliminación:");
                System.out.println("Tipo de error: " + throwable.getClass().getSimpleName());
                System.out.println("Mensaje: " + throwable.getMessage());
                return false;
            }
        });
    }


    /**
     * <body style="color:white;">
     * Este metodo elimina todos los mensajes de un usuario especifico de la base de datos
     * sin eliminar el usuario en si. La operacion se realiza de manera asincrona para
     * mantener la respuesta del servidor.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Utiliza {@link CompletableFuture#supplyAsync(Supplier)} para ejecutar la operacion
     *     de eliminacion en un hilo separado, evitando bloquear el hilo principal del servidor.</li>
     *     <li>Verifica si el nombre de usuario proporcionado es nulo, arrojando una excepcion
     *     en caso afirmativo.</li>
     *     <li>Intenta eliminar todos los mensajes asociados al usuario especificado mediante
     *     una llamada al sistema de gestion de base de datos.</li>
     *     <li>Si la eliminacion es exitosa, notifica a las interfaces de usuario y actualiza
     *     los contadores correspondientes.</li>
     *     <li>En caso de fallo en la eliminacion de mensajes, lanza una excepcion detallada.</li>
     *     <li>Incluye un manejador de excepciones mediante {@link CompletableFuture#exceptionally(Function)}
     *     para capturar y registrar cualquier error que ocurra durante el proceso.</li>
     * </ul>
     * @param externalClient El nombre de usuario del cliente cuyos mensajes se desean eliminar.
     *                       Este parametro no debe ser nulo.
     *
     * @return Un {@link CompletableFuture} que se completa con:
     *         <ul>
     *             <li>{@code true} si la operacion de eliminacion de mensajes fue exitosa.</li>
     *             <li>{@code false} si la operacion fallo o se produjo alguna excepcion durante el proceso.</li>
     *         </ul>
     *
     * @throws CompletionException Si ocurre un error durante la operacion de eliminacion. Esta excepcion
     *                           puede producirse por diversos motivos:
     *                           <ul>
     *                              <li>El parametro de entrada es nulo.</li>
     *                              <li>Error de acceso o comunicacion con la base de datos.</li>
     *                              <li>Fallo al eliminar los mensajes del usuario.</li>
     *                           </ul>
     *
     * @see CompletableFuture
     * @see CompletionException
     * </body>
     */
    public final CompletableFuture<Boolean> deleteAllUserMessagesFromDatabase(
            String externalClient){
        //? 1. Este metodo se encarga de eliminar todos los mensajes
        // de un usuario de la base de datos
        return CompletableFuture.supplyAsync( () -> {
            //? 2. Verificamos un edge case de valor incorrecto
            if (externalClient == null){
                throw new CompletionException("[ServerSideComms] Error durante el analisis de " +
                                                      "datos del metodo " +
                                                      "deleteAlluserMessagesFromDatabase, el " +
                                                      "parametro ingresado para eliminacion es " +
                                                      "nulo.",
                                              null);
            }

            //? 3. Intentamos elminar los datos de nuevo, pero como no es eliminar el usuario
            // solo elminamos sus mensajes y despachamos una UI update para el servidor
            System.out.println("[ServerSideComms] Inicio de proceso de eliminacion de cliente " +
                                       "registrado:" +
                                       " " + externalClient);
            boolean messagesDeletionWasSuccessful =
                    _DatabaseManagementSystem.dropAllRegisteredMessagesByClient(
                            externalClient);
            if (messagesDeletionWasSuccessful){
                System.out.println("[ServerSideComms] Se paso el proceso de elmiiniacion de " +
                                           "mensjaes del usuario:" + externalClient);
                dispatchServerUIUpdateMessage();
                dispatchClientUIUpdateMessage();
                updateServerSideClientCount();
                return true;
            } else {
                throw new CompletionException(
                        "[ServerSideComms] Error: No se pudo eliminar los mensajes del usuario "
                                + externalClient
                                + ". No se realizaron cambios en la base de datos",
                        null
                );
            }
        }).exceptionally(throwable -> {
            //! 6. Manejo de excepciones finales
            System.out.println("[ServerSideComms] Error fatal en el proceso de eliminacion de " +
                                       "mensajes:");
            System.out.println("Tipo de error: " + throwable.getClass().getSimpleName());
            System.out.println("Mensaje: " + throwable.getMessage());
            return false;
        });
    }


    /**
     * <body style="color:white;">
     * Este metodo filtra y recupera mensajes de un usuario especifico de la base de datos
     * segun el tipo de filtro solicitado, y actualiza la interfaz de usuario con los resultados.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Verifica que los parametros de entrada (nombre de usuario y tipo de filtro) no sean nulos.</li>
     *     <li>Realiza una consulta asincrona a la base de datos para recuperar todos los mensajes enviados
     *     y recibidos por el usuario especificado.</li>
     *     <li>Establece un tiempo limite de 10 segundos para la operacion de consulta.</li>
     *     <li>A pesar de iniciarse de forma asincrona, el metodo se vuelve secuencial debido al uso de
     *     {@code join()}, lo que bloquea hasta que se complete la operacion.</li>
     *     <li>Una vez recuperados los mensajes, filtra el resultado basado en el tipo de filtro solicitado.</li>
     *     <li>Actualiza la propiedad observable {@code ex_FilteredMessagesProperty} en el hilo de la interfaz
     *     de usuario con los mensajes filtrados.</li>
     *     <li>Maneja errores en diferentes niveles, limpiando la lista de mensajes filtrados en caso de fallo.</li>
     * </ul>
     * <p>Notas sobre limitaciones:</p>
     * <ul>
     *     <li>Este es el unico metodo en el sistema que funciona de manera secuencial debido a requisitos
     *     de coherencia entre el servidor y los clientes.</li>
     *     <li>A pesar de iniciar la operacion de forma asincrona con {@code CompletableFuture.supplyAsync()},
     *     el uso de {@code join()} hace que el metodo espere por el resultado, convirtiendolo en una operacion
     *     bloqueante.</li>
     * </ul>
     *
     * @param externalUsername El nombre de usuario cuyos mensajes se desean filtrar.
     *                        Este parametro no debe ser nulo.
     * @param externalFilterType El tipo de filtro a aplicar sobre los mensajes.
     *                          Este parametro no debe ser nulo y debe corresponder a una
     *                          clave valida en el mapa de resultados de la consulta.
     *
     * @throws CompletionException Si ocurre un error durante la consulta a la base de datos.
     *                           Esta excepcion es capturada internamente.
     *
     * @see CompletableFuture
     * @see Platform#runLater(Runnable)
     * @see MessageDTO
     * </body>
     */
    public final void filterClientListBasedOnUsernameAndType(String externalUsername,
                                                             String externalFilterType)
    {
        //? 1. Revision base de parametros ingresados
        if (externalFilterType == null || externalUsername == null) {
            System.out.println("[ServerSideComms] Error: No se pudo filtrar la base de datos " +
                                       "- alguno de los parametros ingresados fue nulo.");
            return;
        }
        System.out.println("[ServerSideComms] El sistema ha " +
                                   "iniciado el proceso de recopilacion\n" +
                                   "de mensajes para el cliente" + externalUsername);
        try {
            //?2. Realizamos una busqueda hacia la base de datos en un proceso sequencial dado a
            // que se usa no solo en este sistema sino que en el del cliente, es el unico metodo
            // que no encontramos forma de mantener su funcionamiento interno correcto. Aunque
            // usemos un supplyAsync, al usar .join() se regresa a lineal
            Map<String, List<MessageDTO>> result = CompletableFuture.supplyAsync(() -> {
                        try {
                            System.out.println("[ServerSideComms] Poll de la Base De Datos: " +
                                                       "Buscando los mensajes enviados y " +
                                                       "recibidos por parte del usuario: " +
                                                       externalUsername);
                            return _DatabaseManagementSystem
                                    .pollAllSentAndReceivedMessagesByUsername(
                                            externalUsername);
                        } catch (Exception e) {
                            System.out.println("[ServerSideComms] Error en el Poll de la Base de " +
                                                       "Datos : " + " " + e.getMessage());
                            throw new CompletionException("[ServerSideComms] Error en la Poll de "
                                                                  + "la Base de Datos", e);
                        }
                    }, _serviceForDatabase)
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();

            //? 3. Enviamos informacion de resultados
            System.out.println("[ServerSideComms] Poll de la Base De Datos: " +
                                       "Se ha completado el proceso de poll de la base de datos");
            if (result == null || result.isEmpty()) {
                System.out.println("[ServerSideComms] Poll de la Base de Datos: no se han " +
                                           "encontrado valores para el usuario" + externalUsername);
                Platform.runLater(() -> this.ex_FilteredMessagesProperty.clear());
                return;
            }

            //? 4. Filtramos basados en el tipo de filtro ingresado desde la UI del servidor
            List<MessageDTO> filteredMessages = result.get(externalFilterType);
            if (filteredMessages == null) {
                System.out.println("[ServerSideComms] Error durante Filtrado: no se han " +
                                           "encontrado mensajes para el tipo de filtro enviado" +
                                           " " + externalFilterType);
                Platform.runLater(() -> this.ex_FilteredMessagesProperty.clear());
                return;
            }

            //? 5. Enviamos los datos a una actualizacion del observable para la UI
            Platform.runLater(() -> {
                this.ex_FilteredMessagesProperty.clear();
                this.ex_FilteredMessagesProperty.addAll(filteredMessages);
            });

        } catch (Exception e) {
            System.out.println("[ServerSideComms] Error: " + e.getMessage());
            System.out.println("e.getMessage() = " + e.getMessage());
            System.out.println("e.getCause() = " + e.getCause());
            e.printStackTrace();

            //? 5.1 Si ocurrio un error grave que nos llevo hasta aqui, entonces limipiamos los
            // mensajes filtrados en el off chance que salga un valor incorrecto (aunque nunca ha
            // pasada en pruebas)
            Platform.runLater(() -> this.ex_FilteredMessagesProperty.clear());
        }
    }



    /*! Funciones preliminares para el envio de mensajes al servidor*/

    /**
     * <body style="color:white;">
     * Intenta entregar un mensaje a otro cliente y almacenarlo en la base de datos.
     * Este metodo verifica si el receptor esta conectado, actualiza el estado del mensaje
     * y lo guarda en la base de datos.
     *
     * <p>Funcionamiento del metodo:</p>
     * <ul>
     *     <li>Verifica que el mensaje proporcionado no sea nulo.</li>
     *     <li>Comprueba si el destinatario del mensaje esta actualmente conectado.</li>
     *     <li>Crea una nueva instancia del mensaje con el estado de conexion actualizado.</li>
     *     <li>Almacena el mensaje en la base de datos.</li>
     *     <li>Si el almacenamiento es exitoso, actualiza la interfaz de usuario del servidor.</li>
     *     <li>Programa una actualizacion asincorna de la interfaz de usuario del cliente.</li>
     * </ul>
     *
     * @param externalMessageToSend El objeto MessageDTO que contiene la informacion del mensaje
     *                             a entregar. No debe ser nulo.
     *
     * @return {@code true} si el mensaje fue almacenado exitosamente en la base de datos,
     *         {@code false} si el parametro de entrada es nulo.
     *
     * @throws CompletionException Si ocurre un error durante el proceso de almacenamiento en la base de
     *                           datos o cualquier otra operacion durante la entrega del mensaje.
     */
    public boolean attemptToDeliverToOtherClientAReceivedMessage(MessageDTO externalMessageToSend) {
        //? 1. Revision base, intentamos revisar si el parametro no es nulo
        if (externalMessageToSend == null) {
            return false;
        }
        System.out.println("[ServerSideComms] Proceso de Distribucion de Mensaje: el " +
                                   "servidor ha iniciado el proceso de envio de mensaje a otro " +
                                   "cliente: " + externalMessageToSend._senderUUID());
                    try {
                        //? 2. Buscamos en el servidor si el receptor esta loggeado
                        String receiverUUID = externalMessageToSend._receiverUUID();
                        boolean isReceiverConnected =
                                this._connectedClients.containsKey(receiverUUID);
                        System.out.println("[ServerSideComms] Proceso de Distribucion de Mensaje " +
                                                   ": el cliente esta activo :"
                                                   + isReceiverConnected);

                        //? 2. Creamos un MessageDTO para la base de datos
                        MessageDTO messageDTOWithUpdatedStatus = new MessageDTO(
                                externalMessageToSend._senderUUID(),
                                externalMessageToSend._receiverUUID(),
                                externalMessageToSend._messageContent(),
                                externalMessageToSend._messageTimestamp(),
                                externalMessageToSend._senderConfirmation(),
                                isReceiverConnected
                        );

                        System.out.println("[ServerSideComms] Proceso de Distribucion de Mensaje:" +
                                                   " validamos mensaje enviado previamente: "
                                                   + externalMessageToSend);
                        System.out.println("[ServerSideComms] proceso de Distribucion de Mensaje:" +
                                                   " validamos mensaje actualizado: "
                                                   + messageDTOWithUpdatedStatus);

                        //? 3. Almacenamos en la base de datos
                        boolean databaseOperationSuccessful =
                                _DatabaseManagementSystem.insertMessageSentIntoDatabase(
                                        messageDTOWithUpdatedStatus);

                        if (databaseOperationSuccessful) {
                            System.out.println("[ServerSideComms] Succeeded at storing message into database");
                            System.out.println("[ServerSideComms] Succeeded at delivering message to receiver");


                            //? 4. Enviamos informacion a la UI
                            dispatchServerUIUpdateMessage();

                            CompletableFuture.runAsync( () -> {
                                try{
                                    Thread.sleep(500);

                                    dispatchClientUIUpdateMessage();
                                } catch (InterruptedException e){
                                    System.out.println("[ServerSideComms] Error: " + e.getMessage());
                                    System.out.println("e.getMessage() = " + e.getMessage());
                                    System.out.println("e.getCause() = " + e.getCause());
                                }
                            });

                            return true;
                        } else {
                            throw new CompletionException(
                                    "[ServerSideComms] Error: No se pudo almacenar el mensaje en la base de datos",
                                    null);

                        }
                    } catch (Exception e) {
                        //! 8. Manejo detallado de errores
                        System.out.println("[ServerSideComms] Error procesando mensaje:");
                        System.out.println("Mensaje de error: " + e.getMessage());
                        System.out.println("Causa: " + (e.getCause() != null ?
                                e.getCause().getMessage() : "Desconocida"));
                        e.printStackTrace();
                        throw new CompletionException(
                                "Error al procesar el mensaje: " + e.getMessage(),
                                e
                        );
                    }
    }

    /**
     * <body style="color:white;">
     * ClientHandler es una clase interna que gestiona la comunicación con un cliente conectado al servidor.
     *
     * <p>Esta clase implementa Runnable para ejecutar la comunicación con el cliente en un hilo separado,
     * manteniendo una conexión persistente y procesando los comandos recibidos.</p>
     *
     * <p>Principales responsabilidades:</p>
     * <ul>
     *     <li>Mantener la conexión con un cliente específico</li>
     *     <li>Procesar los comandos recibidos del cliente</li>
     *     <li>Manejar solicitudes de mensajes broadcast</li>
     *     <li>Gestionar actualizaciones de listas de mensajes y clientes</li>
     *     <li>Procesar solicitudes de desconexión</li>
     *     <li>Administrar los streams de entrada/salida para la comunicación</li>
     * </ul>
     *
     * <p>Utiliza bloqueos concurrentes (ReentrantLock) para garantizar que las operaciones críticas
     * de comunicación se realicen de manera segura y colas concurrentes para el procesamiento
     * de mensajes.</p>
     */
    private class ClientHandler implements Runnable {
        /**
         * Parametro usado para mantener activo el socket del cliente, como este se genera antes
         * de llegar a esta zona, la idea es mantener el estado guardandolo aqui, al igual que
         * los streams.
         */
        private final Socket clientSocket;
        /**
         * InputStream de tipo ObjectInputStream usado para recibir objetos y cadenas de mensajes
         * de REQUEST desde el cliente.
         */
        private ObjectInputStream inputStream;
        /**
         * OutputStream de tipo ObjectOutputStream usado para enviar objetos y cadenas de mensajes
         * de ACKNOWLEDGE, asi como objetos internos, hacia el cliente.
         */
        private ObjectOutputStream outputStream;
        /**
         * UUID del cliente, usado para identificarlo en el servidor.
         */
        private String clientUUID = null;
        /**
         * Username del cliente, usado para identificarlo en el servidor.
         */
        private String clientUsername = "Unknown";
        /**
         * Indica si el cliente esta activo o no.
         */
        private final AtomicBoolean running = new AtomicBoolean(true);
        /**
         * Lock usado para garantizar que las operaciones de envio de mensajes sean atomicas y
         * sincronizadas.
         */
        private final ReentrantLock lockForOperationResultSending = new ReentrantLock();

        /**
         * <body style="color:white;">
         * Constructor para inicializar un nuevo manejador de cliente.
         *
         * <p>Este constructor guarda la conexion inicial con un cliente, configurando
         * los canales de comunicación necesarios y registrando la informacion de identificacion
         * del cliente.</p>
         *
         * @param socket El socket de conexion establecido con el cliente
         * @param inputStream Stream de entrada para recibir datos del cliente
         * @param outputStream Stream de salida para enviar datos al cliente
         * @param clientUUID Identificador unico universal del cliente
         * @param clientUsername Nombre de usuario del cliente
         */
        public ClientHandler(Socket socket,
                             ObjectInputStream inputStream,
                             ObjectOutputStream outputStream,
                             String clientUUID,
                             String clientUsername) {
            this.clientSocket = socket;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            this.clientUUID = clientUUID;
            this.clientUsername = clientUsername;
        }

        /**
         * <body style="color:white;">
         * Implementacion del metodo run de la interfaz Runnable que ejecuta el ciclo principal de manejo
         * del cliente conectado.
         *
         * <p>Este metodo se ejecuta en un hilo independiente y realiza las siguientes operaciones:</p>
         * <ol>
         *     <li>Verifica si los streams de entrada/salida estan inicializados, caso contrario los inicializa</li>
         *     <li>Mantiene un ciclo continuo de escucha mientras el cliente este conectado y activo</li>
         *     <li>Lee comandos enviados por el cliente a traves del stream de entrada</li>
         *     <li>Procesa cada comando recibido mediante el metodo processCommand</li>
         *     <li>Maneja excepciones de entrada/salida que puedan ocurrir durante la comunicacion</li>
         * </ol>
         *
         * <p>El metodo utiliza un flag atomico (running) para controlar la ejecucion del ciclo,
         * permitiendo una terminacion segura del hilo cuando sea necesario.</p>
         *
         * @throws IOException Si ocurre un error durante la lectura de datos del cliente
         * @throws Exception Para capturar cualquier otra excepcion no prevista durante la ejecucion
         */
        @Override
        public void run() {
            try {
                if (inputStream == null || outputStream == null) {
                    initializeStreams();
                }
                System.out.println("[ClientHandler para :" + this.clientUsername  + "] " +
                                           "construido" +
                                           " e inicializado");
                while (running.get() && !clientSocket.isClosed()) {
                    try {
                        String command = inputStream.readUTF();
                        processCommand(command);
                    } catch (IOException e) {
                        if (running.get()) {
                            System.err.println("Error reading from client " + clientUsername + ": " + e.getMessage());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error in client handler for " + clientUsername + ": " + e.getMessage());
            }
        }

        /**
         * <body style="color:white;">
         * Procesa los comandos recibidos del cliente y ejecuta las acciones correspondientes.
         *
         * <p>Este metodo implementa un sistema de enrutamiento de comandos basado en un enfoque
         * similar a HTTP, donde cada comando recibido se traduce a una accion especifica en el
         * servidor. El flujo de procesamiento incluye:</p>
         *
         * <ol>
         *     <li>Identificar el tipo exacto del mensaje recibido comparandolo con los valores
         *         definidos en el enum UsefulCommunicationMessages</li>
         *     <li>Validar que el comando sea reconocido por el sistema</li>
         *     <li>Redirigir el procesamiento a handlers especializados dependiendo del tipo de comando</li>
         * </ol>
         *
         * <p>La implementacion maneja tres flujos principales de comunicacion:</p>
         * <ul>
         *     <li>Cliente hacia servidor (envio de mensajes, desconexiones)</li>
         *     <li>Cliente solicitando informacion al servidor (actualizaciones de listas)</li>
         *     <li>Servidor enviando mandatos a los clientes (actualizaciones forzadas)</li>
         * </ul>
         *
         * <p>Para los mandatos del servidor hacia el cliente, se utiliza un mecanismo de bloqueo
         * (lock) que garantiza la exclusividad durante el envio de mensajes, evitando condiciones
         * de carrera en la comunicacion.</p>
         *
         * @param commandReadFromSocket Cadena de texto que contiene el comando recibido del socket
         *
         * @throws EOFException Si se alcanza el final del stream durante la lectura/escritura
         * @throws IOException Si ocurre un error durante la lectura/escritura en los streams
         * @throws Exception Captura general para cualquier otra excepcion durante el procesamiento
         *
         * @see UsefulCommunicationMessages
         */
        private void processCommand(String commandReadFromSocket) {
            /*
             ? la idea de este metodo es tomar un comando del usuario y redireccionar la llamada
             ? hacia un funcion especifica que pueda tomar en cuenta los datos que este le envian.
             ? En este sentido pueden haber dos flujos, o casos de uso, separados, uno tiene que
             ? ver con el proceso de enviar y recibir mensajes, enviar al grupo y recibir
             ? confirmacion, o enviar confirmacion y recibir del grupo. En el segundo use case,
             ? el client solicita al usuario cambios en la UI. EN un tercer caso, que es mucho
             ?mas cercano al trabajo del servidor, el servidor indica a los usuarios que tienen
             ?que actualizar sus datos internos. Todos estos mensajes hacen uso de una serie de
             ?comandos proveninentes de los UsefulCommunicationMessages enum.
             */
            try{
                UsefulCommunicationMessages readInMessageFromUser = null;
                //? 1. Encontramos el tipo exacto del mensaje para poder manejarlo
                for(UsefulCommunicationMessages msg :UsefulCommunicationMessages.values()){
                    if (msg.get_message().equals(commandReadFromSocket)){
                        readInMessageFromUser = msg;break;
                    }
                }
                //! 1.1 Si el mensaje no fue correcto, entonces debemos de registrar la
                //! comunicacion en el sistem
                if (readInMessageFromUser == null){
                    System.out.println("Client Message: Error en el mensaje, el comando ingresado" +
                                               " + [" + commandReadFromSocket + "] es invalido.");
                    return;
                }

                //? 2. Difurcamos la llamada dependiendo del tipo de comando. Nuestra
                //? implementacion se basa en metodos parecidos a los de HTTP para manejar la
                //? conexion con el usuario
                switch(readInMessageFromUser){
                    /*! Todos estos casos son del proceso cliente -[info]-> servidor*/
                    case POST_MESSAGE_BROADCAST_REQUEST: {
                        System.out.println("[ServerSideComms] Cliente solicita enviar un mensaje");
                         handlePostMessageBroadcastRequest();
                         break;
                    }
                    case POST_CLIENT_DISCONNECTION_REQUEST:{
                        System.out.println("[ServerSideComms] Cliente solicita desconectarse");
                        handlePostClientDisconnectionRequest();
                        break;
                    }
                    /*! Todos estos casos son del proceso cliente <-[info]-> servidor, es decir,
                    /*!el cliente requiere informacion y envia peticiones para esta*/
                    case POST_MANDATORY_MESSAGE_LIST_UPDATE_REQUEST: {
                        System.out.println("[ServerSideComms] Cliente solicita actualizar " +
                                                   "lista de mensajes");
                        handleMessageListUpdateRequest();
                        break;
                    }
                    case POST_MANDATORY_CLIENT_LIST_UPDATE_REQUEST: {
                        System.out.println("[ServerSideComms] Cliente solicita actualizar " +
                                                   "lista de clientes");
                        handleClientListUpdateRequest();
                        break;
                    }
                    /*Handles para los casos de solo comunicacion*/
                    case POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE:{
                        lockForOperationResultSending.lock();
                        try{
                            //? 1. Intentamos enviar el mensaje hacia el cliente
                            outputStream.writeUTF(
                                    UsefulCommunicationMessages
                                            .POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE
                                            .get_message());
                            outputStream.flush();
                        } catch(EOFException e ){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario. Ha occurido un " +
                                                       "EOFException");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                        }catch(IOException e){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario.");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                            e.printStackTrace();
                        } catch(Exception e){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario.");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                        }finally {
                            lockForOperationResultSending.unlock();
                        }
                        break;
                    }
                    case POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE: {
                        lockForOperationResultSending.lock();
                        try{
                            //? 1. Intentamos enviar el mensaje hacia el cliente
                            outputStream.writeUTF(
                                    UsefulCommunicationMessages
                                            .POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE
                                            .get_message());
                            outputStream.flush();
                        } catch(EOFException e ){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario. Ha occurido un " +
                                                       "EOFException");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                        }catch(IOException e){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario.");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                            e.printStackTrace();
                        } catch(Exception e){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario.");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                        }finally {
                            lockForOperationResultSending.unlock();
                        }
                        break;
                    }
                    case POST_CLIENT_SHUTDOWN_MANDATE:{
                        lockForOperationResultSending.lock();
                        try{
                            //? 1. Intentamos enviar el mensaje hacia el cliente
                            outputStream.writeUTF(
                                    UsefulCommunicationMessages
                                            .POST_CLIENT_SHUTDOWN_MANDATE
                                            .get_message());
                            outputStream.flush();
                        } catch(EOFException e ){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario. Ha occurido un " +
                                                       "EOFException");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                        }catch(IOException e){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario.");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                            e.printStackTrace();
                        } catch(Exception e){
                            System.out.println("Error 0x001 - [Raised] No se pudo procesar el mensaje ingresado " +
                                                       "por el usuario.");
                            System.out.println("e.getMessage() = " + e.getMessage());
                            System.out.println("e.getCause() = " + e.getCause());
                        }finally {
                            lockForOperationResultSending.unlock();
                        }
                        break;
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
                System.out.println("Error 0x002 - [Raised] No se pudo procesar el mensaje ingresado " +
                                       "por el usuario.");
            }
        }


        /**
         * <body style="color:white;">
         * Maneja las solicitudes de actualizacion de la lista de clientes conectados.
         *
         * <p>Este metodo gestiona el proceso completo de respuesta a una solicitud de actualizacion
         * de la lista de clientes. Implementa un protocolo de comunicacion que sigue estos pasos:</p>
         *
         * <ol>
         *     <li>Adquiere un bloqueo para garantizar la exclusividad durante la respuesta</li>
         *     <li>Verifica que el cliente este correctamente inicializado (nombre de usuario y canal de salida)</li>
         *     <li>Envia un mensaje de confirmacion al cliente usando el protocolo definido</li>
         *     <li>Consulta la base de datos para obtener la lista actualizada de todos los usuarios registrados</li>
         *     <li>Serializa y envia la lista de objetos ClientDTO al cliente solicitante</li>
         *     <li>Libera el bloqueo al finalizar (exito o error)</li>
         * </ol>
         *
         * <p>El metodo utiliza un sistema de bloqueo (lock) para evitar condiciones de carrera
         * durante la comunicacion con el cliente. Ademas, implementa un patron de manejo de errores
         * en capas para distinguir entre errores de comunicacion y errores de acceso a datos.</p>
         *
         * @throws IOException Si ocurre un error en la comunicacion con el cliente durante el envio
         *                     de la confirmacion o la lista de clientes
         * @throws CompletionException Si falla la consulta a la base de datos, encapsulando la excepcion original
         * @throws Exception Para capturar cualquier otro error no previsto durante la ejecucion
         *
         * @see UsefulCommunicationMessages
         * @see ClientDTO
         */
        public void handleClientListUpdateRequest() {
           lockForOperationResultSending.lock();
            //? 1. Revisa los parametros internos del ClientHandler, esto por si acaso se cole un
            // clientHandler que no esta inicicializado correctamente.
            if (clientUsername == null || outputStream == null) {
                System.out.println("[ServerSideComms] POST REQUEST RECEIVED: El cliente quiso " +
                                           "acutalizarla lista de mensajes, cliente:"
                                           + (clientUsername == null) + ", pero el sistema " +
                                           "detecto un OutputStream nulo: " +
                                           (outputStream == null));
                return;
            }

            //? 2. Inicializamos el proceso interno, el primer paso es enviar un ACKNOWLEDGE
            // hacia el cliente.
            List<ClientDTO> operationFuture = null;
            try {
                outputStream.writeUTF(UsefulCommunicationMessages
                                      .POST_MANDATORY_CLIENT_LIST_UPDATE_ACKNOWLEDGMENT
                                      .get_message());
                outputStream.flush();
                System.out.println("[ServerSideComms] POST REQUEST RECEIVED: EL servidor ha " +
                                           "enviado un mensaje de acknowledge para actualizar la " +
                                           "lista de mensajes al cliente : "  +
                                           " " + clientUsername);
                try {
                    System.out.println("[ServerSideComms] POST REQUEST RECEIVED: " +
                                               "El servidor ha iniciado una peticion para obtener" +
                                               " todos los clientes registrados");
                    operationFuture = _DatabaseManagementSystem.pollAllRegisteredUsersInDatabase();
                } catch (Exception e) {
                    System.out.println("[ServerSideComms] POST REQUEST RECEIVED: error en la base" +
                                               " de datos" +
                                               " " + e.getMessage());
                    throw new CompletionException("[ServerSideComms] Error en la base de datos", e);
                }
                    if (operationFuture == null || operationFuture.isEmpty()) {
                        System.out.println("[ServerSideComms] POST REQUEST RECEIVED: error en la " +
                                                   "base de datos, no se pudo obtener la lista de " +
                                                   "clientes");
                    }
                    System.out.println("[ServerSideComms] POST REQUEST RECEIVED: El servidor " +
                                               "ha obtenido la lista de clientes registrados, " +
                                               "enviando al cliente" + clientUsername);
                    outputStream.writeObject(operationFuture);
                    outputStream.flush();
                    System.out.println("[ServerSideComms] Successfully sent client list to: " + clientUsername);

            } catch (IOException e) {
                System.err.println("[ServerSideComms] Communication error: " + e.getMessage());
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                e.printStackTrace();
            } catch (Exception e) {
                System.err.println("[ServerSideComms] Unexpected error: " + e.getMessage());
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                e.printStackTrace();
            }
            finally {
                lockForOperationResultSending.unlock();
            }
        }

        public void handlePostMessageBroadcastRequest() {
            /*
             ? La idea de este metodo es receptar un mensaje del usuario directamente en el
             ? servidor mediante sus canales de comunicacion, para esto enviamos inforamcion como
             ? senales informatias de haber recibido el mensaje, etc. La implementacion de este
             ? metodo sigue la idea basica de una HTTP API con un handler para un post
             */
            lockForOperationResultSending.lock();
            try {
                System.out.println("[ServerSideComms] POST REQUEST: Handling message broadcast " +
                                           "request for " +
                                           "client: " + clientUsername);
                //? 2. Luego de recibir el POST REQUEST, enviamos un ACKNOWLEDGE al cliente.
                outputStream.writeUTF(
                        UsefulCommunicationMessages
                                .POST_MESSAGE_BROADCAST_ACKNOWLEDGEMENT
                                .get_message());
                outputStream.flush();

                //? 3. Intentamos leer la informacion del cliente
                MessageDTO messageDTO = (MessageDTO) inputStream.readObject();

                if (messageDTO == null){
                    System.out.println("[ServerSideComms] POST REQUEST: Error reading message from " +
                                               "client: " + clientUsername);
                    return;
                }

                System.out.println("[ServerSideComms] POST REQUEST: Message received from " +
                                           "client: " + clientUsername);
                System.out.println("[ServerSideComms] Validation, received DTO: ");
                System.out.println(messageDTO);

                //? 4. Realizamos la logica interna del metodo
                boolean result =
                        MessageServer.this
                                .attemptToDeliverToOtherClientAReceivedMessage(
                                        messageDTO);
                if (result){
                    System.out.println("[ServerSideComms] POST REQUEST: Message delivered to " +
                                               "client: " + clientUsername);
                } else {
                    System.out.println("[ServerSideComms] POST REQUEST: Message not delivered to " +
                                               "client: " + clientUsername);
                }
            } catch (IOException e){
                System.out.println("[ServerSideComms] POST REQUEST: Error sending acknowledgement " +
                                           "for client: " + clientUsername);
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                e.printStackTrace();
            } catch(ClassCastException | ClassNotFoundException e){
                System.out.println("[ServerSideComms] POST REQUEST: Error reading message from " +
                                           "client: " + clientUsername);
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                e.printStackTrace();
            } catch (Exception e){
                System.out.println("[ServerSideComms] POST REQUEST: Unexpected error handling " +
                                           "message " +
                                           "broadcast request for client: " + clientUsername);
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getClass() = " + e.getClass());
                e.printStackTrace();
            }
            finally {
                lockForOperationResultSending.unlock();
            }
        }
        public void handleMessageListUpdateRequest() {

            // Add a status flag to track operation completion
            AtomicBoolean operationComplete = new AtomicBoolean(false);
            // 1. Enhanced parameter validation with detailed logging
            if (clientUsername == null || outputStream == null) {
                System.out.println("[ServerSideComms] Error: Invalid parameters" +
                                           "\nUsername null: " + (clientUsername == null) +
                                           "\nOutputStream null: " + (outputStream == null));
                return;
            }

            lockForOperationResultSending.lock();
            Map<String, List<MessageDTO>> operationFuture = null;


            try {
                    outputStream.writeUTF(UsefulCommunicationMessages
                                                  .POST_MANDATORY_MESSAGE_LIST_UPDATE_ACKNOWLEDGMENT
                                                  .get_message());
                    outputStream.flush();


                System.out.println("[ServerSideComms] Sent acknowledgement to client: " + clientUsername);

                System.out.println("[ServerSideComms] Database Querying: Retrieving messages for client: "
                                           + clientUsername);
                operationFuture = _DatabaseManagementSystem
                        .pollAllSentAndReceivedMessagesByUsername(clientUsername);

                try {
                    if (operationFuture.isEmpty()) {
                        System.out.println("[ServerSideComms] Info: Empty message map for client: "
                                                   + clientUsername);
                    }

                    if (!operationFuture.isEmpty()) {
                        System.out.println("[ServerSideComms] Message map contents for " + clientUsername + ":");
                        operationFuture.forEach((recipient, messages) -> {
                            System.out.println("Recipient: " + recipient + ", Message count: "
                                                       + messages.size());
                        });
                        System.out.println("[ServerSideComms] Sending message map to client: "
                                                   + clientUsername);
                        outputStream.writeObject(operationFuture);
                        outputStream.flush();
                        System.out.println("[ServerSideComms] Successfully sent message map to: "
                                                   + clientUsername);
                    }
                } finally {
                    lockForOperationResultSending.unlock();
                }


            } catch (IOException e) {
                System.err.println("[ServerSideComms] Communication error during message list update: "
                                           + e.getMessage());
            } catch (Exception e) {
                System.err.println("[ServerSideComms] Unexpected error in message list update: " + e.getMessage());
            }
        }
        private void handlePostClientDisconnectionRequest() {
            try {
                lockForOperationResultSending.lock();
                //! Validacion de Datos
                if (clientUsername == null || clientUUID == null){
                    System.out.println("[ServerSideComms] Error: Client username or UUID is null");
                }
                //? 1. Registro para el servidor
                System.out.println("[ServerSideComms] Disconnection process initialized for user " +
                                           ": " + clientUsername + "with UUID: " + clientUUID);
                //? 2. Enviamos mensaje de confirmacion
                try {
                    outputStream.writeUTF(UsefulCommunicationMessages.
                                                  POST_CLIENT_DISCONNECTION_REQUEST_ACKNOWLEDGEMENT.get_message());
                    outputStream.flush();
                } catch (IOException e) {
                    System.out.println("Error sending disconnection acknowledgement to client " +
                                               clientUsername + ": " + e.getMessage());
                    System.out.println("e.getMessage() = " + e.getMessage());
                    System.out.println("e.getCause() = " + e.getCause());
                    e.printStackTrace();
                }

                //? 3. Limpiamos recursos del sistema
                CompletableFuture.runAsync(() -> {
                    try {
                        //! 3. Limpiamos Recursos!
                        //! 3.1. Removes al cliente del listaod de clientes conectados
                        if (clientUUID != null) {
                            MessageServer.this._connectedClients.remove(clientUUID);
                        }
                        //! 3.2. Removes al usuario del mapeo de UUID a Usernames
                        if (clientUsername != null) {
                            MessageServer.this._usernameToUuidMap.remove(clientUsername);
                        }
                        //! 3.3. Actualizamos la UI del servidor
                        MessageServer.this.updateServerSideClientCount();
                        MessageServer.this.dispatchServerUIUpdateMessage();
                        MessageServer.this.dispatchClientUIUpdateMessage();

                        //! 3.4 Cerramos resto de recursos internos
                        this.running.set(false);
                        closeResources();
                        System.out.println("Client " + clientUsername + " disconnected successfully");
                    }catch (Exception e){
                        System.out.println("[ServerSideComms] Unexpected error cleaning up " +
                                                   "resources for client " + clientUsername +
                                                   ": " + e.getMessage());
                        System.out.println("e.getMessage() = " + e.getMessage());
                        System.out.println("e.getCause() = " + e.getCause());
                        e.printStackTrace();
                    }
                }).exceptionally(new Function<Throwable, Void>() {
                    @Override
                    public Void apply(Throwable throwable) {
                        System.out.println("[ServerSideComms] Unexpected error cleaning up " +
                                                   "resources for client " + clientUsername +
                                                   ": " + throwable.getMessage());
                        System.out.println("throwable = " + throwable.getMessage());
                        System.out.println("throwable.getCause() = " + throwable.getCause());
                        return null;
                    }
                });
                running.set(false);
                closeResources();

            } catch (Exception e) {
                System.err.println("Error al manejar desconexion del usuario: " + clientUsername +
                                           ": " + e.getMessage());
                e.printStackTrace();
            } finally {
                lockForOperationResultSending.unlock();
            }
        }
        private void closeResources() {
            try {
                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                }

                if (outputStream != null) {
                    outputStream.close();
                    outputStream = null;
                }

                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error Al Cerrar El Servidor:  " + clientUsername + ": " + e.getMessage());
            }
        }


        /*! Helper methods para la inicializacion y manejo de un ClientHandler*/
        private void initializeStreams() throws IOException {
            this.outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
            this.outputStream.flush();
            this.inputStream = new ObjectInputStream(clientSocket.getInputStream());
        }

        public final void cierreDeRecursosInesperado(){
            System.out.println("[ServerSideComms] Se ha detectado que un cliente eliminado estaba" +
                                       " conectado al servicio, se procede a eliminar sus " +
                                       "recursos");
            try {
                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                }

                if (outputStream != null) {
                    outputStream.close();
                    outputStream = null;
                }

                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Error Al Cerrar El Servidor:  " + clientUsername + ": " + e.getMessage());
            }
        }
    }



    public final void shutdownTheServer(){
            System.out.println("[ServerShutdown] Initiating server shutdown sequence...");

            //? SI todavia hay clientes tenemos que enviar el commando de shutdown
            if (!this._connectedClients.isEmpty()){
                System.out.println("[ServerShutdown] Sending shutdown command to clients...");
                for (Map.Entry<String, ClientHandler> entry : _connectedClients.entrySet()) {
                    ClientHandler handler = entry.getValue();
                    try {
                        handler.processCommand(
                                UsefulCommunicationMessages.
                                        POST_CLIENT_SHUTDOWN_MANDATE.get_message());
                    } catch (Exception e) {
                        System.err.println("[ServerShutdown] Error sending shutdown command to client " + entry.getKey() + ": " + e.getMessage());
                        System.out.println("e.getMessage() = " + e.getMessage());
                        System.out.println("e.getCause() = " + e.getCause());
                    }
                }
            }
            //? Apagamos la base de datos ...
            System.out.println("[ServerShutdown] Attempting to shutdown database service...");
            try {
                _serviceForDatabase.shutdown();
                if (!_serviceForDatabase.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.out.println("[ServerShutdown] Warning: Database service did not terminate normally");
                    List<Runnable> pendingTasks = _serviceForDatabase.shutdownNow();
                    System.out.println("[ServerShutdown] Forcing shutdown. Pending tasks: " + pendingTasks.size());
                    pendingTasks.forEach(task -> System.out.println("[ServerShutdown] Unfinished task: " + task));
                } else {
                    System.out.println("[ServerShutdown] Database service shutdown successfully");
                }
            } catch (InterruptedException e) {
                System.err.println("[ServerShutdown] Database service shutdown interrupted: " + e.getMessage());
                _serviceForDatabase.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 2. Shutdown client handlers and client service
            System.out.println("[ServerShutdown] Attempting to shutdown client connections...");
            try {
                // First notify all clients and close their connections
                for (Map.Entry<String, ClientHandler> entry : _connectedClients.entrySet()) {
                    ClientHandler handler = entry.getValue();
                    try {
                        handler.processCommand(
                                UsefulCommunicationMessages.
                                        POST_CLIENT_DISCONNECTION_REQUEST.get_message());
                    } catch (Exception e) {
                        System.err.println("[ServerShutdown] Error disconnecting client " + entry.getKey() + ": " + e.getMessage());
                        System.out.println("e.getMessage() = " + e.getMessage());
                        System.out.println("e.getCause() = " + e.getCause());
                    }
                }

                // Clear the client holders
                _connectedClients.clear();
                _usernameToUuidMap.clear();

                // Shutdown the client service executor
                _serviceForClients.shutdown();
                if (!_serviceForClients.awaitTermination(30, TimeUnit.SECONDS)) {
                    System.out.println("[ServerShutdown] Warning: Client service did not terminate normally");
                    List<Runnable> pendingTasks = _serviceForClients.shutdownNow();
                    System.out.println("[ServerShutdown] Forcing shutdown. Pending client tasks: " + pendingTasks.size());
                } else {
                    System.out.println("[ServerShutdown] Client service shutdown successfully");
                }
            } catch (InterruptedException e) {
                System.err.println("[ServerShutdown] Client service shutdown interrupted: " + e.getMessage());
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                _serviceForClients.shutdownNow();
                Thread.currentThread().interrupt();
            }

            // 3. Finally shutdown database connection
            System.out.println("[ServerShutdown] Closing database connection...");
            try {
                _DatabaseManagementSystem.shutDownDatabaseConnection();
            } catch (Exception e) {
                System.err.println("[ServerShutdown] Error during database connection shutdown: " + e.getMessage());
                System.out.println("e.getMessage() = " + e.getMessage());
                System.out.println("e.getCause() = " + e.getCause());
                e.printStackTrace();
            }

            System.out.println("[ServerShutdown] Server shutdown completed");
            _ServerIsRunning.set(false);
        }






    }



