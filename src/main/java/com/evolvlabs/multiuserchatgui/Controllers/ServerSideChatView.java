package com.evolvlabs.multiuserchatgui.Controllers;

import com.evolvlabs.multiuserchatgui.ClientSideBackend.ClientPOJO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.ClientDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO;
import com.evolvlabs.multiuserchatgui.ServerSideBackend.MessageServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;

import java.nio.file.Paths;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author : Santiago Arellano
 * @date : 07-Mar-2025
 * @description: El presente archivo incluye la comunicacion entre el servidor y la UI del
 * servidor, conectando todos los parametros internos para que esta funcione
 */
public class ServerSideChatView extends Application {

    /*! JavaFX Params*/
    @FXML
    private Label serverSideCurrentAddressLabel;
    @FXML
    private Label serverSideCurrentPortLabel;
    @FXML
    private Label serverSideConnectedUsersLabel;
    @FXML   
    private Label serverSideRegisteredUsersLabel;
    @FXML
    private TableColumn<MessageDTO, String> liveChatRegistrySenderAccountColumn;
    @FXML
    private TableColumn<MessageDTO, String> liveChatRegistryReceiverAccountColumn;
    @FXML
    private TableColumn<MessageDTO, Timestamp> liveChatRegistryHourSentColumn;
    @FXML
    private TableColumn<MessageDTO, String> liveChatRegistryMessageColumn;
    @FXML
    private TableColumn<MessageDTO, String> liveChatRegistryConfirmedColumn;
    @FXML
    private TableColumn<MessageDTO, String> liveChatRegistryReceiverConfirmationColumn;
    @FXML
    private TableView<MessageDTO> liveChatRegistryTableColumn;
    @FXML
    private ToggleGroup clientAsStyleAnalysisToggleGroup;
    @FXML 
    private RadioButton clientAsReceiverRadioButton;
    @FXML
    private RadioButton clientAsSenderRadioButton;
    @FXML
    private Button serverSideDeleteAllMessages;
    @FXML
    private TableView<MessageDTO> serverSidePerClientChatRegistryTableView;
    @FXML
    private TableView<ClientDTO> serverSideRegisteredClientListTableView;
    @FXML
    private MenuButton servserSideClientSelectorMenuButton;
    @FXML
    private Button serverSidePerformAnalysisButton;
    @FXML
    private RadioButton serverSideDeleteClientRadioButton;
    @FXML
    private RadioButton serverSideCreateClientRadioButton;
    @FXML
    private TextField clientUsernameTextField;
    @FXML
    private TextField clientPasskeyTextField;
    @FXML
    private Button createClientButton;
    @FXML
    private ToggleGroup ClientTableToggleGroup;




    /*! Clases Internas*/
    private MessageServer _MessageServerForApplication;
    private ExecutorService service = Executors.newSingleThreadExecutor();
    private String selectedUsername;


    /**
     * <body style="color:white;">
     * Este metodo es el encargado de inicializar los componentes visuales y funcionales de la
     * interfaz de usuario para el lado del servidor en una aplicacion de chat multiusuario.
     *
     * <p>El metodo no recibe parametros de entrada, no retorna ningun valor y no lanza
     * excepciones,
     * pero se ejecuta automáticamente al cargar la vista. Su objetivo principal es conectar las
     * diferentes partes de la interfaz con el modelo subyacente de datos.</p>
     *
     * <p>Entre las tareas principales que realiza este metodo se encuentran:</p>
     * <ul>
     *     <li>Asociar datos y funciones especificas a botones, tablas y columnas mediante
     *     listeners y configuraciones de propiedades.</li>
     *     <li>Habilitar las tablas para mostrar titulares o registros pertinentes de los mensajes
     *     y usuarios en tiempo real, incluyendo campos como contenido del mensaje, confirmacion,
     *     hora de envio, entre otros.</li>
     *     <li>Modificar el comportamiento de los campos de entrada y botones segun selecciones
     *     realizadas en radio buttons, como habilitar el proceso de crear o eliminar usuarios.</li>
     *     <li>Actualizar y hacer scroll automáticamente en la tabla de registros de mensajes al
     *     detectar nuevos elementos en la lista observable conectada.</li>
     *     <li>Crear y definir estilos personalizados para ciertos elementos visuales como
     *     {@link TableColumn}, proporcionando una experiencia mas estilizada y clara.</li>
     *     <li>Implementar funciones para sincronizar y refrescar la interfaz al detectar cambios
     *     en datos subyacentes.</li>
     * </ul>
     *
     * <p>El metodo utiliza tecnologias y conceptos clave como {@link TableColumn},
     * {@link SimpleStringProperty}, {@link SimpleObjectProperty}, {@link ListChangeListener},
     * y estilos CSS en un entorno JavaFX.</p>
     *
     * </body>
     */
    public void initialize() {

        this.clientAsReceiverRadioButton.setUserData("receivedMessages");
        this.clientAsSenderRadioButton.setUserData("sentMessages");

        setupClientAnalysisTableView();
        setupRegisteredClientsTableView();
        ClientTableToggleGroup.selectedToggleProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue == serverSideCreateClientRadioButton) {
                        clientUsernameTextField.setDisable(false);
                        clientPasskeyTextField.setDisable(false);
                        createClientButton.setText("Create Client");
                        clientUsernameTextField.clear();
                        clientPasskeyTextField.clear();
                        serverSideRegisteredClientListTableView
                                .getSelectionModel().clearSelection();
                    } else if (newValue == serverSideDeleteClientRadioButton) {
                        createClientButton.setText("Delete Client");
                        clientUsernameTextField.setDisable(true);
                        clientPasskeyTextField.setDisable(true);
                    }
                });
        liveChatRegistrySenderAccountColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue()._senderUUID())
                                                               );
        liveChatRegistryReceiverAccountColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue()._receiverUUID())
                                                                 );
        liveChatRegistryHourSentColumn.setCellValueFactory(
                cellData -> new SimpleObjectProperty<>(cellData.getValue()._messageTimestamp())
                                                          );
        liveChatRegistryHourSentColumn.setCellFactory(column -> new TableCell<MessageDTO, Timestamp>() {
            private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            @Override
            protected void updateItem(Timestamp item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : format.format(item));
            }
        });
        liveChatRegistryMessageColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(cellData.getValue()._messageContent())
                                                         );
        liveChatRegistryConfirmedColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        Boolean.toString(cellData.getValue()._senderConfirmation())
                )
                                                           );
        liveChatRegistryReceiverConfirmationColumn.setCellValueFactory(
                cellData -> new SimpleStringProperty(
                        Boolean.toString(cellData.getValue()._receiverConfirmation())
                ));
        liveChatRegistryTableColumn.getItems().addListener((ListChangeListener<MessageDTO>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    System.out.println("Added " + c.getAddedSize() + " items");
                    c.getAddedSubList().forEach(msg ->
                                                        System.out.println("Message: " + msg._messageContent())
                                               );
                }
            }
        });

        liveChatRegistryTableColumn.getItems().addListener((ListChangeListener<MessageDTO>) c -> {
            while (c.next()) {
                if (c.wasAdded()) {
                    int lastIndex = liveChatRegistryTableColumn.getItems().size() - 1;
                    if (lastIndex >= 0) {
                        liveChatRegistryTableColumn.scrollTo(lastIndex);
                    }
                }
            }
        });
        this.liveChatRegistrySenderAccountColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        this.liveChatRegistryReceiverAccountColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        this.liveChatRegistryHourSentColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        this.liveChatRegistryMessageColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        this.liveChatRegistryConfirmedColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        this.liveChatRegistryReceiverConfirmationColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");


        /*Conectamos modelo de seleccion del cliente para poder eliminarlo*/
        this.serverSideRegisteredClientListTableView
                .getSelectionModel()
                .setSelectionMode(SelectionMode.SINGLE);
        // Add listener to selection changes
        serverSideRegisteredClientListTableView.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (serverSideDeleteClientRadioButton.isSelected()) {
                        if (newValue != null) {
                            // Update fields only in delete mode
                            clientUsernameTextField.setText(newValue._clientUsername());
                            clientPasskeyTextField.setText(newValue._clientPwdHash());
                        } else {
                            clientUsernameTextField.clear();
                            clientPasskeyTextField.clear();
                        }
                    }
                });

    }

    /**
     * <body style="color:white;">
     * Este metodo se utiliza para inicializar, despues del arranque, los elementos de menu
     * desplegable usados para seleccionar clientes en la interfaz del servidor.
     *
     * <p>Este metodo realiza lo siguiente:</p>
     * <ul>
     *     <li>Llena el menu desplegable con los nombres de usuario de todos los clientes registrados
     *     en el {@link MessageServer}.</li>
     *     <li>Agrega un listener para detectar cambios en la lista de nombres de usuario de clientes.</li>
     *     <li>Cuando ocurre un cambio, actualiza los elementos del menu desplegable de manera
     *     automatica para reflejar el nuevo estado de la lista de usuarios.</li>
     * </ul>
     *
     * @param messageServer La instancia de {@link MessageServer} que mantiene la lista observable
     *                      de clientes y provee los datos necesarios para actualizar el menu
     *                      desplegable.
     * @throws NullPointerException Si el parametro {@code messageServer} es {@code null}.
     *                              </body>
     */
    public void initMenuItemsAfterStartup(MessageServer messageServer) {
        updateMenuItems(messageServer.getEx_AllUsernamesProperty());
        messageServer.getEx_AllUsernamesProperty().addListener(
                (ListChangeListener<ClientDTO>) c -> {
            Platform.runLater(() -> updateMenuItems(
                    messageServer.getEx_AllUsernamesProperty()));
        });
    }

    /**
     * <body style="color:white;">
     * Este metodo actualiza los elementos del menu desplegable que permite seleccionar a los
     * clientes en la interfaz de usuario.
     *
     * <p>Se asegura de que cada cliente registrado, contenido en la lista {@link ObservableList}
     * proporcionada como argumento, este representado con un elemento dentro del menu.</p>
     *
     * <p>El metodo realiza las siguientes operaciones:</p>
     * <ul>
     *     <li>Limpia los elementos existentes del {@link MenuButton}.</li>
     *     <li>Itera sobre cada cliente dentro de la lista.</li>
     *     <li>Crea una nueva instancia de {@link MenuItem} para cada cliente, utilizando el nombre
     *     de usuario del cliente como texto de visualizacion.</li>
     *     <li>Asocia una accion al elemento del menu para que, al seleccionarse, se actualice
     *     el {@code selectedUsername} y el texto del boton desplegable con el nombre del cliente
     *     correspondiente.</li>
     *     <li>Agrega cada {@link MenuItem} al {@link MenuButton} que se utiliza para mostrar
     *     los clientes.</li>
     * </ul>
     * @param clients una lista observable, instancia de {@link ObservableList}, que contiene
     *                objetos de la clase {@link ClientDTO}, cada uno representando un cliente
     *                registrado.
     * @throws NullPointerException si {@code clients} es {@code null}.
     *                              </body>
     */
    private void updateMenuItems(ObservableList<ClientDTO> clients) {
        servserSideClientSelectorMenuButton.getItems().clear();
        for (ClientDTO client : clients) {
            MenuItem item = new MenuItem(client._clientUsername());
            item.setOnAction(event -> {
                selectedUsername = client._clientUsername();
                servserSideClientSelectorMenuButton.setText(selectedUsername);
            });
            servserSideClientSelectorMenuButton.getItems().add(item);
        }
    }


    /**
     * <body style="color:white;">
     * Este metodo configura la tabla de analisis de mensajes por cliente en la interfaz de usuario.
     * La tabla esta disenada para reflejar informacion clave asociada con los mensajes enviados y
     * recibidos por un cliente especifico.
     *
     * <p>El metodo realiza las siguientes tareas:</p>
     * <ul>
     *     <li>Limpia cualquier columna existente en la tabla antes de configurarla.</li>
     *     <li>Define columnas, cada una representando una propiedad distinta de {@link MessageDTO}, incluyendo:
     *         <ul>
     *             <li>UUID del remitente del mensaje.</li>
     *             <li>UUID del receptor del mensaje.</li>
     *             <li>Hora de envio del mensaje.</li>
     *             <li>Contenido del mensaje.</li>
     *             <li>Confirmacion del envio por parte del remitente.</li>
     *             <li>Confirmacion de recepcion por parte del receptor.</li>
     *         </ul>
     *     </li>
     *     <li>Establece estilos personalizados para las columnas utilizando la configuracion CSS.</li>
     *     <li>Establece una politica de redimension automatica para las columnas para que ajusten su tamano dinamicamente.</li>
     *     <li>Configura un marcador de posicion (placeholder) que aparece si la tabla esta vacia.</li>
     * </ul>
     *
     * <p>El metodo utiliza conceptos clave de JavaFX como {@link TableColumn}, {@link SimpleStringProperty},
     * {@link SimpleBooleanProperty}, y personaliza celdas con {@link TableCell} para definir formatos especificos,
     * como convertir valores booleanos en texto y formatear fechas.</p>
     *
     * <p>Este metodo no recibe parametros, no devuelve valores y no lanza excepciones.</p>
     * </body>
     */
    private void setupClientAnalysisTableView() {
        serverSidePerClientChatRegistryTableView.getColumns().clear();
        TableColumn<MessageDTO, String> senderColumn = new TableColumn<>("Sender");
        senderColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()._senderUUID()));
        TableColumn<MessageDTO, String> receiverColumn = new TableColumn<>("Receiver");
        receiverColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()._receiverUUID()));
        TableColumn<MessageDTO, String> timestampColumn = new TableColumn<>("Hour Sent");
        timestampColumn.setCellValueFactory(data ->
                                                    new SimpleStringProperty(data.getValue()._messageTimestamp().toString()));
        TableColumn<MessageDTO, String> contentColumn = new TableColumn<>("Message");
        contentColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()._messageContent()));
        TableColumn<MessageDTO, Boolean> senderConfirmColumn = new TableColumn<>("Sender " +
                                                                                         "Confirmation");
        senderConfirmColumn.setCellValueFactory(data ->
                                                        new SimpleBooleanProperty(data.getValue()._senderConfirmation()));
        senderConfirmColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(Boolean.toString(item));
                }
            }
        });
        TableColumn<MessageDTO, Boolean> receiverConfirmColumn = new TableColumn<>("Receiver " +
                                                                                           "Confirmation");
        receiverConfirmColumn.setCellValueFactory(data ->
                                                          new SimpleBooleanProperty(data.getValue()._receiverConfirmation()));
        receiverConfirmColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(Boolean.toString(item));
                }
            }
        });
        senderColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        receiverColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        timestampColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        contentColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        senderConfirmColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        receiverConfirmColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        serverSidePerClientChatRegistryTableView.getColumns().addAll(
                senderColumn,
                receiverColumn,
                timestampColumn,
                contentColumn,
                senderConfirmColumn,
                receiverConfirmColumn);
        serverSidePerClientChatRegistryTableView
                .setColumnResizePolicy(
                        TableView
                                .CONSTRAINED_RESIZE_POLICY);
        serverSidePerClientChatRegistryTableView
                .setPlaceholder(
                        new Label("No Info"));
    }


    /**
     * <body style="color:white;">
     * Este metodo configura la tabla utilizada para mostrar a los clientes registrados en la
     * interfaz de usuario del lado del servidor. La tabla incluye varias columnas que reflejan
     * propiedades clave de cada cliente, como el UUID, el nombre de usuario, el hash de la
     * contrasena y el hash de la sal.
     *
     * <p>
     * Este metodo realiza los siguientes pasos:
     * <ul>
     *     <li>Limpia cualquier columna existente en la tabla.</li>
     *     <li>Define nuevas columnas con titulos y estilos personalizados.</li>
     *     <li>Vincula cada columna a una propiedad especifica de los objetos {@link ClientDTO},
     *     que representa los clientes registrados.</li>
     *     <li>Agrega las columnas configuradas a la tabla.</li>
     *     <li>Establece una politica de redimension automatica de columnas.</li>
     *     <li>Configura un marcador de posicion para mostrar en caso de que la tabla este vacia.</li>
     * </ul>
     *
     * <p>
     * Este metodo no recibe parametros ni retorna valores. Tampoco lanza excepciones.
     * </p>
     * <p>
     * </body>
     */
    public void setupRegisteredClientsTableView() {
        serverSideRegisteredClientListTableView.getColumns().clear();
        TableColumn<ClientDTO, String> uuidColumn =
                new TableColumn<>("Client UUID");
        uuidColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(cellData.getValue()._clientUUID()));
        TableColumn<ClientDTO, String> usernameColumn =
                new TableColumn<>("Username");
        usernameColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(cellData.getValue()._clientUsername()));
        TableColumn<ClientDTO, String> passwordHashColumn =
                new TableColumn<>("Password Hash");
        passwordHashColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(cellData.getValue()._clientPwdHash()));
        TableColumn<ClientDTO, String> saltHashColumn =
                new TableColumn<>("Salt Hash");
        saltHashColumn.setCellValueFactory(
                cellData ->
                        new SimpleStringProperty(cellData.getValue()._clientSaltHash()));

        uuidColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        usernameColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        passwordHashColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");
        saltHashColumn.setStyle("-fx-font-family: 'Microsoft JhengHei'; -fx-font-size: 12;");

        serverSideRegisteredClientListTableView.getColumns().addAll(
                uuidColumn,
                usernameColumn,
                passwordHashColumn,
                saltHashColumn);
        serverSideRegisteredClientListTableView
                .setColumnResizePolicy(
                        TableView.CONSTRAINED_RESIZE_POLICY);
        serverSideRegisteredClientListTableView
                .setPlaceholder(new Label("No info"));
    }


    /**
     * The application initialization method. This method is called immediately after the
     * Application class is loaded and constructed. An application may override this method to
     * perform initialization prior to the actual starting of the application.
     *
     * <p>
     * The implementation of this method provided by the Application class does nothing.
     * </p>
     *
     * <p>
     * NOTE: This method is not called on the JavaFX Application Thread. An application must not
     * construct a Scene or a Stage in this method. An application may construct other JavaFX
     * objects in this method.
     * </p>
     *
     * @throws Exception if something goes wrong
     */
    @Override
    public void init() throws Exception {
        super.init();
    }

    /**
     * The main entry point for all JavaFX applications. The start method is called after the init
     * method has returned, and after the system is ready for the application to begin running.
     *
     * <p>
     * NOTE: This method is called on the JavaFX Application Thread.
     * </p>
     *
     * @param primaryStage the primary stage for this application, onto which the application scene
     *                     can be set. Applications may create other stages, if needed, but they
     *                     will not be primary stages.
     * @throws Exception if something goes wrong
     */
    @Override
    public void start(Stage primaryStage) throws Exception {

        Scene sceneLoadedFromFXML = FXMLLoader.load(Paths.get(System.getProperty("user.dir")
                                                             ,"src"
                                                             ,"main"
                                                             ,"resources",
                                                              "com",
                                                              "evolvlabs",
                                                              "multiuserchatgui",
                                                              "ServerSideChatView.fxml").toUri().toURL());
        primaryStage.setScene(sceneLoadedFromFXML);
        primaryStage.show();

        this._MessageServerForApplication = new MessageServer();
        this.service.submit(_MessageServerForApplication::attemptToAcceptClientConnectionRequests);
        this.serverSideCurrentAddressLabel = (Label) sceneLoadedFromFXML.lookup(
                "#serverSideCurrentAddressLabel");
        this.serverSideCurrentPortLabel = (Label) sceneLoadedFromFXML.lookup(
                "#serverSideCurrentPortLabel");
        this.serverSideConnectedUsersLabel = (Label) sceneLoadedFromFXML.lookup(
                "#serverSideConnectedUsersLabel");
        this.serverSideRegisteredUsersLabel = (Label) sceneLoadedFromFXML.lookup(
                "#serverSideRegisteredUsersLabel");
        this.liveChatRegistryTableColumn = (TableView<MessageDTO>) sceneLoadedFromFXML.lookup(
                "#liveChatRegistryTableColumn");
        /*! Conectamos al informacion de los observables a la informacion del servidor y la GUI*/
        this.serverSideCurrentAddressLabel.textProperty().bind(this._MessageServerForApplication.getEx_ConnectionAddressProperty());
        this.serverSideCurrentPortLabel.textProperty().bind(this._MessageServerForApplication.getEx_ConnectionPortProperty());
        this.serverSideConnectedUsersLabel.textProperty().bind(this._MessageServerForApplication.getEx_ConnectedUsersProperty());
        this.serverSideRegisteredUsersLabel.textProperty().bind(this._MessageServerForApplication.getEx_RegisteredUsersProperty());
        /*Precargamos los datos en la view*/
        liveChatRegistryTableColumn.setItems(_MessageServerForApplication.getEx_AllSentMessagesProperty());
        this.servserSideClientSelectorMenuButton = (MenuButton) sceneLoadedFromFXML.lookup(
                "#servserSideClientSelectorMenuButton");
        initMenuItemsAfterStartup(this._MessageServerForApplication);
        this.serverSidePerformAnalysisButton = (Button) sceneLoadedFromFXML.lookup(
                "#serverSidePerformAnalysisButton");


        String resultFilter = "";

        this.clientAsReceiverRadioButton = (RadioButton) sceneLoadedFromFXML.lookup(
                "#clientAsReceiverRadioButton");
        this.clientAsSenderRadioButton = (RadioButton) sceneLoadedFromFXML.lookup(
                "#clientAsSenderRadioButton");
        this.serverSidePerformAnalysisButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (selectedUsername == null) {
                    Platform.runLater(() ->{
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Advertencia");
                        alert.setHeaderText(null);
                        alert.setContentText("Debe seleccionar un usuario para realizar " +
                                                     "un analisis de sus mensajes.");
                        alert.showAndWait();
                    });
                }

                if (clientAsReceiverRadioButton.isSelected()){
                    _MessageServerForApplication
                            .filterClientListBasedOnUsernameAndType(
                                    selectedUsername,
                                    (String) clientAsReceiverRadioButton.getUserData()
                                    );
                }else if (clientAsSenderRadioButton.isSelected()){
                    _MessageServerForApplication
                            .filterClientListBasedOnUsernameAndType(
                                    selectedUsername,
                                    (String) clientAsSenderRadioButton.getUserData()
                                    );
                }
            }
        });
        this.serverSidePerClientChatRegistryTableView = (TableView<MessageDTO>) sceneLoadedFromFXML.lookup(
                "#serverSidePerClientChatRegistryTableView");
        this.serverSideRegisteredClientListTableView = (TableView<ClientDTO>) sceneLoadedFromFXML.lookup(
                "#serverSideRegisteredClientListTableView");
        this.serverSidePerClientChatRegistryTableView.setItems(_MessageServerForApplication.getEx_FileteredMessagesProperty());
        this.serverSideRegisteredClientListTableView.setItems(this._MessageServerForApplication.getEx_AllUsernamesProperty());
        this._MessageServerForApplication.getEx_FileteredMessagesProperty()
                .addListener((ListChangeListener<MessageDTO>) change -> {
                    while (change.next()) {
                        if (change.wasAdded() || change.wasRemoved() || change.wasReplaced()) {
                            Platform.runLater(() -> {
                                serverSidePerClientChatRegistryTableView.refresh();
                                serverSideRegisteredClientListTableView.refresh();
                            });
                        }
                    }
                });
        this.serverSideDeleteAllMessages = (Button) sceneLoadedFromFXML.lookup(
                "#serverSideDeleteAllMessages");
        this.serverSideDeleteAllMessages.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {

                if (selectedUsername == null){
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Advertencia | No hay Seleccion de Usuario");
                        alert.setHeaderText("Se ha detectado que no hay un usuario seleccionado.." +
                                                    ".");
                        alert.setContentText("Debe seleccionar un usuario para eliminar sus " +
                                                     "mensajes.\n Esto lo puede hacer dando " +
                                                     "click en el menu despegable superior.");
                        alert.showAndWait();
                    });
                    return;
                }

                //? Mostramos una warning al usuario, ya que esto elimina todos los mensajes del
                //? usasurio
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Conformacion Requerida ");
                    alert.setHeaderText(null);
                    alert.setContentText("Esta seguro de que desea eliminar todos los mensajes " +
                                         "de este usuario?");
                    ButtonType confirmationBttn = new ButtonType("Confirmar ELiminacion");
                    ButtonType cancelarBttn = new ButtonType("Cancelar Operacion");

                    alert.getButtonTypes().clear();
                    alert.getButtonTypes().addAll(confirmationBttn, cancelarBttn);

                    alert.showAndWait().ifPresent(new Consumer<ButtonType>() {
                        @Override
                        public void accept(ButtonType buttonType) {
                            if (buttonType == confirmationBttn){
                                CompletableFuture<Boolean> futureResult =
                                        _MessageServerForApplication.deleteAllUserMessagesFromDatabase(
                                                selectedUsername);
                                futureResult.thenAccept(new Consumer<Boolean>() {
                                    @Override
                                    public void accept(Boolean aBoolean) {
                                        Platform.runLater(() -> {
                                            if (aBoolean){
                                                Alert successAlert =
                                                        new Alert(Alert.AlertType.INFORMATION);
                                                successAlert
                                                        .setTitle("Eliminacion De Mensajes Correcta");
                                                successAlert
                                                        .setHeaderText(null);
                                                successAlert
                                                        .setContentText("Los mensajes del usuario han sido eliminados exitosamente!");
                                                successAlert.showAndWait();
                                            } else {
                                                Alert errorAlert =
                                                        new Alert(Alert.AlertType.ERROR);
                                                errorAlert
                                                        .setTitle("Error | Error Durante Eliminacion");
                                                errorAlert
                                                        .setHeaderText("Error en el proceso de eliminación");
                                                errorAlert
                                                        .setContentText("No se pudieron eliminar los mensajes del usuario. Por favor intente nuevamente.");
                                                errorAlert
                                                        .showAndWait();

                                            }
                                        });
                                    }
                                });
                            }
                        }
                    });
                });
            }
        });


        /*Segunda seccion de creacion y eliminacion de clientes*/
        this.createClientButton = (Button) sceneLoadedFromFXML.lookup(
                "#createClientButton");
        this.serverSideCreateClientRadioButton = (RadioButton) sceneLoadedFromFXML.lookup(
                "#serverSideCreateClientRadioButton");
        this.serverSideDeleteClientRadioButton = (RadioButton) sceneLoadedFromFXML.lookup(
                "#serverSideDeleteClientRadioButton");
        this.clientUsernameTextField = (TextField) sceneLoadedFromFXML.lookup(
                "#clientUsernameTextField");
        this.clientPasskeyTextField = (TextField) sceneLoadedFromFXML.lookup(
                "#clientPasskeyTextField");


        this.createClientButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (serverSideCreateClientRadioButton.isSelected()) {
                    //! Leemos los parametros internos
                    String readUsername = clientUsernameTextField.getText();
                    if (!readUsername.isBlank()){
                        if (readUsername.length() > 30) {
                            Platform.runLater(() -> {
                                Alert alert = new Alert(Alert.AlertType.ERROR);
                                alert.setTitle("Error | Error Durante Creacion de Nuevo Usuario");
                                alert.setHeaderText("Se ha detectado un campo incorrecto...");
                                alert.setContentText("El nombre de usuario debe contener menos " +
                                                             "de\n " +
                                                             "30 caracteres para ser " +
                                                             "almacenado en la base de datos.");
                                alert.showAndWait();
                            });
                            return; // Cancel operation
                        } else {
                            String readPassword = clientPasskeyTextField.getText();
                            if (!readPassword.isBlank()) {//Perform the operation!
                                ClientPOJO clientDTO = new ClientPOJO(readUsername,
                                                                      readPassword);
                                CompletableFuture<Boolean> isClientCreationSuccessful =
                                        _MessageServerForApplication.registerNewUserInDatabase(clientDTO);
                                isClientCreationSuccessful.thenAccept(success -> {
                                    if (success) {
                                        Platform.runLater(() -> {
                                            Alert alert = new Alert(Alert.AlertType.INFORMATION);
                                            alert.setTitle("Creacion De Usuario Correcta");
                                            alert.setHeaderText(null);
                                            alert.setContentText("Cliente creado exitosamente: una vez " +
                                                                         "cerrada esta penstana, podra " +
                                                                         "visualizar todos sus cambios en" +
                                                                         " las tablas, menus y registros " +
                                                                         "de la base de datos y esta " +
                                                                         "aplicacion!");
                                            alert.showAndWait();
                                        });
                                    } else {
                                        Platform.runLater(() -> {
                                            Alert alert = new Alert(Alert.AlertType.ERROR);
                                            alert.setTitle("Error | Error Durante Creacion de " +
                                                                   "Nuevo Usuario");
                                            alert.setHeaderText("Se ha producido un error en la " +
                                                                        "base de datos...");
                                            alert.setContentText("Error al crear el cliente: En este " +
                                                                         "caso, suele suceder por dos " +
                                                                         "razones." +
                                                                         "\n" +
                                                                         "1. El cliente ya esta " +
                                                                         "registrado, y usted esta " +
                                                                         "intenta registrar dos veces a " +
                                                                         "alguie con el mismo nombre. Eso" +
                                                                         " causa un problema ya que los " +
                                                                         "campos dependenden de este " +
                                                                         "titulo." +
                                                                         "\n2. Hubo un error en la base " +
                                                                         "de datos, si todo el resto de " +
                                                                         "queries funciona, entonces es " +
                                                                         "seguro que es la opcion 1 el " +
                                                                         "problema!");
                                            alert.showAndWait();
                                        });
                                    }
                                });
                            }
                        }
                    } else {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setTitle("Error | Error Durante Creacion de Nuevo Usuario");
                            alert.setHeaderText("Se ha detectado un campo incorrecto...");
                            alert.setContentText("El nombre de usuario no puede estar en blanco.");
                            alert.showAndWait();
                        });
                    }
                }
                else if (serverSideDeleteClientRadioButton.isSelected()) {
                    ClientDTO selectedClient = serverSideRegisteredClientListTableView.getSelectionModel().getSelectedItem();

                    if (selectedClient != null) {
                        // Create custom confirmation alert
                        Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
                        confirmationAlert.setTitle("Confirmacion Requerida | Confirmar " +
                                                           "Eliminacion");
                        confirmationAlert.setHeaderText("¿Esta seguro que desea eliminar este " +
                                                                "cliente?");
                        confirmationAlert.setContentText(
                                "Username: " + selectedClient._clientUsername() + "\n" +
                                        "UUID: " + selectedClient._clientUUID()
                                                        );

                        ButtonType confirmarButton = new ButtonType("Confirmar Mi Seleccion");
                        ButtonType cancelarButton = new ButtonType("Cancelar Proceso");

                        confirmationAlert.getButtonTypes().setAll(confirmarButton, cancelarButton);

                        // Show dialog and wait for response
                        confirmationAlert.showAndWait().ifPresent(buttonType -> {
                            if (buttonType == confirmarButton) {
                                CompletableFuture<Boolean> isDeletionSuccessful =
                                        _MessageServerForApplication.deleteUserFromDatabase(
                                                selectedClient._clientUsername());

                                isDeletionSuccessful.thenAccept(success -> {
                                    Platform.runLater(() -> {
                                        if (success) {
                                            Alert successAlert = new Alert(Alert.AlertType.INFORMATION);
                                            successAlert.setTitle("Eliminacion De Usuario " +
                                                                          "Correcta");
                                            successAlert.setHeaderText("Se ha eliminado al " +
                                                                               "usuario " +
                                                                               "seleccionado " +
                                                                               "correctamente");
                                            successAlert.setContentText("Cliente eliminado " +
                                                                                "exitosamente:\"n" +
                                                                                " " +
                                                                                "Los campos de " +
                                                                                "todos los " +
                                                                                "registros\n se " +
                                                                                "actualizaran " +
                                                                                "cuando esta " +
                                                                                "pestana se " +
                                                                                "cierre!");
                                            successAlert.showAndWait();
                                            clientUsernameTextField.clear();
                                            clientPasskeyTextField.clear();
                                            serverSideRegisteredClientListTableView.getSelectionModel().clearSelection();
                                        } else {
                                            Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                                            errorAlert.setTitle("Error | Error Durante " +
                                                                         "Eliminacion de " +
                                                                         "Usuario");
                                            errorAlert.setHeaderText("Se ha producido un error en " +
                                                                      "la base de datos...");
                                            errorAlert.setContentText("Error al eliminar el " +
                                                                              "cliente:" +
                                                                              "\n" +
                                                                              "Puede que el " +
                                                                              "cliente no " +
                                                                              "existia en " +
                                                                              "la base de " +
                                                                              "datos\n, o que " +
                                                                              "hubo un " +
                                                                              "error en la " +
                                                                              "base de " +
                                                                              "datos.\n En " +
                                                                              "cualquiera de " +
                                                                              "estos casos, si " +
                                                                              "toda consulta " +
                                                                              "anterior " +
                                                                              "funcionaba,\n lo " +
                                                                              "mas" +
                                                                              " seguro es que el " +
                                                                              "cliente no exista!");
                                            errorAlert.showAndWait();
                                        }
                                    });
                                });
                            } else {
                                serverSideRegisteredClientListTableView.getSelectionModel().clearSelection();
                                clientUsernameTextField.clear();
                                clientPasskeyTextField.clear();
                            }
                        });
                    } else {
                        Platform.runLater(() -> {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Error | Error Durante Eliminacion de Usuario");
                            alert.setHeaderText("No se ha seleccionado ningun cliente...");
                            alert.setContentText("Por favor, seleccione un cliente para eliminar");
                            alert.showAndWait();
                        });
                    }
                }
            }
        });


        primaryStage.setOnCloseRequest(event -> {
            if (service != null) {
                try {
                    _MessageServerForApplication.shutdownTheServer();
                    service.shutdown();
                    if (!service.awaitTermination(5, TimeUnit.MILLISECONDS)) {
                        service.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    service.shutdownNow();
                }
            }
            Platform.exit();
            System.exit(0);
        });

    }
}
