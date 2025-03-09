package com.evolvlabs.multiuserchatgui.ServerSideBackend;

import com.evolvlabs.multiuserchatgui.ClientSideBackend.ClientPOJO;
import com.evolvlabs.multiuserchatgui.ClientSideBackend.MessagePOJO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.ClientDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO;
import com.evolvlabs.multiuserchatgui.ServerSideBackend.PersistencyEngine.DatabaseConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-2025
 * @description: El presente archivo contiene varios de los metodos de alto nivel de
 * administracion para el manejo de sentencias SQL y los resultados de las mismas. Esta API de
 * alto nivel permite al sistema elegir de diversas queries prestablecidas internamente para que
 * el manejo de SQL se centre en la transformacion de los datos, y no en la recuperacion y manejo
 * de sentencias. En este sentido, se proveen metodos internos con statements
 * predeterminados para el manejo de las operaciones de la base de datos. Es decir, el sistema no
 * permite la ejecucion de statements definidos por el cliente, ni el administrador del servidor,
 * sino que bloquea la base de datos a un cierto tipo de operaciones seguras.
 */
public final class DatabaseManagementSystem {

    /*! Parametros internos*/
    private DatabaseConnection databaseConnection;
    private final ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = readWriteLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = readWriteLock.writeLock();

    /**
     * Constructor vacio ya que solo se encarga de inicializar los datos de la conexion a la base
     * de datos, internamente.
     */
    public DatabaseManagementSystem(){
        try {
            this.databaseConnection = new DatabaseConnection();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    private <T> T executeReadOperation(DatabaseOperation<T> operation) {
        readLock.lock();
        try {
            T result = operation.execute();
            return result;
        } catch (Exception e) {
            System.err.println("Database read error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database read operation failed", e);
        } finally {
            readLock.unlock();
        }
    }

    private <T> T executeWriteOperation(DatabaseOperation<T> operation) {
        writeLock.lock();
        try {
            // Don't get the connection here - let the operation get it when needed
            T result = operation.execute();
            return result;
        } catch (Exception e) {
            System.err.println("Database write error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database write operation failed", e);
        } finally {
            writeLock.unlock();
        }
    }

    @FunctionalInterface
    private interface DatabaseOperation<T> {
        T execute() throws Exception;
    }
    /*! DQL Statements: Operaciones que solo pueden recoger datos de la base de datos para el
      ! sistema */
    /*
     ? La primera operacion que el backend tiene que realizar es la carga de contactos, algo que
     ? se realiza a traves de una lista con los nombres de los usuarios que se han registrado en
     ? la aplicacion. Para hacer esto usamos un metodo que englobe este proceso
    */

    /**
     * <body style="color: white">
     * Este metodo recupera una lista con los nombres de usuario de todos los clientes registrados
     * en la base de datos, ordenados alfabeticamente.
     *
     * <p>Utiliza una consulta SQL seleccionando exclusivamente la columna de nombres de usuario
     * desde una tabla llamada {@code CLIENTSTABLE}. La consulta se ejecuta utilizando
     * {@link DatabaseConnection}, y el resultado se procesa iterando en el {@link ResultSet}
     * ofrecido por la API JDBC.</p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     *     <li>Se define una sentencia SQL que recupera y ordena alfabeticamente la columna
     *         {@code CLIENT_USERNAME} de la tabla {@code CLIENTSTABLE}.</li>
     *     <li>La consulta SQL se ejecuta mediante un bloque {@code try-with-resources} que asegura
     *         el cierre adecuado de los recursos implicados.</li>
     *     <li>El resultado es procesado para recolectar los nombres de usuario, ignorando valores
     *         nulos.</li>
     *     <li>Se devuelve una lista con los nombres de usuario obtenidos.</li>
     * </ol>
     *
     * <h2>Errores y Excepciones</h2>
     * <ul>
     *     <li>Error SQL: Puede producirse si hay un problema con la operacion o integridad de las
     *         consultas hacia la base de datos.</li>
     *     <li>Excepcion {@code SQLException}: Si ocurre algun error en las operaciones sobre el
     *         {@link ResultSet}.</li>
     * </ul>
     *
     * @return Lista de cadenas que contiene los nombres de usuario de todos los clientes
     * registrados en la base de datos. Si no hay clientes registrados, se devolvera una lista
     * vacia.
     */
    public final List<String> pollAllClientUsernamesInDatabase() {
        return executeReadOperation(() -> {
            List<String> results = new ArrayList<>();
            //? 1. Escribimos la sentencia SQL
            String queryAllUsers =
                    """
                            SELECT CLIENT_USERNAME FROM CLIENTSTABLE
                            ORDER BY CLIENT_USERNAME
                            """;
            //? 2. Ejecutamos la setencia dentro de la base de datos abstraida en DatabaseConnection
            try (DatabaseConnection.QueryResult queryResult =
                         this.databaseConnection.executeQuery(queryAllUsers)) {
                while (queryResult.get_resultSet().next()) {
                    String username = queryResult.get_resultSet().getString("CLIENT_USERNAME");
                    if (username != null) {
                        results.add(username);
                    }
                }
            } catch (SQLException exception) {
                System.err.println("Fatal Error 0x001 - [Raised] [SQL-" + exception.getErrorCode() +
                                           "] error in pollAllClientusernamesInDatabase. Caused by [" + exception.getMessage() + "]");
            }

            //? 3. Retornamos resultados
            return results;
        });
    }

    /*
     ? Otra operacion del lado del usuario que puede ser requerida es el retornar en listas
     ? separadas los mensajes en los que el usuario sea el sender o receiver. Podemos en general
     ? utilizar un objeto
     */

    /**
     * <body style="color: white">
     Este metodo permite extraer todos los mensajes enviados y recibidos por un usuario
     * especifico, diferenciandolos en listas separadas de mensajes. El resultado se organiza usando
     * un mapa con dos claves principales: "sentMessages" y "receivedMessages". Cada clave contiene
     * una lista de objetos MessagePOJO con los datos de los mensajes.
     *
     * <p>
     * El metodo utiliza dos sentencias SQL separadas: una para recuperar mensajes donde el usuario
     * es el remitente, y otra para mensajes donde el usuario es el receptor. Los datos se consultan
     * usando {@link DatabaseConnection}, y los resultados se extraen por medio de iteraciones sobre
     * el ResultSet.
     * </p>
     *
     * <h2>Funcionalidad Interna</h2>
     * <ol>
     *     <li>Se inicializan estructuras de datos requeridas, como {@code LinkedHashMap} y {@code List<MessagePOJO>}.</li>
     *     <li>Se define la consulta SQL para identificar mensajes donde el usuario es remitente.</li>
     *     <li>Se ejecuta dicha consulta y se procesan los resultados dentro de un bloque try-with-resources.</li>
     *     <li>Se define la segunda consulta SQL para los mensajes donde el usuario es receptor.</li>
     *     <li>Se ejecuta la segunda consulta y se procesan de igual manera los resultados, almacenandolos en la estructura resultante.</li>
     * </ol>
     *
     * <h2>Errores Posibles</h2>
     * <ul>
     *     <li>Error de SQL: Puede ocurrir si hay problemas en la operacion o integridad de la base de datos.</li>
     *     <li>Datos Inconsistentes: Si algun campo esencial, como los nombres de usuarios, no existe en la consulta.</li>
     * </ul>
     *
     * @param exUserName Nombre de usuario especifico para el cual se desean recuperar los
     *                   mensajes.
     * @return Mapa con dos claves ("sentMessages" y "receivedMessages"), donde cada valor es una
     * lista de mensajes clasificados por el tipo de emision (enviados o recibidos).
     * @throws RuntimeException si ocurre algun error durante las operaciones SQL que impida la
     *                          correcta continuacion del metodo.     
     * </body>
     */
    public final Map<String, List<MessageDTO>> pollAllSentAndReceivedMessagesByUsername(String exUserName) {
        return executeReadOperation( () -> {//? Paso Base: Definimos estructuras requeridas
            HashMap<String, List<MessageDTO>> results = new HashMap<>();
            //? 1. Definimos la sentencias a ejecutar
            String pollUsernameAsSender =
                    """
                            SELECT
                                s.CLIENT_USERNAME as SENDER_NAME,
                                r.CLIENT_USERNAME as RECEIVER_NAME,
                                m.MESSAGE_CONTENT,
                                m.MESSAGE_TIMESTAMP,
                                m.SENDER_CONFIRMATION,
                                m.RECEIVER_CONFIRMATION
                            FROM MESSAGESTABLE m
                                     INNER JOIN CLIENTSTABLE s ON m.SENDER_UUID = s.CLIENT_UUID
                                     INNER JOIN CLIENTSTABLE r ON m.RECEIVER_UUID = r.CLIENT_UUID  
                            WHERE s.CLIENT_USERNAME = ?    
                            ORDER BY m.MESSAGE_TIMESTAMP
                            """;
            String pollUsernameAsReceiver =
                    """
                            SELECT
                                r.CLIENT_USERNAME as SENDER_NAME,
                                s.CLIENT_USERNAME as RECEIVER_NAME,
                                m.MESSAGE_CONTENT,
                                m.MESSAGE_TIMESTAMP,
                                m.SENDER_CONFIRMATION,
                                m.RECEIVER_CONFIRMATION
                            FROM MESSAGESTABLE m
                                     INNER JOIN CLIENTSTABLE r ON m.SENDER_UUID = r.CLIENT_UUID
                                     INNER JOIN CLIENTSTABLE s ON m.RECEIVER_UUID = s.CLIENT_UUID
                            WHERE s.CLIENT_USERNAME = ?
                            ORDER BY MESSAGE_TIMESTAMP
                            """;

            //? 2. Ejecutamos cada sentencia en secuencial
            try {
                try (DatabaseConnection.QueryResult sentResult = databaseConnection.executeQuery(pollUsernameAsSender,
                                                                                                 exUserName)) {
                    ResultSet rs = sentResult.get_resultSet();
                    List<MessageDTO> sentMessages = new ArrayList<>();
                    while (rs.next()) {
                        sentMessages.add(new MessageDTO(
                                rs.getString("SENDER_NAME"),
                                rs.getString("RECEIVER_NAME"),
                                rs.getString("MESSAGE_CONTENT"),
                                rs.getTimestamp("MESSAGE_TIMESTAMP"),
                                rs.getBoolean("SENDER_CONFIRMATION"),
                                rs.getBoolean("RECEIVER_CONFIRMATION")
                        ));
                    }
                    results.put("sentMessages", sentMessages);
                }
                try (DatabaseConnection.QueryResult receivedResult = databaseConnection.executeQuery(pollUsernameAsReceiver, exUserName)) {
                    ResultSet rs = receivedResult.get_resultSet();
                    List<MessageDTO> receivedMessages = new ArrayList<>();
                    while (rs.next()) {
                        receivedMessages.add(new MessageDTO(
                                rs.getString("SENDER_NAME"),
                                rs.getString("RECEIVER_NAME"),
                                rs.getString("MESSAGE_CONTENT"),
                                rs.getTimestamp("MESSAGE_TIMESTAMP"),
                                rs.getBoolean("SENDER_CONFIRMATION"),
                                rs.getBoolean("RECEIVER_CONFIRMATION")
                        ));
                    }
                    results.put("receivedMessages", receivedMessages);
                }

            } catch (SQLException e) {
                e.printStackTrace();
                throw new RuntimeException("Error al obtener los mensajes del usuario " + exUserName +
                                                   ": " + e.getMessage(), e);
            }
            return results;
        });
    }


    /*
     ? Otra operacion del lado del servidor es el llamar a todos los usuarios para listarlos con
     ? su contrasenas. En el caso de estos objetos, como en la base de datos se guardan con un
     ? UUID, el UUID debe ser registrado asi como el password cuyo hash se registra.
     */

    /**
     * <body style="color: white;">
     * Este metodo extrae una lista de objetos {@link ClientDTO} que representan a todos los
     * usuarios registrados en la base de datos.
     *
     * <p>
     * El metodo implementa una consulta SQL que selecciona los identificadores unicos, nombres de
     * usuario, hashes de contrasena y salts correspondientes desde una tabla llamada
     * {@code CLIENTSTABLE}. Utiliza un bloque {@code try-with-resources} para ejecutar una
     * instancia de {@link DatabaseConnection.QueryResult}, lo que garantiza el manejo seguro y
     * eficiente de los recursos como el {@link ResultSet}.
     * </p>
     *
     * <h2>Proceso de Funcionamiento</h2>
     * <ol>
     *     <li>Se inicializa una lista vacia de {@link ClientDTO} para almacenar los resultados.</li>
     *     <li>Se declara una consulta SQL que recupera las columnas esenciales de la tabla {@code CLIENTSTABLE}.</li>
     *     <li>Se ejecuta la consulta usando {@link DatabaseConnection} y se procesan los resultados dentro de un
     *         bucle para crear los objetos {@link ClientDTO} apropiadamente.</li>
     *     <li>Finalmente, la lista de {@link ClientDTO} generada es retornada.</li>
     * </ol>
     *
     * <h2>Errores y Excepciones</h2>
     * <ul>
     *     <li>{@link SQLException}: Si ocurre algun error durante la ejecucion de la consulta SQL o el procesado
     *         de los resultados.</li>
     *     <li>{@link RuntimeException}: Si algo impide la ejecucion correcta de este metodo, encapsulando la excepcion original.</li>
     * </ul>
     *
     * @return Una lista de objetos {@link ClientDTO}, representando a todos los clientes
     * almacenados en la base de datos. Si no existen clientes registrados, retorna una lista
     * vacia.
     * @throws RuntimeException Si ocurre un error en el mecanismo subyacente de acceso a la base de
     *                          datos.
     *                          </body>
     */
    public final List<ClientDTO> pollAllRegisteredUsersInDatabase() {
        return executeReadOperation( () ->{//? Definimos estructuras de retorno
            List<ClientDTO> results = new ArrayList<>();

            //? 1. Definimos la cadena de Query
            String pollRegisteredUsers =
                    """
                            SELECT
                                CLIENT_UUID,
                                CLIENT_USERNAME,
                                CLIENT_PASSWORDHASH,
                                CLIENT_PASSWORDSALTING
                            FROM CLIENTSTABLE
                            """;

            //? 2. Ejecutamos la Sentencia sincronicamente
            try (DatabaseConnection.QueryResult resultQuery =
                         this.databaseConnection.executeQuery(pollRegisteredUsers)) {
                while (resultQuery.get_resultSet().next()) {
                    results.add(new ClientDTO(
                            resultQuery.get_resultSet().getString("CLIENT_UUID"),
                            resultQuery.get_resultSet().getString("CLIENT_USERNAME"),
                            resultQuery.get_resultSet().getString("CLIENT_PASSWORDHASH"),
                            resultQuery.get_resultSet().getString("CLIENT_PASSWORDSALTING")
                    ));
                }
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
                throw new RuntimeException("Error al pedir todos los clientes a la base de datos" +
                                                   ": " + sqlException.getMessage(), sqlException);
            }

            //? 3. Retornamos la informacion pertinente
            return results;
        });
    }


    /**
     * <body style="color: white;">
     * Este metodo recupera la informacion asociada a un usuario especifico desde la base de datos,
     * utilizando el nombre de usuario como criterio de busqueda. La informacion es encapsulada en
     * un objeto {@link ClientDTO}.
     *
     * <p>
     * Implementa una declaracion SQL para extraer las columnas relevantes de la tabla
     * {@code CLIENTSTABLE}, y utiliza un bloque {@code try-with-resources} para ejecutar la
     * consulta, asegurando el correcto manejo de recursos como el {@link ResultSet}.
     * </p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     *     <li>Se inicializa una variable {@code ClientDTO} para almacenar los resultados (si existen).</li>
     *     <li>Se prepara una sentencia SQL que filtra por el nombre de usuario dado como parametro.</li>
     *     <li>Se ejecuta la consulta utilizando el metodo {@code executeQuery} de {@link DatabaseConnection}.</li>
     *     <li>Se valida si la consulta retorna un resultado. Si es asi, se rellena un objeto {@link ClientDTO};
     *         si no, se retorna un {@code Optional.empty()}.</li>
     * </ol>
     *
     * <h2>Errores y Excepciones</h2>
     * <ul>
     *     <li>{@link SQLException}: Si ocurre un error durante la ejecucion de la consulta SQL o el procesamiento
     *         del resultado del {@link ResultSet}.
     *     </li>
     *     <li>{@link RuntimeException}: Si ocurre algun problema critico, encapsulando la excepcion original.</li>
     * </ul>
     *
     * @param exUserUsername Nombre de usuario del cliente que se desea buscar. No puede ser
     *                       {@code null}.
     * @return Un {@code Optional<ClientDTO>} que contiene la informacion del cliente si este existe
     * en la base de datos. Retorna {@code Optional.empty()} si no se encontraron coincidencias.
     * @throws RuntimeException Si ocurre un error en el mecanismo subyacente de acceso a la base de
     *                          datos o en el query.
     *                          </body>
     */
    public final Optional<ClientDTO> pollAllRegisteredInformationPerUsernameInDatabase(String exUserUsername) {
        return executeReadOperation( () -> { //? Definimos estructuras de retorno
            ClientDTO result = null;

            //? 1. Preparamos la sentencia SQL
            String pollRegisteredUsernameInformation =
                    """
                            SELECT
                                CLIENT_UUID,
                                CLIENT_USERNAME,
                                CLIENT_PASSWORDHASH,
                                CLIENT_PASSWORDSALTING
                            FROM CLIENTSTABLE
                            WHERE CLIENT_USERNAME = ?
                            """;

            //? 2. Ejecutamos la sentencia SQL
            try (DatabaseConnection.QueryResult results = this
                    .databaseConnection
                    .executeQuery(pollRegisteredUsernameInformation, exUserUsername)) {
                ResultSet rs = results.get_resultSet();
                if (rs.next()) {
                    result = new ClientDTO(
                            rs.getString("CLIENT_UUID"),
                            rs.getString("CLIENT_USERNAME"),
                            rs.getString("CLIENT_PASSWORDHASH"),
                            rs.getString("CLIENT_PASSWORDSALTING")
                    );
                } else {
                    return Optional.empty();
                }
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
                throw new RuntimeException("Error al tomar datos para el DTO de: " + exUserUsername +
                                                   ": " + sqlException.getMessage(), sqlException);
            }
            return Optional.of(result);
        });
    }

    /**
     * <body style="color: white;">
     * Este metodo recupera todos los mensajes registrados en la base de datos, incluyendo los datos
     * de emisor, receptor, contenido, timestamp y confirmaciones de envio y recepcion.
     *
     * <p>Lo realiza utilizando una declaracion SQL predefinida con un modelo sincrono para extraer
     * datos desde una combinacion de las tablas {@code MESSAGESTABLE} y {@code CLIENTSTABLE}.
     * Emplea un bloque try-with-resources para garantizar el correcto cierre de recursos como el
     * {@link ResultSet}.</p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     *     <li>Se declara una sentencia SQL que realiza una consulta con joins para unir la tabla de
     *         mensajes con las de usuarios emisores y receptores (basándose en sus UUIDs).</li>
     *     <li>La consulta incluye columnas como nombres de usuario, timestamp del mensaje, contenido y confirmaciones.</li>
     *     <li>Se ejecuta la consulta SQL a traves del metodo {@code executeQuery}, procesando cada
     *         fila obtenida para crear instancias de {@link MessageDTO}.</li>
     *     <li>Los objetos construidos se almacenan en una lista que es devuelta como resultado.</li>
     * </ol>
     * @return Una lista de objetos {@link MessageDTO}, cada uno representando un mensaje en la base
     * de datos. Si no hay registros, retorna una lista vacia.
     * @throws RuntimeException Si ocurre un problema interno relacionado con el manejo de consultas
     *                          SQL.
     *                          </body>
     */
    public final List<MessageDTO> pollAllMessagesInDatabase() {
        return executeReadOperation(() ->{//? Preparamos la sentencia SQL
            String pollAllMessagesWithNames =
                    """
                            SELECT
                            s.CLIENT_USERNAME as SENDER_NAME,
                            r.CLIENT_USERNAME as RECEIVER_NAME,
                            m.MESSAGE_TIMESTAMP,
                            m.MESSAGE_CONTENT,
                            m.SENDER_CONFIRMATION,
                            m.RECEIVER_CONFIRMATION
                            FROM MESSAGESTABLE m
                                 INNER JOIN CLIENTSTABLE s ON m.SENDER_UUID = s.CLIENT_UUID
                                 INNER JOIN CLIENTSTABLE r ON m.RECEIVER_UUID = r.CLIENT_UUID
                            ORDER BY m.MESSAGE_TIMESTAMP
                            """;
            List<MessageDTO> results = new ArrayList<>();
            //? 1. Ejecutamos la sentencia SQL
            try (DatabaseConnection.QueryResult resultingQuery =
                         this.databaseConnection.executeQuery(pollAllMessagesWithNames)) {
                while (resultingQuery.get_resultSet().next()) {
                    results.add(new MessageDTO(
                            resultingQuery.get_resultSet().getString("SENDER_NAME"),
                            resultingQuery.get_resultSet().getString("RECEIVER_NAME"),
                            resultingQuery.get_resultSet().getString("MESSAGE_CONTENT"),
                            resultingQuery.get_resultSet().getTimestamp("MESSAGE_TIMESTAMP"),
                            resultingQuery.get_resultSet().getBoolean("SENDER_CONFIRMATION"),
                            resultingQuery.get_resultSet().getBoolean("RECEIVER_CONFIRMATION")
                    ));
                }
            } catch (SQLException exception) {
                exception.printStackTrace();
                throw new RuntimeException("Error al pedir todos los mensajes de la base de datos: " +
                                                   exception.getMessage(), exception);
            }

            //? Retornamos los resultados
            return results;
        });
    }


    /*!  Data Modification Language (DML) statements*/

    /*
    ? La primera operacion que se va a realizar de lado del usuario (siempre es mas facil pensar
    ? desde esta vista), es la operacion de enviar un mensaje, lo que conlleva insertar un
    ? mensaje en la tabla correspondiente
     */

    /**
     * <body style="color: white;">
     * Este metodo inserta un mensaje en la base de datos utilizando los datos proporcionados en un
     * objeto {@link MessageDTO}.
     *
     * <p>
     * El metodo emplea una sentencia SQL preparada para evitar riesgos de SQL Injection, tomando
     * los valores del mensaje directo desde el DTO. Utiliza un modelo sincrono para procesar los
     * datos y verificar si la operacion fue exitosa.
     * </p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     * <li>Se prepara una sentencia SQL con placeholders para los valores del mensaje.</li>
     * <li>Se ejecuta dicha sentencia a traves del metodo {@code executeCommand} de la clase
     * {@link DatabaseConnection}, pasando los valores del DTO como parametros.</li>
     * <li>Si la operacion concluye satisfactoriamente, retorna {@code true}. En caso de fallar,
     * lanza una excepcion.</li>
     * </ol>
     * @param exMessageDTO Un objeto {@link MessageDTO} que contiene los datos necesarios para la
     *                     insercion del mensaje en la base de datos. No puede ser {@code null}.

     * @return {@code true} si la sentencia SQL se ejecuto con exito. Retorna {@code false} si no
     * e} si no hubo cambios en la base de datos.
     * @throws RuntimeException Si se produce un error SQL durante la ejecucion de la sentencia,
     *                          encapsulando la excepcion original {@link SQLException}.
     *                          </body>
     */
    public final boolean insertMessageSentIntoDatabase(MessageDTO exMessageDTO) {
        return executeWriteOperation(() ->{//? 1. Preparamos sentencia SQL
            String insertMessageIntoTable =
                    """
                            INSERT INTO MESSAGESTABLE (SENDER_UUID, RECEIVER_UUID,
                                                       MESSAGE_TIMESTAMP, MESSAGE_CONTENT,
                                                       SENDER_CONFIRMATION, RECEIVER_CONFIRMATION)
                            VALUES (?,?,?,?,?,?)
                            """;
            boolean changesDone;
            //? 2. Realizamos la setencia SQL
            try {

                if (this.databaseConnection == null || this.databaseConnection.isClosed()){
                    System.out.println("[DatabaseManagementSubsystem] Connection is closed, " +
                                               "attemptying reconnection");
                    this.databaseConnection = new DatabaseConnection();
                }
                changesDone = this.databaseConnection
                        .executeCommand(insertMessageIntoTable,
                                        exMessageDTO.toObjectArray());

                if (changesDone){
                    System.out.println("[DatabaseManagementSubsystem] Message inserted " +
                                               "succesfully, committing transaction...");
                    this.databaseConnection.commit();
                    System.out.println("[DatabaseManagementSubsystem] Transaction committed " +
                                               "succesfully");
                }else {
                    System.out.println("[DatabaseManagementSubsystem] No changes were made " +
                                               "during the transaction, rolling back...");
                }
            } catch (SQLException sqlException) {
                System.out.println("sqlException.getSQLState() = " + sqlException.getSQLState());
                System.out.println("sqlException.getErrorCode() = " + sqlException.getErrorCode());
                System.out.println("sqlException.getMessage() = " + sqlException.getMessage());
                System.out.println("sqlException.getNextException().getMessage() = " + sqlException.getNextException().getMessage());
                System.out.println("sqlException.getNextException().getSQLState() = " + sqlException.getNextException().getSQLState());
                System.out.println("sqlException.getNextException().getErrorCode() = " + sqlException.getNextException().getErrorCode());
                sqlException.printStackTrace();

                try{
                    System.out.println("[DatabaseManagementSubsystem] Rolling back transaction...");
                    this.databaseConnection.rollback();
                } catch (SQLException e){
                    System.out.println("[DatabaseManagementSubsystem] Error during rollback");
                    System.out.println("e.getMessage() = " + e.getMessage());
                    System.out.println("e.getSQLState() = " + e.getSQLState());
                    System.out.println("e.getErrorCode() = " + e.getErrorCode());
                }
                throw new RuntimeException("Error al insertar el mensaje en la base de datos: " +
                                                   sqlException.getMessage(), sqlException);
            }

            return changesDone;
        });
    }

    /*
    ? Otro proceso necesario puede ser el de insertar un cliente dentro de la tabla de clientes
     */

    /**
     * <body style="color: white;">
     * Este metodo inserta un cliente en la base de datos utilizando los datos proporcionados en un
     * objeto {@link ClientDTO}.
     *
     * <p>
     * Se emplea una sentencia SQL preparada que utiliza valores de entrada para garantizar la
     * seguridad frente a inyecciones SQL. El metodo toma los valores del objeto {@link ClientDTO},
     * los organiza de manera que correspondan a las columnas de la tabla {@code CLIENTSTABLE}, y
     * los inserta en la base de datos. Utiliza un modelo sincrono para ejecutar la operacion SQL.
     * </p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     * <li>Se prepara una sentencia SQL con placeholders para insertar los valores del cliente.</li>
     * <li>Se ejecuta la sentencia utilizando {@code executeCommand} de la clase
     * {@link DatabaseConnection}, asignando los valores del DTO mediante el metodo
     * {@code toObjectArray()} del {@link ClientDTO}.</li>
     * <li>Si la operacion concluye satisfactoriamente, retorna {@code true}. En caso de que no haya
     * modificaciones en la base de datos, retorna {@code false}.</li>
     * </ol>
     * @param exClientDTO Un objeto {@link ClientDTO} que contiene los datos necesarios para la
     *                    insercion del cliente en la base de datos. No puede ser {@code null}.
     * @return {@code true} si la sentencia SQL fue ejecutada exitosamente, o {@code false} si no se
     * realizaron modificaciones en la base de datos.
     * @throws RuntimeException Si se produce un error SQL durante la ejecucion de la sentencia,
     *                          encapsulando la excepcion original {@link SQLException}.
     *                          </body>
     */
    public final boolean insertClientCreatedIntoDatabase(ClientDTO exClientDTO) {
        return executeWriteOperation( ()-> {//? 1. Preparamos sentencia SQL y retornos
            String insertClientIntoTable =
                    """
                            INSERT INTO CLIENTSTABLE (CLIENT_UUID, CLIENT_USERNAME, CLIENT_PASSWORDHASH, CLIENT_PASSWORDSALTING)
                            VALUES (?,?,?,?)
                            """;
            boolean changesDone = false;
            //? 2. Ejecutamos la sentencia SQL
            try {
                changesDone = this.databaseConnection.executeCommand(insertClientIntoTable,
                                                                     exClientDTO.toObjectArray());
            } catch (SQLException sqlException) {
                sqlException.printStackTrace();
                throw new RuntimeException("Error al ingresar datos del DTO :" + exClientDTO.toString()
                                                   + "\n" +
                                                   ": " + sqlException.getMessage(), sqlException);
            }
            return changesDone;
        });
    }

    /**
     * <body style="color: white;">
     * Este metodo permite actualizar informacion asociada a un cliente registrado en la base de
     * datos.
     *
     * <p>El metodo trabaja en dos etapas principales:</p>
     * <ol>
     *     <li>Primero, utiliza el nombre de usuario proporcionado como parametro para obtener la informacion actual del cliente almacenada en la base de datos.</li>
     *     <li>Posteriormente, realiza la actualizacion de los campos "CLIENT_PASSWORDHASH" y "CLIENT_PASSWORDSALTING" en la base de datos, basado en los datos proporcionados en el objeto {@link ClientDTO} de entrada.</li>
     * </ol>
     * <h2>Errores posibles</h2>
     * <ul>
     *     <li>Error SQL: Puede ocurrir si hay problemas al ejecutar la consulta o actualizar los datos.</li>
     *     <li>{@link RuntimeException}: Lanza esta excepcion en caso de errores criticos que interrumpan el flujo, encapsulando la excepcion original.</li>
     * </ul>
     *
     * @param exUserUsername     Nombre de usuario del cliente que se quiere actualizar. No debe ser
     *                           {@code null}.
     * @param exClientDTONewData Un objeto {@link ClientDTO} que contiene los nuevos valores a
     *                           actualizar. No debe ser {@code null}.
     * @return {@code true} si la actualizacion se realizo exitosamente. Retorna {@code false} si no
     * se realizaron cambios en la base de datos.
     * @throws RuntimeException Si ocurre un error durante la ejecucion de la consulta SQL o el
     *                          update.
     *                          </body>
     */
    public final Boolean updateRegisteredClientInformation(String exUserUsername,
                                                           ClientDTO exClientDTONewData) {
        return executeReadOperation(() ->{ //? Preparamos la sentencia SQL y retorno
            String updateRegisteredClient =
                    """
                            UPDATE CLIENTSTABLE
                            SET CLIENT_PASSWORDHASH = ?,
                                CLIENT_PASSWORDSALTING = ?
                            WHERE CLIENT_UUID = ?
                            """;
            AtomicBoolean changesDone = new AtomicBoolean(false);
            //? 1. Ejecutamos la sentencia SQL con los datos del DTONuevo
            Optional<ClientDTO> antiguoClientConUUID =
                    this.pollAllRegisteredInformationPerUsernameInDatabase(exUserUsername);
            antiguoClientConUUID.ifPresent(new Consumer<ClientDTO>() {
                @Override
                public void accept(ClientDTO oldClientDTO) {
                    try {
                        changesDone.set(databaseConnection.executeCommand(updateRegisteredClient,
                                                                          new Object[]{
                                                                                  exClientDTONewData._clientPwdHash(),
                                                                                  exClientDTONewData._clientSaltHash(),
                                                                                  oldClientDTO._clientUUID()}));
                    } catch (SQLException exception) {
                        exception.printStackTrace();
                        throw new RuntimeException("Error al actualizar los datos del usuario: " +
                                                           exUserUsername + "\n" +
                                                           ": " + exception.getMessage(), exception);
                    }
                }
            });

            return changesDone.get();
        });
    }

    /**
     * <body style="color: white;">
     * Este metodo elimina toda la informacion asociada con un cliente registrado en la base de
     * datos, utilizando el nombre de usuario como criterio de eliminacion.
     *
     * <p>El metodo funciona en dos etapas principales:</p>
     * <ol>
     *     <li>Primero, utiliza el nombre de usuario proporcionado como parametro para buscar y verificar
     *         si el cliente existe en la base de datos con los datos obtenidos mediante el metodo
     *         {@code pollAllRegisteredInformationPerUsernameInDatabase}. Si no existe, no realiza
     *         ninguna accion.</li>
     *     <li>Posteriormente, ejecuta la sentencia SQL preparada para eliminar al cliente usando
     *         {@link DatabaseConnection#executeCommand(String, Object...)}.</li>
     * </ol>
     *
     * @param exUserUsername Nombre de usuario del cliente que se desea eliminar. No puede ser
     *                       {@code null}.
     * @return {@code true} si la eliminacion fue satisfactoria, o {@code false} si no se realizaron
     * cambios en la base de datos.
     * @throws RuntimeException Si ocurre un error durante la ejecucion de la consulta SQL o la
     *                          eliminacion.
     *                          </body>
     */
    public final Boolean dropAllRegisteredClientInformation(String exUserUsername) {
        return executeWriteOperation( () -> {//? Preparamos la sentencia SQL y el retorno
            String dropBasedOnUsername =
                    """
                            DELETE
                            FROM CLIENTSTABLE
                            WHERE CLIENT_USERNAME = ?
                            """;
            AtomicBoolean changesDone = new AtomicBoolean(false);

            //? 1. Ejecutamos la sentencia SQL
            Optional<ClientDTO> resultFromQuery =
                    this.pollAllRegisteredInformationPerUsernameInDatabase(exUserUsername);
            resultFromQuery.ifPresent(new Consumer<ClientDTO>() {
                @Override
                public void accept(ClientDTO clientDTO) {
                    try {
                        changesDone.set(databaseConnection.executeCommand(dropBasedOnUsername, exUserUsername));
                    } catch (SQLException e) {
                        e.printStackTrace();
                        throw new RuntimeException("Error al eliminar los datos del usuario: " +
                                                           exUserUsername + "\n" +
                                                           ": " + e.getMessage(), e);
                    }
                }
            });

            //? 2. Retornamos los resultados
            return changesDone.get();
        });
    }

    /*
     ? El ultimo metodo DML que se puede requerir es el eliminar los mensajes de un usuario, para
     ? hacer esto debemos buscar todos aquellos en donde se UUID se use, tanto como sender o
     ? receiver basados en un tipo de registro ClientDTO que se regrese por otro metodo adicional!
     */

    /**
     * <body style="color: white;">
     * Este metodo elimina todos los mensajes registrados en la base de datos asociados a un cliente
     * especifico.
     *
     * <p>
     * Utiliza el nombre de usuario proporcionado para identificar al cliente correspondiente en la
     * tabla {@code CLIENTSTABLE} y realiza una eliminacion de todos los mensajes en la tabla
     * {@code MESSAGESTABLE} en los que el cliente participa como remitente o receptor. La operacion
     * se realiza de forma atomica para asegurar la consistencia de los datos.
     * </p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     *     <li>Identifica al cliente con el nombre de usuario mediante el metodo {@link #pollAllRegisteredInformationPerUsernameInDatabase(String)}.</li>
     *     <li>Si el cliente existe, se prepara una declaracion SQL que elimina todos los registros en {@code MESSAGESTABLE} donde su UUID
     *         aparece como {@code SENDER_UUID} o {@code RECEIVER_UUID}.</li>
     *     <li>Se ejecuta la sentencia SQL utilizando el metodo {@code executeCommand} de {@link DatabaseConnection} con los UUIDs obtenidos.</li>
     *     <li>Si la operacion se ejecuta con exito, retorna {@code true}, indicando que se realizaron cambios en la base de datos.</li>
     * </ol>
     * @param exUserUsername El nombre de usuario del cliente cuyos mensajes deben eliminarse. No
     *                       debe ser {@code null}.
     * @return {@code true} si la eliminacion fue exitosa y se realizaron cambios en la base de
     * datos. De lo contrario, retorna {@code false}.
     * @throws RuntimeException Si se produce algun error SQL o si la ejecucion del metodo falla
     *                          debido a problemas internos.
     *                          </body>
     */
    public final Boolean dropAllRegisteredMessagesByClient(String exUserUsername) {
        return executeWriteOperation( () -> {//? Preparamos la sentencia SQL
            String dropAllRegisteredMessagesPerClient =
                    """
                            DELETE
                            FROM MESSAGESTABLE
                            WHERE SENDER_UUID = ?
                            OR RECEIVER_UUID = ?
                            """;
            AtomicBoolean changesDone = new AtomicBoolean(false);
            Optional<ClientDTO> clientDTOOptional =
                    this.pollAllRegisteredInformationPerUsernameInDatabase(exUserUsername);
            if (clientDTOOptional.isPresent()) {
                try {
                    changesDone.set(this.databaseConnection.executeCommand(dropAllRegisteredMessagesPerClient,
                                                                           new Object[]{clientDTOOptional.get()._clientUUID(),
                                                                                   clientDTOOptional.get()._clientUUID()}));
                } catch (SQLException exception) {
                    exception.printStackTrace();
                    throw new RuntimeException("Error al eliminar los mensajes del usuario: " +
                                                       exUserUsername + "\n" +
                                                       ": " + exception.getMessage(), exception);
                }
            }

            return changesDone.get();
        });
    }

    /**
     * <body style="color: white;">
     * Este metodo permite cerrar la conexion activa con la base de datos administrada por la
     * aplicacion.
     *
     * <p>Su funcionamiento interno consta en delegar la tarea de cierre a la instancia de la clase
     * {@link DatabaseConnection}. Utiliza un bloque try-catch para manejar las posibles excepciones
     * SQL que puedan arrojarse. En caso de que no se pueda cerrar la conexion, lanza una excepcion
     * {@link RuntimeException} encapsulando la causa original.</p>
     *
     * <h2>Funcionamiento</h2>
     * <ol>
     *     <li>Invoca el metodo {@code shutdownDatabaseConnection} dentro de la clase {@link DatabaseConnection}.</li>
     *     <li>En caso de fallo de esta operacion, la excepcion {@link SQLException} se captura, y el metodo arroja
     *         una {@link RuntimeException} con toda la informacion relacionada al error.</li>
     * </ol>
     * @throws RuntimeException Si ocurre un error al intentar cerrar la conexion con la base de
     *                          datos.
     *                          </body>
     */
    public final void shutDownDatabaseConnection() {
        try {
            this.databaseConnection.shutdownDatabaseConnection();
            System.out.println("Database connection closed successfully");
        } catch (SQLException exception) {
            if (exception.getSQLState() != null &&
                    (exception.getSQLState().equals("08006") || exception.getSQLState().equals("XJ015"))) {
                System.out.println("Derby database shut down normally");
            } else {
                exception.printStackTrace();
                System.err.println("Error closing database connection: " + exception.getMessage());
            }
        }
    }


    public static void main(String[] args) {
        DatabaseManagementSystem db = new DatabaseManagementSystem();
        System.out.println(db.pollAllClientUsernamesInDatabase());
        var result = db.pollAllSentAndReceivedMessagesByUsername("NinjaBaker");
        result.entrySet().forEach(new Consumer<Map.Entry<String, List<MessageDTO>>>() {
            @Override
            public void accept(Map.Entry<String, List<MessageDTO>> stringListEntry) {
                System.out.println(stringListEntry.getKey() + "\n");
                stringListEntry.getValue().forEach(System.out::println);
            }
        });

        System.out.println(db.pollAllRegisteredInformationPerUsernameInDatabase("Juan"));
        System.out.println(db.pollAllRegisteredInformationPerUsernameInDatabase(("NinjaBaker")));

        /*! POC 01: Creating username, creating a message, sending a message, querying messages,
        /*!removing messages, queryingagain, removing user*/
        ClientPOJO client = new ClientPOJO("fPasmay", "1234");
        db.insertClientCreatedIntoDatabase(client.transformIntoDTO());
        System.out.println((db.pollAllRegisteredUsersInDatabase()));
        MessagePOJO messagePOJO = new MessagePOJO(client.get_clientUUID(),
                                                  db.pollAllRegisteredInformationPerUsernameInDatabase("NinjaBaker").get()._clientUUID(),
                                                  "Hello There!", Timestamp.from(Instant.now()));
        messagePOJO.set_messageSenderAcknowledge(true);
        messagePOJO.set_messageReceiverAcknowledge(true);
        db.insertMessageSentIntoDatabase(messagePOJO.transformToMessageDTO());
        result = db.pollAllSentAndReceivedMessagesByUsername("fPasmay");
        result.entrySet().forEach(new Consumer<Map.Entry<String, List<MessageDTO>>>() {
            @Override
            public void accept(Map.Entry<String, List<MessageDTO>> stringListEntry) {
                System.out.println(stringListEntry.getKey() + "\n");
                stringListEntry.getValue().forEach(System.out::println);
            }
        });
        result = db.pollAllSentAndReceivedMessagesByUsername("fPasmay");
        result.entrySet().forEach(new Consumer<Map.Entry<String, List<MessageDTO>>>() {
            @Override
            public void accept(Map.Entry<String, List<MessageDTO>> stringListEntry) {
                System.out.println(stringListEntry.getKey() + "\n");
                stringListEntry.getValue().forEach(System.out::println);
            }
        });
        System.out.println(db.pollAllMessagesInDatabase());
        System.out.println((db.pollAllRegisteredUsersInDatabase()));

        db.shutDownDatabaseConnection();
    }
}
