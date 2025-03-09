package com.evolvlabs.multiuserchatgui.Controllers;

import com.evolvlabs.multiuserchatgui.ClientSideBackend.ClientPOJO;
import com.evolvlabs.multiuserchatgui.ClientSideBackend.MessageClient;
import com.evolvlabs.multiuserchatgui.ClientSideBackend.MessagePOJO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.ClientDTO;
import com.evolvlabs.multiuserchatgui.CommunicationBackend.MessageDTO;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.Duration;

import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * @author : Santiago Arellano
 * @date : 07-Mar-2025
 * @description: El presente archivo incluye el cumulo de la aplicacion en el lado del cliente,
 * la idea es que esta clase presente en su totalidad el manejo de la interfaz grafica, como la
 * principal tarea de la aplicacion, no es el manejo de la GUI, el controlador se deja dentro de
 * esta clase.
 */
public class ClientSideChatView extends Application {

    /*! Parametros internos de FXML, estos los usamos simplemente para nombrarlos y en algunos
    listeners, si no es porque los tenemos aqui, procederia a usarlos con root.lookup la mayoria del
     el tiempo.*/
    @FXML
    ListView<ClientDTO> clientSideAvailableRecipientsListView;
    @FXML
    Button clientSideRefreshListButton;
    @FXML
    TitledPane clientSideAvailableRecipientsTitledPane;
    @FXML
    TabPane clientSideCurrentChatTabPane;
    @FXML
    GridPane clientSideCurrentChatGridPane;
    @FXML
    TextArea clientSideCurrentChatIntTextField;
    @FXML
    Button clientSideCurrentChatSendMessageButton;
    @FXML
    Button clientSideRefreshCurrentChatButton;

    /*Parametros Internos, estos son los mas importantes para el manejo de la UI*/
    private MessageClient messageClientForThisUIInstance;
    private ExecutorService clientServiceForThisUIInstance =
            Executors.newFixedThreadPool(1);
    private String uuid;


    /**
     * Metodo principal para inicializar la interfaz grafica del cliente y manejar todos los
     * componentes visuales y logicos necesarios para el correcto funcionamiento de la aplicacion.
     * <p>
     * Este metodo utiliza el archivo FXML proporcionado para cargar la interfaz grafica, implementa
     * el manejo de eventos en la GUI usando JavaFX, y autentica al usuario mediante ventanas
     * emergentes y logica de autenticacion del lado cliente. Tambien enlaza elementos visuales con
     * las funciones de negocio relevantes, como la actualizacion de listas de usuarios y mensajes.
     *
     * @param primaryStage La ventana principal de la aplicacion donde se cargara la interfaz
     *                     grafica.
     * @throws Exception En caso de errores durante la carga del archivo FXML o durante la
     *                   inicializacion de la interfaz grafica y logica.
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        /*Cargamos la informacion del archivo FXML*/
        Scene sceneLoadedFromFXML = FXMLLoader.load(
                Paths.get(System.getProperty("user.dir")
                        , "src"
                        , "main"
                        , "resources",
                          "com",
                          "evolvlabs",
                          "multiuserchatgui",
                          "ClientSideChatView.fxml").toUri().toURL());
        primaryStage.setScene(sceneLoadedFromFXML);

        /*
         * Este bloque intenta conectarse al cliente, lo hace en un loop que no termina si el
         * servidor esta encendido y la respuesta es incorrecta, termina immediatamente si el
         * retorno es SERVER_DISCONNECT, lo que indica que el servidor no esta conectado y
         * debemos apagar la aplicacion.
         */
        Optional<String> loginSuccessful; ClientPOJO credentials;
        do {
            this.messageClientForThisUIInstance = new MessageClient();
            LogInPopUp loginDialog = new LogInPopUp();

            Optional<ClientPOJO> result = loginDialog.showAndWait();

            if (result.isPresent()) {
                credentials = result.get();

                loginSuccessful =
                        this.messageClientForThisUIInstance
                                .attemptClientSideAuthenticationRequest(
                                credentials.get_clientUsername(),
                                credentials.get_clientClearPassword());
                if (!loginSuccessful.isPresent()) {
                    Alert badCredentials = new Alert(Alert.AlertType.ERROR);
                    badCredentials.setTitle("Inicio de Sesion Fallido");
                    badCredentials.setHeaderText("Inicio de Sesion Fallido | Usuario O Contrasena" +
                                                         " Incorrectos");
                    badCredentials.setContentText("El usuario o la constrasena fueron " +
                                                          "incorrectos\n, y el sistema no valido " +
                                                          "el" +
                                                          " ingreso. Favor asegurarse de que las " +
                                                          "\n credenciales ingresadas sean " +
                                                          "correctas.");
                    badCredentials.showAndWait();
                } else if (loginSuccessful.get().equals("SERVER_DISCONNECT")) {
                    Alert serverDisconnect = new Alert(Alert.AlertType.ERROR);
                    serverDisconnect.setTitle("Inicio de Sesion Fallido");
                    serverDisconnect.setHeaderText("Inicio de Sesion Fallido | Servidor " +
                                                           "Desconectado");
                    serverDisconnect.setContentText("No se ha podido iniciar la conexion al " +
                                                            "servidor\n. Esto no significa que su" +
                                                            " programa esta danado, sino que el " +
                                                            "servidor esta apagado\n. Favor " +
                                                            "comunicarse con TI.");
                    serverDisconnect.showAndWait();
                    try {
                        clientServiceForThisUIInstance.shutdown();
                        if (!clientServiceForThisUIInstance.awaitTermination(5, 
                                                           TimeUnit.MILLISECONDS)) {
                            clientServiceForThisUIInstance.shutdownNow();
                        }
                        } catch (InterruptedException e) {
                            clientServiceForThisUIInstance.shutdownNow();
                        }
                    Platform.exit();
                    System.exit(0);
                    break;
                } else{
                    this.uuid = loginSuccessful.get();
                }
            } else {
                Platform.exit();
                break;
            }
        } while (!loginSuccessful.isPresent());

        primaryStage.setTitle("Multiuser Client Chat GUI - Currently Logged In For : " + 
                                      this
                                      .messageClientForThisUIInstance
                                      .getMessageClient_ClientUsername());

        primaryStage.show();

        /*Init buton for updating client list*/
        this.clientSideRefreshListButton = (Button) sceneLoadedFromFXML.lookup(
                "#clientSideRefreshListButton");
        this.clientSideRefreshListButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                if (messageClientForThisUIInstance.getMessageClient_IsConnected())
                CompletableFuture.runAsync(() -> {
                    messageClientForThisUIInstance.postClientUpdateListRequest();
                });
            }
        });

        this.clientSideRefreshCurrentChatButton = (Button) sceneLoadedFromFXML.lookup(
                "#clientSideRefreshCurrentChatButton");
        this.clientSideRefreshCurrentChatButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                CompletableFuture.runAsync(() -> {
                    messageClientForThisUIInstance.postMessageUpdateListRequest();
                });
            }
        });

        this.clientSideAvailableRecipientsListView =
                (ListView<ClientDTO>) sceneLoadedFromFXML.lookup(
                "#clientSideAvailableRecipientsListView");
        this.clientSideAvailableRecipientsListView
                .getSelectionModel()
                .setSelectionMode(SelectionMode.MULTIPLE);
        this.clientSideAvailableRecipientsListView.setCellFactory(
                new Callback<ListView<ClientDTO>, ListCell<ClientDTO>>() {
                    @Override
                    public ListCell<ClientDTO> call(ListView<ClientDTO> param) {
                        return new ClientListElement();
                    }
                });
        Platform.runLater(() -> {
            // Set up the ListView
            this.clientSideAvailableRecipientsListView.setItems(
                    this.messageClientForThisUIInstance.getMessageClient_ListadoDeClientes());

            // Request client list update in a separate thread
            CompletableFuture.runAsync(() -> {
                this.messageClientForThisUIInstance.postClientUpdateListRequest();
                this.messageClientForThisUIInstance.postMessageUpdateListRequest();
            }).exceptionally(throwable -> {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Connection Error");
                    alert.setHeaderText("Failed to update client list");
                    alert.setContentText("Unable to retrieve the latest client list. Please try again.");
                    alert.showAndWait();
                });
                return null;
            });
        });

        /*Conectamos los botones, lista y texto para hacer funcionar la mensajeria*/
        this.clientSideCurrentChatIntTextField = (TextArea) sceneLoadedFromFXML.lookup(
                "#clientSideCurrentChatIntTextField");
        this.clientSideCurrentChatSendMessageButton = (Button) sceneLoadedFromFXML.lookup(
                "#clientSideCurrentChatSendMessageButton");

        this.clientSideCurrentChatIntTextField.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(KeyEvent event) {
                if (event.getCode().equals(KeyCode.ENTER)){
                    validarEnvioDeInformacion();
                }
            }
        });

        this.clientSideCurrentChatSendMessageButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent event) {
                validarEnvioDeInformacion();
            }
        });

        /*Conectamos un metodo de trabajo interno para trabajar con dos listados de observables
        dentro del sistema*/

        this.clientSideCurrentChatGridPane = (GridPane) sceneLoadedFromFXML.lookup(
                "#clientSideCurrentChatGridPane");



        this.clientSideCurrentChatGridPane.getColumnConstraints().clear();
        this.clientSideCurrentChatGridPane.getColumnConstraints().addAll(
                createColumn(50),
                createColumn(50) );

        messageClientForThisUIInstance.getMessageClient_ListadoDeMensajesEnviados().addListener(
                (ListChangeListener<MessageDTO>) change -> {
                    while (change.next()) {
                        if (change.wasAdded() || change.wasRemoved()) {
                            loadAllMessages();
                        }
                    }
                });

        messageClientForThisUIInstance.getMessageClient_ListadoDeMensajesRecibidos().addListener(
                (ListChangeListener<MessageDTO>) change -> {
                    while (change.next()) {
                        if (change.wasAdded() || change.wasRemoved()) {
                            loadAllMessages();
                        }
                    }
                });

        /*Binding para finalizar el executor service y la conectividad interna*/
        primaryStage.setOnCloseRequest(event -> {
            if (this.clientServiceForThisUIInstance != null
                    && this.messageClientForThisUIInstance != null) {
                try {
                    if (messageClientForThisUIInstance.getMessageClient_IsConnected()){
                        messageClientForThisUIInstance.postDisconnectionRequestFromClientInterface();
                        clientServiceForThisUIInstance.shutdown();
                        if (!clientServiceForThisUIInstance.awaitTermination(5, TimeUnit.MILLISECONDS)) {
                            clientServiceForThisUIInstance.shutdownNow();
                        }
                    } else {
                        System.out.println("[ClientSideComms] El servidor ha cerrado la conexion " +
                                                   "desde su lado, el cliente puede haber sido " +
                                                   "eliminado");
                        clientServiceForThisUIInstance.shutdown();
                        if (!clientServiceForThisUIInstance.awaitTermination(5, TimeUnit.MILLISECONDS)) {
                            clientServiceForThisUIInstance.shutdownNow();
                        }
                    }
                } catch (InterruptedException e) {
                    clientServiceForThisUIInstance.shutdownNow();
                }
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private void loadAllMessages() {
        Platform.runLater(() -> {
            clientSideCurrentChatGridPane.getChildren().clear();
            clientSideCurrentChatGridPane.getRowConstraints().clear();

            List<MessageDTO> allMessages = new ArrayList<>();
            allMessages.addAll(messageClientForThisUIInstance.getMessageClient_ListadoDeMensajesEnviados());
            allMessages.addAll(messageClientForThisUIInstance.getMessageClient_ListadoDeMensajesRecibidos());

            allMessages.sort(Comparator.comparing(MessageDTO::_messageTimestamp));

            for (MessageDTO message : allMessages) {
                boolean isSent = messageClientForThisUIInstance.getMessageClient_ListadoDeMensajesEnviados().contains(message);
                addMessageToGrid(message, isSent);
            }
        });
    }


    /**
     * Crea un objeto de tipo {@link ColumnConstraints} con un ancho especificado porcentualmente.
     * Este metodo establece el ancho en porcentaje para controlar el tamano de las columnas dentro
     * de un {@link GridPane}. Es una tecnica comun para ajustar el diseño de los componentes en un
     * diseño de cuadrícula.
     *
     * @param percentWidth El ancho de la columna expresado como un porcentaje (ejemplo: 50
     *                     representa 50%). Debe estar en el rango de 0 a 100.
     * @return Un objeto de tipo {@link ColumnConstraints} configurado con el ancho especificado.
     * @throws IllegalArgumentException Si el argumento {@code percentWidth} es menor a 0 o mayor a
     *                                  100.
     * @implNote Este metodo es esencial para adaptaciones responsivas en la interfaz grafica,
     * permitiendo un tamaño relativo de columnas dentro de un GridPane.
     */
    private ColumnConstraints createColumn(double percentWidth) {
        if (percentWidth < 0 || percentWidth > 100) {
            throw new IllegalArgumentException(
                    "El porcentaje de ancho debe estar entre 0 y 100. Valor recibido: " + percentWidth);
        }
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(percentWidth);
        return column;
    }

    /**
     * <body style="color: white;">
     * Metodo encargado de agregar un mensaje al componente {@link GridPane} que representa el chat
     * actual. Se utiliza un diseno de tipo {@link VBox} que adapta la informacion del mensaje,
     * incluyendo el autor (remitente o destinatario), contenido y hora de envio.
     * <p>
     * El metodo organiza visualmente los mensajes dependiendo de quien los envio, diferenciandolos
     * a traves del estilo CSS aplicado al fondo y alineacion.
     *
     * @param message Objeto de tipo {@link MessageDTO} que contiene la informacion del mensaje,
     *                como el contenido, remitente, destinatario y timestamp.
     * @param isSent  Booleano que indica si el mensaje fue enviado por el usuario actual (true) o
     *                recibido de otro usuario (false). Esto afecta el estilo y la ubicacion en el
     *                {@link GridPane}.
     * @throws NullPointerException Si el {@code message} es {@code null}.
     * @implNote Este metodo crea nuevos nodos para cada mensaje y los añade como una nueva fila al
     * {@link GridPane}. Ademas, se asegura de que el scroll del chat se posicione automaticamente
     * al mensaje mas reciente.
     * </body>
     */
    private void addMessageToGrid(MessageDTO message, boolean isSent) {
        VBox messageBox = new VBox(5);
        messageBox.setPadding(new Insets(10));
        messageBox.setMaxWidth(300);

        String style = String.format(
                "-fx-background-color: %s; -fx-background-radius: 10; -fx-padding: 10;",
                isSent ? "#DCF8C6" : "#E8E8E8"
                                    );
        messageBox.setStyle(style);

        String senderName = message._senderUUID();
        String receiverName = message._receiverUUID();
        for (ClientDTO client : messageClientForThisUIInstance.getMessageClient_ListadoDeClientes()) {
            if (client._clientUUID().equals(message._senderUUID())) {
                senderName = client.getClientUsername();
                break;
            }
        }
        for (ClientDTO client : messageClientForThisUIInstance.getMessageClient_ListadoDeClientes()) {
            if (client._clientUUID().equals(message._receiverUUID())) {
                receiverName = client.getClientUsername();
                break;
            }
        }

        Label senderLabel = new Label(senderName);
        senderLabel.setFont(Font.font("Microsoft JhengHei UI", FontWeight.BOLD, 12));
        senderLabel.setWrapText(true);
        Label receiverLabel = new Label(receiverName);
        receiverLabel.setFont(Font.font("Microsoft JhengHei UI", FontWeight.BOLD, 12));
        receiverLabel.setWrapText(true);
        Label messageText = new Label(message._messageContent());
        messageText.setWrapText(true);

        Label timestamp = new Label(message._messageTimestamp().toLocalDateTime().format(
                DateTimeFormatter.ofPattern("HH:mm")));
        timestamp.setStyle("-fx-font-size: 10; -fx-text-fill: #666666;");

        messageBox.getChildren().addAll(senderLabel, receiverLabel, messageText, timestamp);
        messageBox.setAlignment(isSent ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        int newRow = clientSideCurrentChatGridPane.getRowCount();
        clientSideCurrentChatGridPane.add(messageBox, isSent ? 1 : 0, newRow);
        GridPane.setMargin(messageBox, new Insets(5, 5, 5, 5));

        if (clientSideCurrentChatGridPane.getParent() instanceof ScrollPane) {
            ScrollPane scrollPane = (ScrollPane) clientSideCurrentChatGridPane.getParent();
            Platform.runLater(() -> scrollPane.setVvalue(scrollPane.getVmax()));
        }
    }


    /**
     * <body style="color: white;">
     * Metodo encargado de validar y procesar el envio de mensajes en el sistema de chat del
     * cliente. Este metodo realiza las siguientes funciones:
     * <ul>
     *     <li>Verifica que se haya seleccionado al menos un destinatario antes de proceder con el envio.</li>
     *     <li>Valida que el contenido del mensaje no sea vacio antes de enviarlo.</li>
     *     <li>Crea instancias del mensaje a enviar utilizando {@link MessagePOJO}.</li>
     *     <li>Envía el mensaje a cada destinatario utilizando el cliente de comunicacion.</li>
     *     <li>Anima el boton de envio y actualiza el listado de mensajes con un retraso minimo
     *         para confirmar que el servidor ha procesado el mensaje.</li>
     * </ul>
     *
     * @throws NullPointerException Si algun componente interno requerido no esta inicializado.
     * @implNote Este metodo utiliza logica asincrona mediante {@link CompletableFuture} para
     * reducir el bloqueo en el UI thread mientras interactua con el backend.
     * @implNote Usa animaciones simples con {@link Timeline} para retroalimentacion rapida al
     * usuario.
     * </body>
     */
    public void validarEnvioDeInformacion() {
        if (clientSideAvailableRecipientsListView.getSelectionModel().isEmpty()) {
            alertFactoryMethod(
                    "Error | No Hay Destinatario Seleccionado",
                    "Se ha detectado un destinatario vacio...",
                    "Por favor seleccione un destinatario, del listado a mano derecha, \n" +
                            "antes de enviar el mensaje.",
                    Alert.AlertType.WARNING
                              );
            return;
        }

        String messageContent = clientSideCurrentChatIntTextField.getText().trim();
        if (messageContent.isEmpty()) {
            alertFactoryMethod(
                    "Error | No Hay Menssaje Escrito",
                    "Se ha detectado un mensaje vacio...",
                    "Por favor escriba un mensaje, en el campo de texto, antes de enviarlo.",
                    Alert.AlertType.WARNING
                              );
            return;
        }

        ObservableList<ClientDTO> selectedRecipients =
                clientSideAvailableRecipientsListView
                        .getSelectionModel()
                        .getSelectedItems();

        boolean success = false;
        for (ClientDTO message : selectedRecipients) {// Create the message
            MessagePOJO messagePOJO = new MessagePOJO(
                    this.uuid,
                    message._clientUUID(),
                    messageContent,
                    Timestamp.from(Instant.now()),
                    true, false
            );

            // Send the message
            boolean internalSuccess =
                    this.messageClientForThisUIInstance.postMessageFromClientInterface(messagePOJO);

            if (internalSuccess) {
                // Animate the button to provide feedback
                success = true;
                animacionDeBotonDeEnvioDeMensaje();
            } else {
                alertFactoryMethod(
                        "Error | Fallo al Enviar Mensaje",
                        "No se pudo enviar el mensaje",
                        "Hubo un problema al enviar el mensaje. Por favor intente nuevamente.",
                        Alert.AlertType.ERROR
                                  );
            }
        }

        if (success) {
            clientSideCurrentChatIntTextField.clear();
            // Request message list update with a slight delay to allow server processing
            CompletableFuture.runAsync(() -> {
                try {
                    // Wait a bit to ensure server has processed the message
                    Thread.sleep(500);
                    this.messageClientForThisUIInstance.postMessageUpdateListRequest();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
    }


    /**
     * <body style="color: white;">
     * Metodo encargado de fabricar y mostrar alertas en la interfaz grafica del cliente. Este
     * metodo no devuelve ningun valor, pero realiza lo siguiente:
     *
     * <ul>
     *     <li>Crea una nueva instancia de {@link Alert} basada en el {@code alertType} proporcionado.</li>
     *     <li>Configura el titulo, encabezado, y contenido del mensaje usando los parametros indicados.</li>
     *     <li>Muestra el alerta al usuario, deteniendo la ejecucion hasta que este cierre el mensaje.</li>
     * </ul>
     *
     * <p>La utilidad principal del metodo es estandarizar la creacion de alertas para ser usadas
     * en casos de error, advertencia o notificaciones generales dentro del sistema.</p>
     *
     * @param title     String que representa el titulo de la ventana de alerta. No debe ser
     *                  {@code null}.
     * @param header    String que define el encabezado de la alerta. Puede ser {@code null} si no
     *                  se requiere encabezado.
     * @param content   String que contiene el mensaje principal dentro del cuerpo de la alerta. No
     *                  debe ser {@code null} o vacio.
     * @param alertType Enumeracion {@link Alert.AlertType} que define el tipo de alerta (ejemplo:
     *                  ERROR, WARNING, INFORMATION). No debe ser {@code null}.
     * @throws NullPointerException Si alguno de los argumentos {@code title}, {@code content}, o
     *                              {@code alertType} es {@code null}.
     * @implNote Este metodo conecta directamente con las capacidades de JavaFX para manejar
     * ventanas modales, garantizando simplicidad y centralizacion en la gestion de alertas dentro
     * de la aplicacion.
     * </body>
     */
    private void alertFactoryMethod(String title, String header, String content, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    /**
     * <body style="color: white;">
     * Metodo responsable de animar el boton de envio con un cambio temporal de color en caso de
     * exito en el envio de un mensaje.
     * <p>
     * Este metodo aplica un estilo CSS animado al boton para mostrar visualmente al usuario que el
     * mensaje fue enviado, utilizando un {@link Timeline} y multiples keyframes que alternan entre
     * colores dentro de un lapso de tiempo especificado.
     *
     * <ul>
     *    <li>La animacion consta de un cambio del color del fondo del boton hacia verde (#28a745) indicando exito.</li>
     *    <li>El estilo CSS original del boton se restaura al terminar la animacion, asegurando consistencia visual.</li>
     * </ul>
     *
     * @throws NullPointerException Si {@code clientSideCurrentChatSendMessageButton} no esta
     *                              inicializado antes de la ejecucion del metodo.
     * @implNote La duracion de la animacion esta definida para que ocurra en intervalos de 500
     * milisegundos, totalizando 1500 milisegundos para completar.
     * </body>
     */
    private void animacionDeBotonDeEnvioDeMensaje() {
        String originalStyle = clientSideCurrentChatSendMessageButton.getStyle();

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(
                        clientSideCurrentChatSendMessageButton.styleProperty(),
                        "-fx-background-color: #28a745;" 
                )),
                new KeyFrame(Duration.millis(500), new KeyValue(
                        clientSideCurrentChatSendMessageButton.styleProperty(),
                        originalStyle
                )),
                new KeyFrame(Duration.millis(1000), new KeyValue(
                        clientSideCurrentChatSendMessageButton.styleProperty(),
                        "-fx-background-color: #28a745;"
                )),
                new KeyFrame(Duration.millis(1500), new KeyValue(
                        clientSideCurrentChatSendMessageButton.styleProperty(),
                        originalStyle
                ))
        );

        timeline.play();
    }


    /**
     * <body style="color: white;">
     * La clase {@code LogInPopUp} extiende {@link Dialog} y representa un cuadro de dialogo modal
     * para autenticar las credenciales del usuario. Este cuadro de dialogo permite capturar un
     * nombre de usuario (username) y una contrasena (password), validando algunas condiciones antes
     * de cerrar con exito.
     *
     * <p>La clase se basa completamente en componentes de JavaFX, incluyendo {@link TextField},
     * {@link PasswordField}, {@link ButtonType}, y {@link GridPane}. Tambien utiliza
     * {@link javafx.beans.value.ChangeListener} para responder a cambios en tiempo real en los
     * campos introducidos por el usuario.</p>
     *
     * <ul>
     *     <li>Admite entradas limitadas para nombre de usuario (maximo 30 caracteres).</li>
     *     <li>Habilita el boton de inicio de sesion en base a la validez del campo de contrasena.</li>
     * </ul>
     *
     * <p><b>Comportamiento General:</b>
     * Cuando la accion de "Login" es confirmada por el usuario, se genera una instancia de
     * {@link ClientPOJO}, la cual contiene las credenciales introducidas.
     * Si no se ha cumplido alguna validacion, el cuadro de dialogo muestra los errores
     * correspondientes.</p>
     *
     * <p><b>Notas:</b>
     * </ul>
     * <li>El layout utiliza {@link GridPane} para organizar los elementos visuales.</li>
     * <li>Soporta cierre del cuadro de dialogo de manera explicita (boton Cancel).</li>
     * </body>
     */
    public static final class LogInPopUp extends Dialog<ClientPOJO> {
        private TextField usernameField;
        private PasswordField passwordField;

        /**
         * <body style="color: white;">
         * Constructor de la clase {@code LogInPopUp}. Este metodo establece las configuraciones
         * generales del cuadro de dialogo, incluyendo el titulo, el texto del encabezado, y la
         * inicializacion de su interfaz de usuario mediante el metodo {@code initUI()}.
         * <ul>
         *     <li>Establece el titulo del cuadro como "MultiUserChatGUI - Log In Dialog".</li>
         *     <li>Configura las advertencias iniciales para informar al usuario sobre el requerimiento de credenciales.</li>
         * </ul>
         * </body>
         */
        public LogInPopUp() {
            super();
            setTitle("Dialogo de Inicio de Sesion | Multi Chat User GUI");
            setHeaderText("Para ingresar a la aplicacion, se requiere el ingreso de " +
                                  "credenciales\n" +
                                  "de autenticacion");
            initUI();

        }

        /**
         * <body style="color: white;">
         * Metodo de inicializacion de la interfaz de usuario (UI) del cuadro de dialogo. Define
         * tanto los componentes visuales como el comportamiento de los listeners para el campo de
         * nombre de usuario y contrasena.
         *
         * <p><b>Comportamiento:</b>
         * <ul>
         *     <li>Los campos de texto ({@link TextField} y {@link PasswordField}) se organizan
         *     dentro de un {@link GridPane} estructurado con 2 columnas y multiples filas.</li>
         *     <li>El boton de login es inicialmente deshabilitado hasta que se introduce una
         *     contrasena valida.</li>
         *     <li>El listener del campo de nombre de usuario limita la longitud del texto a 30
         *     caracteres e informa al usuario en caso de exceder esta longitud mediante un
         *     {@link Alert}.</li>
         *     <li>El listener del campo de contrasena habilita/deshabilita el boton de login
         *     dependiendo del contenido del campo.</li>
         *     <li>Se utiliza un {@link Callback} en {@link Dialog#setResultConverter} para generar
         *     y devolver un objeto {@link ClientPOJO} con las credenciales ingresadas, o
         *     {@code null} si el cuadro de dialogo se cierra sin accion.</li>
         * </ul>
         *
         * @throws NullPointerException Si alguno de los nodos necesarios no puede inicializarse.
         *                              </body>
         */
        private void initUI() {
            // Set the button types
            ButtonType loginButtonType = new ButtonType("Iniciar Sesion",
                                                        ButtonBar.ButtonData.OK_DONE);
            ButtonType cancelButtonType = new ButtonType("Cancelar Inicio de Sesion",
                                                         ButtonBar.ButtonData.CANCEL_CLOSE);
            getDialogPane().getButtonTypes().addAll(loginButtonType, cancelButtonType);

            // Create the username and password labels and fields
            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            usernameField = new TextField();
            usernameField.setPromptText("Nombre de Usuario");
            passwordField = new PasswordField();
            passwordField.setPromptText("Contrasena");

            grid.add(new Label("Nombre de Usuario:"), 0, 0);
            grid.add(usernameField, 1, 0);
            grid.add(new Label("Contrasena:"), 0, 1);
            grid.add(passwordField, 1, 1);

            getDialogPane().setContent(grid);

            Node loginButton = getDialogPane().lookupButton(loginButtonType);
            loginButton.setDisable(true);

            //? Luego de Haber creado la UI, tenemos que definir el comportamiento de la UI.
            //? Para esto conectamos varios listeners
            
            /*
             ? Para el Listener del username, este no puede ser mayor que 30 
             */

            this.usernameField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observableValue,
                                    String s,
                                    String t1) {
                    if (t1.length() > 30) {
                        final String validValue = "";
                        Platform.runLater(() -> {
                        usernameField.setText(validValue);
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Credencial de Inicio de Sesion Invalida");
                        alert.setHeaderText("Longitud de nombre de usuario incorrecta");
                        alert.setContentText("La longitud del nombre de usuario no puede exeder 30" +
                                                     " caracteres");
                        alert.showAndWait();
                        });

                    }
                }
            });
    
            /*
             ? para el campo del password, atacamos con un listener para saber si el sistema
             ?tiene mas de un caracter
            */
            this.passwordField.textProperty().addListener(new ChangeListener<String>() {
                @Override
                public void changed(ObservableValue<? extends String> observableValue,
                                    String s,
                                    String t1) {
                    if (t1.isEmpty()) {
                        loginButton.setDisable(true);
                    } else {
                        loginButton.setDisable(false);
                    }
                }
            });

            /*Este es un override a un metodo interno de la clase Dialog<> que nos permite 
            transformar un resultado luego de prcionar un boton*/
            setResultConverter(new Callback<ButtonType, ClientPOJO>() {
                @Override
                public ClientPOJO call(ButtonType buttonType) {
                    if (buttonType == loginButtonType) {
                        return new ClientPOJO(usernameField.getText(), passwordField.getText());
                    }
                    return null;
                }
            });
        }
    }


    /**
     * <body style="color: white;">
     * La clase {@code ClientListElement} extiende {@link ListCell} para personalizar la manera en
     * que los elementos de tipo {@link ClientDTO} se muestran dentro de una lista en JavaFX.
     * <p>
     * Utiliza un {@link FlowPane} en configuracion horizontal para organizar el contenido del
     * elemento, incluyendo un {@link Label} que muestra el nombre de usuario (username) asociado al
     * cliente.
     *
     * <p><b>Caracteristicas Clave:</b></p>
     * <ul>
     *     <li>Las instancias de esta clase son utilizadas por un {@link ListView} para manejar
     *     la representacion de los clientes disponibles dentro de la interfaz grafica del
     *     sistema de chat.</li>
     *     <li>Los estilos visuales son ajustados directamente utilizando la clase {@link Font}
     *     para definir el peso y el tamano de la fuente del nombre de usuario.</li>
     * </ul>
     *
     * <p><b>Funcionamiento:</b></p>
     * <ul>
     *     <li>El constructor inicializa y configura los componentes internos del elemento,
     *     incluyendo su alineacion y estilos visuales base.</li>
     *     <li>El metodo {@code updateItem} se encarga de actualizar dinamicamente el contenido
     *     y el estilo del elemento basado en los datos del cliente proporcionados, o limpiar
     *     el elemento si este es vacio.</li>
     * </ul>
     *
     * <p>La clase garantiza un diseño modular y reutilizable al encapsular la logica de
     * representacion de los clientes en una estructura JavaFX completamente personalizada.</p>
     * </body>
     */
    public static final class ClientListElement extends ListCell<ClientDTO> {
        private final FlowPane _FlowPaneHolder;
        private final Label labelForUsername;

        /**
         * <body style="color: white;">
         * Constructor de la clase {@code ClientListElement}. Este metodo inicializa y configura los
         * componentes graficos utilizados para mostrar informacion de un cliente en una vista de
         * lista, utilizando JavaFX.
         *
         * <p><b>Detalle del Funcionamiento:</b></p>
         * <ul>
         *     <li>Crea un contenedor de tipo {@link FlowPane} con orientacion horizontal donde se alojaran los componentes graficos.</li>
         *     <li>Establece padding y alinea visualmente los elementos en el centro a la izquierda.</li>
         *     <li>Añade un {@link Label} para mostrar informacion del nombre de usuario asociado al cliente, utilizando una fuente configurada estilisticamente ({@link Font}).</li>
         *     <li>El {@link FlowPane} se utiliza para organizar los nodos graficos de manera estructurada, permitiendo una personalizacion sencilla.</li>
         * </ul>
         *
         * <p><b>Uso Principal:</b></p>
         * Este constructor forma parte de la personalizacion visual de celdas en una
         * {@link ListView<ClientDTO>}, siendo su objetivo mostrar informacion especifica de cada
         * cliente de manera estilizada y responsiva.
         *
         * <p>La estructura modular permite que el contenedor {@link FlowPane} se extienda para ajustarse
         * a elementos adicionales si es necesario, y ofrece un diseño limpio y flexible al usuario final.</p>
         *
         * @implNote Este constructor es invocado por el sistema JavaFX durante la inicializacion
         * del {@link ListView}. Garantiza que cada celda tiene un layout base independiente, listo
         * para ser actualizado con contenido relevante mediante el metodo {@code updateItem()}.
         * </body>
         */
        public ClientListElement() {
            this._FlowPaneHolder = new FlowPane(Orientation.HORIZONTAL);
            this._FlowPaneHolder.setPadding(new Insets(5));
            this._FlowPaneHolder.setAlignment(Pos.CENTER_LEFT);

            labelForUsername = new Label();
            labelForUsername.setFont(Font.font("Microsoft JhengHei UI", FontWeight.BOLD, 14));

            this._FlowPaneHolder.getChildren().add(labelForUsername);
        }

        /**
         * <body style="color: white;">
         * Metodo sobreescrito que actualiza el contenido grafico de un elemento dentro de la
         * {@link ListView}.
         * <p>
         * Este metodo es llamado automaticamente por el sistema JavaFX cuando un elemento es
         * renderizado o reciclado. Si {@code item} no es nulo y no esta vacio, se actualizan sus
         * datos utilizando el nombre de usuario del cliente; de lo contrario, el componente grafico
         * se limpia.
         * </p>
         *
         * <p><b>Detalles del Funcionamiento:</b></p>
         * <ul>
         *     <li>Si {@code item} es valido (no es nulo ni vacio), se actualiza el {@link Label} interno
         *     con el nombre de usuario del cliente recibido.</li>
         *     <li>El grafico se establece para que sea el contenedor padron {@link FlowPane} definido.</li>
         *     <li>En caso contrario, se limpia el componente grafico llamando a {@code setGraphic(null)}.</li>
         * </ul>
         *
         * @param item  El objeto {@link ClientDTO} que contiene los datos del cliente que se deben
         *              mostrar. Si es nulo o vacio, el contenido grafico se limpiara.
         * @param empty Booleano que indica si el elemento es vacio. {@code true} lo marca como no
         *              asignado a ninguna informacion.
         * @throws NullPointerException Puede lanzar una excepcion si los valores internos esperados
         *                              como atributos del cliente son nulos inesperadamente y no se
         *                              validan debidamente previo a este metodo.
         * @implNote Este metodo aprovecha el reciclaje de celdas implementado en las
         * {@link ListView} de JavaFX, maximizando la eficiencia visual y de memoria.
         * </body>
         */
        @Override
        protected void updateItem(ClientDTO item, boolean empty) {
            super.updateItem(item, empty);

            if (!empty && item != null) {
                labelForUsername.setText(item.getClientUsername());
                setGraphic(this._FlowPaneHolder);
            } else {
                setGraphic(null);
            }
        }
    }
}

