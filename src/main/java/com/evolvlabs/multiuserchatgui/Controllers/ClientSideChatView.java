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
import javafx.beans.Observable;
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
import javafx.scene.text.Text;
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

    public void initialize(){
            /*Configuramos la ListView para que esta pueda soportar correctamente el display de
            los campos de usuarios*/


    }


    @Override
    public void init() throws Exception {
        super.init();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        /*Cargamos la informacion del archivo FXML*/
        Scene sceneLoadedFromFXML = FXMLLoader.load(
                Paths.get(System.getProperty("user.dir")
                        ,"src"
                        ,"main"
                        ,"resources",
                        "com",
                        "evolvlabs",
                        "multiuserchatgui",
                        "ClientSideChatView.fxml").toUri().toURL());
        primaryStage.setScene(sceneLoadedFromFXML);


        Optional<String> loginSuccessful; ClientPOJO credentials;
        do {
            this.messageClientForThisUIInstance = new MessageClient();
            LogInPopUp loginDialog = new LogInPopUp();

            // Show the dialog UI and wait for the inputs:
            Optional<ClientPOJO> result = loginDialog.showAndWait();

            if (result.isPresent()) {
                credentials = result.get();

                // Validate credentials with the server or authentication logic here
                loginSuccessful =
                        this.messageClientForThisUIInstance
                                .attemptClientSideAuthenticationRequest(
                                credentials.get_clientUsername(),
                                credentials.get_clientClearPassword());
                if (!loginSuccessful.isPresent()) {
                    Alert badCredentials = new Alert(Alert.AlertType.ERROR);
                    badCredentials.setHeaderText("Login Failed");
                    badCredentials.setContentText("Invalid username or password. Please try again.");
                    badCredentials.showAndWait();
                } else{
                    this.uuid = loginSuccessful.get();
                }
            } else {
                Platform.exit();
                break;
            }
        } while (!loginSuccessful.isPresent());

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


    private ColumnConstraints createColumn(double percentWidth) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(percentWidth);
        return column;
    }

    private void addMessageToGrid(MessageDTO message, boolean isSent) {
        VBox messageBox = new VBox(5);
        messageBox.setPadding(new Insets(10));
        messageBox.setMaxWidth(300);

        String style = String.format(
                "-fx-background-color: %s; -fx-background-radius: 10; -fx-padding: 10;",
                isSent ? "#DCF8C6" : "#E8E8E8"
                                    );
        messageBox.setStyle(style);

        // Improve the sender label to show username instead of UUID
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
            scrollPane.setVvalue(1.0);
        }
    }



    public void validarEnvioDeInformacion() {
        if (clientSideAvailableRecipientsListView.getSelectionModel().isEmpty()) {
            showAlert(
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
            showAlert(
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
        for(ClientDTO message : selectedRecipients) {// Create the message
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
                animateButtonSuccess();
            } else {
                showAlert(
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

    private void showAlert(String title, String header, String content, Alert.AlertType alertType) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void animateButtonSuccess() {
        String originalStyle = clientSideCurrentChatSendMessageButton.getStyle();

        Timeline timeline = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(
                        clientSideCurrentChatSendMessageButton.styleProperty(),
                        "-fx-background-color: #28a745;" // Green color
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


    public static final class LogInPopUp extends Dialog<ClientPOJO> {
        private TextField usernameField;
        private PasswordField passwordField;

        public LogInPopUp(){
            super();
            setTitle("MultiUserChatGUI - Log In Dialog");
            setHeaderText("To enter the application, you are required to enter your credentials.." +
                                  ".");
            initUI();

        }
        private final void initUI() {
            // Set the button types
            ButtonType loginButtonType = new ButtonType("Login", ButtonBar.ButtonData.OK_DONE);
            getDialogPane().getButtonTypes().addAll(loginButtonType, ButtonType.CANCEL);

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
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Warning");
                        alert.setHeaderText("Invalid Username Length");
                        alert.setContentText("The username cannot exceed 30 characters.");
                        alert.showAndWait();
                        usernameField.clear();
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

            setResultConverter(new Callback<ButtonType, ClientPOJO>() {
                @Override
                public ClientPOJO call(ButtonType buttonType) {
                    if (buttonType == loginButtonType){
                        return new ClientPOJO(usernameField.getText(), passwordField.getText());
                    }
                    return null;
                }
            });
        }
    }





    public static final class ClientListElement extends ListCell<ClientDTO> {
        private final FlowPane _FlowPaneHolder;
        private final Label labelForUsername;

        public ClientListElement() {
            this._FlowPaneHolder = new FlowPane(Orientation.HORIZONTAL);
            this._FlowPaneHolder.setPadding(new Insets(5));
            this._FlowPaneHolder.setAlignment(Pos.CENTER_LEFT);

            labelForUsername = new Label();
            labelForUsername.setFont(Font.font("Microsoft JhengHei UI", FontWeight.BOLD, 14));

            this._FlowPaneHolder.getChildren().add(labelForUsername);
        }

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

