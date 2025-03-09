package com.evolvlabs.multiuserchatgui.ServerSideBackend.PersistencyEngine;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-2025
 * @description: El presente archivo encapsula el proceso de conexion con la base de datos del
 * sistema. La clase presenta simples metodos de una pipeline ETL en tamano reducido que permite
 * extraer datos de la base de datos, transformarlos en un Result set y posteriormente en
 * formatos aceptados en JavaFX y por ultimo los carga a la pantalla en JavaFX con diversos
 * frameworks desarollados en otros paquetes de la aplicacion. La idea de esta clase es tener
 * tres metodos unicos
 * <ol>
 *     <li><b>Constructor</b>: permite crear la base de datos (si esta no existe) y cargar
 *     datos simples, o conectarse simplemente a la base de datos si esta ya existe.</li>
 *     <li><b>executeQuery(String query)</b>: metodo que permite dictar una sentencia de DQL
 *     determinada por una parte externa a esta clase y retornar los resultados de esta query.
 *     Es una API de bajo nivel que abstrae la conexion y ejecucion de queries, reportando
 *     errores a APIs upseriores</li>
 *     <li><b>executeCommand(String command)</b>: metodo que permite dictar una setencia de DDL u
 *     otros a la base de datos. Estas sentencias se dictan por partes externas a esta API como
 *     en el cas de <b>executeQuery()</b>
 *     </li>
 * </ol>
 * En base a estos metodos, la API de esta clase se transforma en una interface simple de
 * comunicacion con la estructura interna de persistencia de la base de datos.
 */
public class DatabaseConnection {

    /*! Parametros Internos*/
    /**
     * String representativa de la cadena de conexion que se requiere para conectarse con la base
     * de datos (En general la base de datos se crea en el directorio determinado por
     * {@code System.getProperty("user.dir")}, dado que este indica el PATH de ejecucion y es
     * local a todos los archivos.
     */
    private String CONNECTION_STRING;
    private Connection databaseConnection;


    /**
     * <body style="color: white;">
     * Constructor encargado de realizar la conexion con la base de datos del sistema. Si la base de
     * datos no existe, se crea un nuevo archivo en el directorio del usuario
     * {@code System.getProperty("user.dir")}. Este metodo maneja los siguientes pasos:
     * <ul>
     *     <li>Verifica si el directorio de la base de datos ya existe. Si no, la genera junto con las tablas requeridas.</li>
     *     <li>Evalua si el archivo existente puede ser utilizado como directorio o base de datos valida.</li>
     *     <li>Inicializa la informacion necesaria dentro de las tablas si estas estan vacias.</li>
     *     <li>Configura la cadena de conexion base utilizando Apache Derby y su motor embebido.</li>
     * </ul>
     *
     * <b>Notas importantes:</b>
     * <ul>
     *     <li>Si el archivo existente no es un directorio, lanza {@link java.lang.IllegalStateException}.</li>
     *     <li>Posibles errores en la creacion o conexion de la base de datos se manejan mediante {@link java.sql.SQLException}.</li>
     * </ul>
     *
     * @throws IllegalStateException Si el archivo donde deberia ubicarse la base de datos no es un
     *                               directorio valido.
     * @throws RuntimeException      Si ocurre un error durante la creacion o conexion con la base
     *                               de datos.
     * @see java.sql.Connection
     * @see java.sql.DriverManager
     * @see java.lang.System
     * </body>
     */
    public DatabaseConnection() {

        System.setProperty("derby.stream.error.file", "derby.log");
        System.setProperty("derby.language.logStatementText", "true");
        System.setProperty("derby.language.logQueryPlan", "true");
        try {
            String derbyLogPath = System.getProperty("user.dir") + "/derby.log";
            PrintWriter pw = new PrintWriter(new FileOutputStream(derbyLogPath, true));
            DriverManager.setLogWriter(pw);
        } catch (FileNotFoundException e) {
            System.err.println("Could not create Derby log file: " + e.getMessage());
        }

        //? 1. Paso base: Busquemos el directorio de la base de datos, si este existe entonces la
        //? base de datos ya se inicializo
        Path databaseLocation = Path.of(System.getProperty("user.dir"), "MultiUserChatGUIDB");
        if (Files.exists(databaseLocation)){
            System.out.println("El archivo con el nombre de la base de datos existe, realizando " +
                               "revisiones internas...");
            //! Si el path existe ahora tenemos dos caminos
            /*
             ? Si el archivo existe pero no es una carpeta entonces alguien modifico el
             ? directorio y corrompio el programa, debemos de enviar un error. Si existe y es
             ? directorio, entonces podemos proceder a revisar si tiene informacion!
            */
            if (!Files.isDirectory(databaseLocation)){
                throw new IllegalStateException("Fatal Error 0x001 - [Raised] El directorio en " +
                                                        "donde se esperaria encontrar la base de " +
                                                        "datos, no es un directorio, sino un " +
                                                        "archivo. Favor elimine el archivo con el" +
                                                        " mismo nombre de la base de datos y " +
                                                        "reintente la conexion de nuevo.");
            }
            else {
                //? La base de datos es un folder, tratemos de conectarnos
                System.out.println("El archivo encontrado es una carpeta, revisando contenido " +
                                   "interno...");
                this.CONNECTION_STRING = "jdbc:derby:" + databaseLocation.toString() + ";";

                //? Intentemos conectarnos a la base de datos y revisar si tiene contenido
                try (Connection databaseConnection = DriverManager.getConnection(this.CONNECTION_STRING)){
                    DatabaseMetaData metaDataFromDB = databaseConnection.getMetaData();
                    //? Tomemos la info de las tablas
                    ResultSet tables = metaDataFromDB.getTables(null, null, "%", new String[]{
                            "TABLE"});
                    boolean hasNoTables = !tables.next();
                    tables.close();

                    //? En este punto si hasNoTables es verdadero, tenemos que inicializar las
                    // tablas
                    if (hasNoTables){
                        System.out.println("Las tablas no existen, creando tablas...");
                        initDatabaseTables(databaseConnection);
                        System.out.println("Tablas creadas, inicializando informacion...");
                        initDatabaseInformation(databaseConnection);
                    } else{
                        System.out.println("Las tablas existen, revisando informacion...");
                        //? En este punto las tablas existen pero pueden estar sin datos
                        // Tables exist, let's check if they have data
                        try (Statement stmt = databaseConnection.createStatement()) {
                            // Check ClientsTable
                            ResultSet clientsCount = stmt.executeQuery("SELECT COUNT(*) FROM ClientsTable");
                            clientsCount.next();
                            int numClients = clientsCount.getInt(1);

                            // Check MessagesTable
                            ResultSet messagesCount = stmt.executeQuery("SELECT COUNT(*) FROM MessagesTable");
                            messagesCount.next();
                            int numMessages = messagesCount.getInt(1);

                            if (numClients == 0 && numMessages == 0) {
                                // Both tables are empty, initialize them
                                System.out.println("Tablas vacias, inicializando informacion...");
                                this.initDatabaseInformation(databaseConnection);
                            } else if (numClients == 0){
                                System.out.println("Tabla de clientes vacia, inicializando " +
                                                   "informacion...");
                                initDatabaseClientInformation(databaseConnection);
                            } else if (numMessages == 0){
                                System.out.println("Tabla de mensajes vacia, inicializando " +
                                                   "informacion...");
                                initDatabaseMessagesInformation(databaseConnection);
                            }

                            clientsCount.close();
                            messagesCount.close();
                        }

                    }
                } catch (SQLException exception){
                    System.err.println("Error en la lectura de la base de datos: " + exception.getMessage());
                    System.err.println("Error en la lectura de la base de datos: " + exception.getErrorCode());
                }
            }
        } else {
            //? La base de datos no existe y debemos crearla
            this.CONNECTION_STRING = "jdbc:derby:" + databaseLocation.toString() + ";create" +
                    "=true;";
            try (Connection connection = DriverManager.getConnection(this.CONNECTION_STRING)){
                initDatabaseTables(connection);
                initDatabaseInformation(connection);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        //? Guardamos la conexion
        try{
            this.databaseConnection = DriverManager.getConnection(this.CONNECTION_STRING);
            this.databaseConnection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            this.databaseConnection.setAutoCommit(false);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }


    /**
     * <body style="color: white;">
     * Este metodo permite ejecutar una sentencia SQL de tipo DQL (Data Query Language) y retornar
     * el resultado de la misma encapsulado en un objeto {@link QueryResult}. Utilizando sentencias
     * preparadas ({@link java.sql.PreparedStatement}), este metodo asegura la ejecucion controlada
     * y parametrizada de consultas con los valores suministrados.
     *
     * <h3 style="color: white;">Funcionamiento</h3>
     * <ul>
     *     <li>Recibe una sentencia SQL como {@code String} y un conjunto de parametros dinámicos.</li>
     *     <li>Valida que la cantidad de parametros proporcionados coincida exactamente con los
     *     marcadores de posicion en la sentencia SQL.</li>
     *     <li>Asigna los valores a cada marcador dinamico utilizando el indice correspondiente.</li>
     *     <li>Ejecuta la consulta y encapsula el resultado dentro de un objeto {@link QueryResult}.</li>
     * </ul>
     *
     * <h3 style="color: white;">Notas importantes</h3>
     * <ul>
     *     <li>Se lanza {@link NullPointerException} si el parametro {@code query} es nulo.</li>
     *     <li>Se lanza {@link IllegalStateException} si la cantidad de parametros no coincide con los
     *     marcadores de posicion en el statement SQL.</li>
     *     <li>Se lanza {@link SQLException} si ocurre un error durante la ejecucion SQL.</li>
     * </ul>
     *
     * @param query      Sentencia SQL a ejecutar, proporcionada como un objeto {@link String}. No
     *                   debe ser nulo.
     * @param parameters Uno o varios parametros de tipo {@link Object} que seran asignados a los
     *                   marcadores de posicion dentro de la sentencia SQL. La cantidad debe
     *                   coincidir con la cantidad de marcadores definidos en la consulta.
     * @return Un objeto {@link QueryResult} que encapsula el {@link java.sql.PreparedStatement} y
     * el {@link java.sql.ResultSet} resultante.
     * @throws NullPointerException  Si el parametro {@code query} proporcionado es nulo.
     * @throws IllegalStateException Si la cantidad de parametros dinamicos no coincide con los
     *                               marcadores de posicion en el query SQL.
     * @throws SQLException          Si ocurre algun error durante el proceso de preparacion o
     *                               ejecucion SQL.
     * @see java.sql.Connection
     * @see java.sql.PreparedStatement
     * @see java.sql.ResultSet
     * @see QueryResult
     * </body>
     */
    public QueryResult executeQuery(String query, Object... parameters) throws SQLException {
        QueryResult queryResult = null;
        if (query != null) {
            PreparedStatement queryStatement = this
                    .databaseConnection.prepareStatement(query,
                                                         ResultSet.TYPE_FORWARD_ONLY,
                                                         ResultSet.CLOSE_CURSORS_AT_COMMIT);
            if (queryStatement.getParameterMetaData().getParameterCount() != parameters.length) {
                queryStatement.close();
                throw new IllegalStateException("Error Code 0x001 - [Raised] La cantidad de " +
                                                        "parametros no coincide con la cantidad " +
                                                        "de marcadores de posicion en el " +
                                                        "statement" +
                                                        ".");
            }
            for (int i = 0; i < parameters.length; i++) {
                queryStatement.setObject(i + 1, parameters[i]);
            }
            var resultSet = queryStatement.executeQuery();
            queryResult = new QueryResult(queryStatement, resultSet);
            this.databaseConnection.commit();
        } else {
            throw new NullPointerException("Error Code 0x001 - [Raised] Query no puede ser nulo" +
                                                   " en el metodo executeQuery.");
        }

        return queryResult;
    }


    /**
     * <body style="color: white;">
     * Este metodo permite ejecutar una sentencia SQL que no requiere retorno de informacion (como
     * sentencias de tipo DDL, DML o similares) utilizando una declaracion preparada
     * ({@link java.sql.PreparedStatement}) para incluir parametros dinamicos.
     *
     * <ul>
     *     <li>Si el parametro {@code command} no es nulo, el metodo crea un
     *     {@link java.sql.PreparedStatement} basado en la conexion activa a la base de datos.</li>
     *     <li>Verifica que la cantidad de parametros proporcionados coincida con los marcadores
     *     de posicion definidos en el comando SQL.</li>
     *     <li>Asigna los valores de los parametros invocados a los marcadores de posicion en el orden
     *     correspondiente y ejecuta el comando SQL.</li>
     * </ul>
     *
     * <b>Notas importantes:</b>
     * <ul>
     *     <li>El metodo no soporta sentencias nulas, lanzando una excepcion
     *     {@link java.lang.NullPointerException} en caso de recibir un comando nulo.</li>
     *     <li>Se lanza {@link java.lang.IllegalStateException} si la cantidad de parametros dinamicos
     *     no coincide con los marcadores de posicion en el comando SQL.</li>
     *     <li>Cualquier error SQL detectado durante la ejecucion es reportado mediante una
     *     excepcion de tipo {@link java.sql.SQLException}.</li>
     * </ul>
     *
     * @param command    Sentencia SQL a ejecutar en forma de {@link java.lang.String}. No puede ser
     *                   nula.
     * @param parameters Uno o varios parametros de tipo {@link java.lang.Object} que seran
     *                   asignados dinamicamente a los marcadores de posicion del comando SQL. La
     *                   cantidad de parametros debe coincidir exactamente con los marcadores del
     *                   comando.
     * @return Una flag de tipo {@link Boolean} que determina si la operacion se realizo con
     * exito o no.
     * @throws NullPointerException  Si el parametro {@code command} es nulo.
     * @throws IllegalStateException Si el numero de parametros proporcionados no coincide con los
     *                               marcadores de posicion en el comando SQL.
     * @throws SQLException          Si ocurre un error durante la preparacion o ejecucion de la
     *                               sentencia SQL.
     * @see java.sql.Connection
     * @see java.sql.PreparedStatement
     * </body>
     */
    public boolean executeCommand(String command, Object... parameters) throws SQLException {
        if (command != null) {
            PreparedStatement preparedStatement =
                    this.databaseConnection.prepareStatement(command);
            if (preparedStatement.getParameterMetaData().getParameterCount() != parameters.length) {
                throw new IllegalStateException("Error Code 0x001 - [Raised] La cantidad de " +
                                                        "parametros no coincide con la cantidad " +
                                                        "de marcadores de posicion en el " +
                                                        "statement" +
                                                        ".");

            } else {
                for (int i = 0; i < parameters.length; i++) {
                    preparedStatement.setObject(i + 1, parameters[i]);
                }
            }
            var result = preparedStatement.execute();
            var updateCount = preparedStatement.getUpdateCount();
            this.databaseConnection.commit();
            return updateCount > 0;

        } else {
            throw new NullPointerException("Error Code 0x001 - [Raised] Command no puede ser nulo" +
                                                   " en el metodo executeQuery.");
        }
    }


    public void shutdownDatabaseConnection() throws SQLException {
        try {
            // First check if there's an active transaction and commit or rollback
            if (!this.databaseConnection.getAutoCommit()) {
                try {
                    this.databaseConnection.commit();
                } catch (SQLException e) {
                    this.databaseConnection.rollback();
                }
            }
            // Then close the connection
            this.databaseConnection.close();
        } catch (SQLException e) {
            // Make sure we attempt to rollback if something goes wrong
            try {
                this.databaseConnection.rollback();
            } catch (SQLException ignored) {
                // If rollback fails, we can't do much more
            }
            throw e; // Rethrow the original exception
        }
    }

    /**
     * <body style="color: white;">
     * Este metodo privado y final se encarga de crear las tablas principales necesarias para el
     * funcionamiento del sistema de persistencia de datos. Utiliza sentencias SQL para construir
     * las tablas y maneja posibles errores mediante bloqueos try-catch. Las tablas creadas son:
     * <ul>
     *     <li><b>ClientsTable</b>: tabla utilizada para almacenar informacion acerca de
     *     los clientes, incluyendo identificadores unicos (UUID), nombres de usuario y
     *     detalles relacionados con la seguridad de las contrasenas como hashes y salting.</li>
     *     <li><b>MessagesTable</b>: tabla utilizada para registrar mensajes enviados y
     *     recibidos por los clientes, incluyendo IDs de remitente y receptor, contenido
     *     del mensaje y marcadores de confirmacion.</li>
     * </ul>
     *
     * @param databaseConnection La conexion activa a la base de datos en la cual se ejecutaran las
     *                           sentencias para crear las tablas.
     * @throws SQLException Si ocurre un error al ejecutar las sentencias SQL para crear las tablas,
     *                      este sera mostrado en la consola de errores.
     * @see Connection
     * @see Statement
     * </body>
     */
    private final void initDatabaseTables(Connection databaseConnection) {

        //? First block, create the client table
        try(Statement statementOne = databaseConnection.createStatement()){
            String createClientTableStatement = """
                                                create table ClientsTable(
                                                   client_UUID VARCHAR(512) NOT NULL PRIMARY KEY,
                                                   client_Username VARCHAR(30) NOT NULL UNIQUE,
                                                   Client_PasswordHash VARCHAR(512) NOT NULL,
                                                   Client_PasswordSalting VARCHAR(512) NOT NULL
                                                )
                                                """;
            statementOne.execute(createClientTableStatement);
        } catch (SQLException exception){
            System.err.println("Error en la creacion de la tabla de clientes: " + exception.getMessage());
            System.err.println("Error en la creacion de la tabla de clientes: " + exception.getErrorCode());
        }
        //? Second block, create the Messages table
        try (Statement statementTwo = databaseConnection.createStatement()){
            String createMessagesTableStatement = """
                                                  create table MessagesTable(
                                                      sender_UUID VARCHAR(512) NOT NULL,
                                                      receiver_UUID VARCHAR(512) NOT NULL,
                                                      MESSAGE_TIMESTAMP TIMESTAMP  NOT NULL default CURRENT_TIMESTAMP,
                                                      MESSAGE_CONTENT VARCHAR(1024) NOT NULL,
                                                      sender_Confirmation BOOLEAN NOT NULL DEFAULT TRUE,
                                                      receiver_Confirmation BOOLEAN NOT NULL DEFAULT TRUE
                                                  )
                                                  """;
            statementTwo.execute(createMessagesTableStatement);
        } catch (SQLException exception) {
            System.err.println("Error en la creacion de la tabla de mensajes: " + exception.getMessage());
            System.err.println("Error en la creacion de la tabla de mensajes: " + exception.getErrorCode());
        }
    }

    /**
     * <body style="color: white;">
     * Este metodo privado se encarga de inicializar informacion dentro de las tablas principales de
     * la base de datos. Utiliza sentencias SQL para insertar datos predefinidos en las tablas,
     * asegurando que existan datos iniciales para el correcto funcionamiento de la aplicacion. El
     * metodo utiliza conexiones activas a la base de datos proporcionadas como parametro para
     * ejecutar estas sentencias.
     *
     * <ul>
     *     <li><b>Tabla ClientsTable:</b> Se insertan usuarios ficticios con UUIDs unicos, nombres de usuarios, hashes de contrasenas
     *     y valores de salting.</li>
     *     <li><b>Tabla MessagesTable:</b> Se insertan mensajes ficticios entre los usuarios creados en la ClientsTable, incluyendo el contenido del mensaje,
     *     marcas de confirmacion y marcas de tiempo generadas automaticamente.</li>
     * </ul>
     * @param databaseConnection La conexion activa a la base de datos que sera utilizada para
     *                           ejecutar las inserciones. Debe ser una instancia valida y
     *                           previamente inicializada de {@link java.sql.Connection}.
     * @throws NullPointerException Si la conexion proporcionada es nula.
     * @see java.sql.Connection
     * @see java.sql.Statement
     * @see java.sql.SQLException
     * </body>
     */
    private final void initDatabaseInformation(Connection databaseConnection) {

        //? First statement: introduce information into ClientsTable
        initDatabaseClientInformation(databaseConnection);
        //? Second Statement: introdcue information into MessagesTable
        initDatabaseMessagesInformation(databaseConnection);
    }

    private static void initDatabaseMessagesInformation(Connection databaseConnection) {
        try(Statement statementTwo = databaseConnection.createStatement()){
            String insertIntoMessages =
                    """
                    INSERT INTO MESSAGESTABLE (SENDER_UUID, RECEIVER_UUID, MESSAGE_CONTENT, MESSAGE_TIMESTAMP) VALUES                                                                                                -- Pickle_Rick and CaptainNaptime conversation
                        ('86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416', '13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', 'Wake up! The multiverse needs you!', CURRENT_TIMESTAMP),
                        ('13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', '86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416', 'Five more minutes...', CURRENT_TIMESTAMP),
                        ('86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416', '13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', 'But the universe is collapsing!', CURRENT_TIMESTAMP),
                        ('13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', '86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416', '*snoring noises*', CURRENT_TIMESTAMP),
                    
                                                                                                                    -- TacoTuesday and NinjaBaker
                        ('ae47c84dd36aba14ed818075444e9fd61d81f54eb2b9c439d811ab8e793629d852', 'bf7749451607ca28f6b158de184cc033adb30e8f62e6aa3911fc89fa902e466e4809', 'Can you bake tacos?', CURRENT_TIMESTAMP),
                        ('bf7749451607ca28f6b158de184cc033adb30e8f62e6aa3911fc89fa902e466e4809', 'ae47c84dd36aba14ed818075444e9fd61d81f54eb2b9c439d811ab8e793629d852', 'I can ninja-chop the ingredients!', CURRENT_TIMESTAMP),
                    
                                                                                                                    -- DragonWhisperer and TacoTuesday
                        ('a099f6c88d10e0fb4661a3d159dea3810e3729d4069cefc64fa4782a6c8c91fd6530', 'ae47c84dd36aba14ed818075444e9fd61d81f54eb2b9c439d811ab8e793629d852', 'Do dragons like tacos?', CURRENT_TIMESTAMP),
                        ('ae47c84dd36aba14ed818075444e9fd61d81f54eb2b9c439d811ab8e793629d852', 'a099f6c88d10e0fb4661a3d159dea3810e3729d4069cefc64fa4782a6c8c91fd6530', 'Only if they''re not too spicy!', CURRENT_TIMESTAMP),
                    
                        -- NinjaBaker and DragonWhisperer
                        ('bf7749451607ca28f6b158de184cc033adb30e8f62e6aa3911fc89fa902e466e4809', 'a099f6c88d10e0fb4661a3d159dea3810e3729d4069cefc64fa4782a6c8c91fd6530', 'Need stealth cookies for your dragon?', CURRENT_TIMESTAMP),
                        ('a099f6c88d10e0fb4661a3d159dea3810e3729d4069cefc64fa4782a6c8c91fd6530', 'bf7749451607ca28f6b158de184cc033adb30e8f62e6aa3911fc89fa902e466e4809', 'Dragons prefer loud crunchy cookies actually!', CURRENT_TIMESTAMP),
                    
                        -- Pickle_Rick back to CaptainNaptime (repeated connection)
                        ('86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416', '13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', 'WAKE UP! I turned myself into a pickle again!', CURRENT_TIMESTAMP),
                        ('13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', '86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416', 'zzzZZZzzz... pickles... zzZZZzz...', CURRENT_TIMESTAMP)
                    """;
            statementTwo.execute(insertIntoMessages);
        } catch (SQLException sqlException){
            System.err.println("Error en la creacion de la tabla de mensajes: " + sqlException.getMessage());
            System.err.println("Error en la creacion de la tabla de mensajes: " + sqlException.getErrorCode());
        }
    }
    private static void initDatabaseClientInformation(Connection databaseConnection) {
        //? First statement: introduce information into ClientsTable
        try (Statement statementOne = databaseConnection.createStatement()){
            String insertIntoClients = """
                insert into CLIENTSTABLE (CLIENT_UUID, CLIENT_USERNAME, CLIENT_PASSWORDHASH, CLIENT_PASSWORDSALTING) VALUES
                        ('86759a43c0ef801755f0a9bb8f7e3a0fbe5b17f85ddeba48886ff83def5579802416','Pickle_Rick', 'ZHO74Gh13tzt3KJ8Hvo74+PRf6VO+Rh+p6ZVnI6oRQE=','jYOkSPQarY3PJJswYHrSHg=='),
                        ('13aa997f3c45270773ba14c600399d16662ffc4d10c5c441de811a48fa2c89161270', 'CaptainNaptime','iHnTD6K6xQDb0X5KgYGwrxDIOQEdMf2XYxB1yxkCNRc=','ycZXTFfr1RNAEP3rt1FQsw=='),
                        ('ae47c84dd36aba14ed818075444e9fd61d81f54eb2b9c439d811ab8e793629d852', 'TacoTuesday', 'xNwLorEVMJOOE2F6pZoLJF8s/4wg8dwsV2vnq3Piz7c=', 'MdBRawjob5PtOnGe926Q4A=='),
                        ('bf7749451607ca28f6b158de184cc033adb30e8f62e6aa3911fc89fa902e466e4809', 'NinjaBaker', 'vDbpptRzs0elt8H5pzGX4AbI46ZyuE8BgG2FaCp7Clk=','sZVYAgP7Ppkq3b7hv8721Q=='),
                        ('a099f6c88d10e0fb4661a3d159dea3810e3729d4069cefc64fa4782a6c8c91fd6530','DragonWhisperer', 'vOvYqVQ1O2LbGQIacIQ14HPVWdiFg1e3bNx7MoHibXY=', 'CUJH/8n0X1rj/ouG5JqRQg==')
                """;
            statementOne.execute(insertIntoClients);
        } catch (SQLException sqlException){
            System.err.println("Error en la creacion de la tabla de mensajes: " + sqlException.getMessage());
            System.err.println("Error en la creacion de la tabla de mensajes: " + sqlException.getErrorCode());
        }
    }

    public boolean isClosed() throws SQLException {
        return this.databaseConnection.isClosed() ;
    }

    public void commit() throws SQLException {
        this.databaseConnection.commit();
    }

    public void rollback() throws SQLException {
        this.databaseConnection.rollback();
    }


    /**
     * <body style="color: white;">
     * La clase {@code QueryResult} encapsula el resultado de una operacion SQL realizada con
     * {@link java.sql.PreparedStatement} y {@link java.sql.ResultSet}, permitiendo un manejo mas
     * sencillo de ambos elementos como una sola entidad. Esta clase implementa la interfaz
     * {@link java.lang.AutoCloseable}, asegurando que los recursos subyacentes (como statements y
     * result sets) puedan ser liberados correctamente mediante el uso de un bloque
     * try-with-resources.
     *
     * <h3 style="color: white;">Funcionamiento</h3>
     * Al invocar un query SQL mediante {@link DatabaseConnection#executeQuery}, esta clase es
     * creada con las siguientes propiedades:
     * <ul>
     *     <li><b>{@code _statement}</b>: representa el statement preparado asociado con la ejecucion del query SQL.</li>
     *     <li><b>{@code _resultSet}</b>: contiene los datos resultantes del query SQL proporcionado.</li>
     * </ul>
     * Este mecanismo permite abstraer y simplificar el manejo de recursos utilizados en una operacion SQL.
     *
     * <h3 style="color: white;">Propositos y Ventajas</h3>
     * <ol>
     *     <li>Garantiza que el cierre adecuado de recursos SQL se realice de forma ordenada mediante el metodo {@link #close()}.</li>
     *     <li>Permite acceso directo al {@link java.sql.ResultSet} asociado a traves del metodo {@link #get_resultSet()}.</li>
     *     <li>Facilita el manejo de excepciones gracias a la implementacion de {@link AutoCloseable}, evitando posibles fugas de recursos.</li>
     * </ol>
     *
     * <h3 style="color: white;">Parametros Internos</h3>
     * <ul>
     *     <li><b>{@code _statement}</b>: un objeto {@link java.sql.PreparedStatement} que representa la sentencia SQL ejecutada.</li>
     *     <li><b>{@code _resultSet}</b>: un objeto {@link java.sql.ResultSet} que almacena los resultados generados por la sentencia SQL.</li>
     * </ul>
     *
     * <h3 style="color: white;">Metodos</h3>
     * <ul>
     *     <li>{@link #get_resultSet()}: Proporciona acceso al {@link java.sql.ResultSet} encapsulado.</li>
     *     <li>{@link #close()}: Libera los recursos asociados con el statement y el result set.</li>
     * </ul>
     *
     * <h3 style="color: white;">Notas</h3>
     * <ul>
     *     <li>Es responsabilidad del desarrollador cerrar la instancia de esta clase tras completar el procesamiento de los datos en {@link java.sql.ResultSet}.</li>
     *     <li>Para evitar excepciones no controladas durante el cierre, asegurese de utilizarla junto con try-with-resources.</li>
     * </ul>
     *
     * </body>
     */
    public final class QueryResult implements AutoCloseable {
        /*! Parametros Internos*/
        private final PreparedStatement _statement;
        private final ResultSet _resultSet;
        
        public QueryResult(PreparedStatement exStatement, ResultSet exResultSet){
            this._resultSet = exResultSet;
            this._statement = exStatement;
        }
        

       public ResultSet get_resultSet() {
           return _resultSet;
       }

       /**
        * Closes this resource, relinquishing any underlying resources. This method is invoked
        * automatically on objects managed by the {@code try}-with-resources statement.
        *
        * @throws Exception if this resource cannot be closed
        * @apiNote While this interface method is declared to throw {@code Exception}, implementers
        * are
        * <em>strongly</em> encouraged to declare concrete implementations of the {@code close}
        * method to
        * throw more specific exceptions, or to throw no exception at all if the close operation
        * cannot fail.
        *
        * <p> Cases where the close operation may fail require careful
        * attention by implementers. It is strongly advised to relinquish the underlying resources
        * and to internally <em>mark</em> the resource as closed, prior to throwing the exception.
        * The {@code close} method is unlikely to be invoked more than once and so this ensures
        * that the resources are released in a timely manner. Furthermore it reduces problems that
        * could arise when the resource wraps, or is wrapped, by another resource.
        *
        * <p><em>Implementers of this interface are also strongly advised
        * to not have the {@code close} method throw {@link InterruptedException}.</em>
        * <p>
        * This exception interacts with a thread's interrupted status, and runtime misbehavior is
        * likely to occur if an {@code InterruptedException} is
        * {@linkplain Throwable#addSuppressed suppressed}.
        * <p>
        * More generally, if it would cause problems for an exception to be suppressed, the
        * {@code AutoCloseable.close} method should not throw it.
        *
        * <p>Note that unlike the {@link Closeable#close close}
        * method of {@link Closeable}, this {@code close} method is <em>not</em> required to be
        * idempotent.  In other words, calling this {@code close} method more than once may have
        * some visible side effect, unlike {@code Closeable.close} which is required to have no
        * effect if called more than once.
        * <p>
        * However, implementers of this interface are strongly encouraged to make their
        * {@code close} methods idempotent.
        */
       @Override
       public void close() throws SQLException {
           try{
               _resultSet.close();
           }finally {
               _statement.close();
           }
       }
   }
    
}
