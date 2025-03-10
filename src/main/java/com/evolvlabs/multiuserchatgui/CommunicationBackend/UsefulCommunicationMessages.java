package com.evolvlabs.multiuserchatgui.CommunicationBackend;

/**
 * @author : Santiago Arellano
 * @date : 08-Mar-2025
 * @description: El presente archivo contiene los identificadores usados edentro del programa en
 * la mayoria de los casos para manejar la comunicacion entre los clientes y el servidor,
 * teniendo en cuenta que sirven para organizar los mensajes en un formato similar al de HTTP.
 */
public enum UsefulCommunicationMessages {

    /*! Listado de Communication Messages de Cliente a Servidor*/
    /*? Proceso de Envio de mensajes y recepcion de mensajes*/
    POST_MESSAGE_BROADCAST_REQUEST("POST_MESSAGE_BROADCAST_REQUEST"),
    POST_MESSAGE_RECEIVED_ACKNOWLEDGMENT("POST_MESSAGE_RECEIVED_ACKNOWLEDGMENT"),
    /*? Proceso de Recepcion y Comunicacion de UI Updates*/
    POST_CLIENT_LIST_UPDATE_REQUEST("POST_CLIENT_LIST_UPDATE_REQUEST"),
    POST_MANDATORY_CLIENT_LIST_UPDATE_ACKNOWLEDGMENT(
            "POST_MANDATORY_CLIENT_LIST_UPDATE_ACKNOWLEDGMENT"),
    POST_MANDATORY_MESSAGE_LIST_UPDATE_ACKNOWLEDGMENT(
            "POST_MANDATORY_MESSAGE_LIST_UPDATE_ACKNOWLEDGMENT"),
    /*? Proceso de Conexion y Desconexion del Servidor principal*/
    POST_CLIENT_CONNECTION_REQUEST("POST_CLIENT_CONNECTION_REQUEST"),
    POST_CLIENT_AUTHENTICATION_REQUEST("POST_CLIENT_AUTHENTICATION_REQUEST"),
    POST_CLIENT_DISCONNECTION_REQUEST("POST_CLIENT_DISCONNECTION_REQUEST"),
    /*! Listado de Communicaiton Messages de Servidor a Cliente*/
    /*? Proceso de recepcion y comunicacion de mensajes*/
    POST_MESSAGE_BROADCAST_ACKNOWLEDGEMENT("POST_MESSAGE_BROADCAST_ACKNOWLEDGEMENT"),
    GET_MESSAGE_RECEIVED_ACKNOWLEDGMENT("GET_MESSAGE_RECEIVED_ACKNOWLEDGMENT"),
    POST_RECEIVED_MESSAGE_TO_CLIENT("POST_RECEIVED_MESSAGE_TO_CLIENT"),
    /*? Proceso de Recepcion y Comunicacion de UI Updates*/
    POST_CLIENT_LIST_UPDATE_REQUEST_ACKNOWLEDGEMENT(
            "POST_CLIENT_LIST_UPDATE_REQUEST_ACKNOWLEDGEMENT"),
    POST_MANDATORY_CLIENT_LIST_UPDATE_REQUEST("POST_MANDATORY_CLIENT_LIST_UPDATE_REQUEST"),
    POST_MANDATORY_MESSAGE_LIST_UPDATE_REQUEST("POST_MANDATORY_MESSAGE_LIST_UPDATE_REQUEST"),
    /*? Proceso de Conexion y Desconexion del Servidor principal*/
    POST_CLIENT_CONNECTION_REQUEST_ACKNOWLEDGEMENT(
            "POST_CLIENT_CONNECTION_REQUEST_ACKNOWLEDGEMENT"),
    POST_CLIENT_AUTHENTICATION_REQUEST_ACKNOWLEDGEMENT(
            "POST_CLIENT_AUTHENTICATION_REQUEST_ACKNOWLEDGEMENT"),
    POST_CLIENT_DISCONNECTION_REQUEST_ACKNOWLEDGEMENT(
            "POST_CLIENT_DISCONNECTION_REQUEST_ACKNOWLEDGEMENT"),
    POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE("POST_CLIENT_CLIENT_LIST_UPDATE_MANDATE"),
    POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE("POST_CLIENT_MESSAGE_LIST_UPDATE_MANDATE"),
    POST_CLIENT_SHUTDOWN_MANDATE("POST_CLIENT_SHUTDOWN_MANDATE");


    private final String _message;
    private UsefulCommunicationMessages(String message){
        this._message = message;
    }

    public String get_message() {
        return _message;
    }
}
