package com.evolvlabs.multiuserchatgui.ServerSideBackend;

import com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine.EncryptionEngine;
import com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine.UUIDFormulator;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-2025
 * @description: El presente archivo incluye la clase de autenticacion del usuario que permite
 * manejar la creacion y la validacion de contrasenas con formato de hash para su registro en la
 * base de datos, basados en las clases del
 * {@link com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine.EncryptionEngine} y
 * {@link com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine.UUIDFormulator}
 */
public class AuthenticatorEngine {

    /*! Parametros Internos*/
    /**
     *  Parametro interno que permite encriptar las contrasenas, y manejar el encriptado y la
     *  evaluacion de la igualdad entre dos contrasenas enviadas por el sistema
      */
    private static final EncryptionEngine encryptionEngine = new EncryptionEngine();
    /**
     * Parametro que permite formular un UUID unico para una persona dependiendo del proceso de
     * registro
     */
    private static final UUIDFormulator uuidFormulator = new UUIDFormulator();

    /**
     * <body style="color: white;">
     * Este metodo toma como entrada una contrasena en texto claro proporcionada por el usuario, la
     * cual sera encriptada utilizando el motor de encriptacion {@link EncryptionEngine}.
     * <p>
     * El proceso convierte la contrasena en un formato seguro, aplicando un algoritmo de hash,
     * junto con un salt generado internamente por el motor de encriptacion. Devuelve un objeto
     * {@link EncryptionEngine.HashedPasswordDTO} que contiene tanto el hash resultante como el salt
     * generado.
     *
     * @param exUserClearPassword La contrasena en formato de texto claro proporcionada por el
     *                            usuario. No puede ser <code>null</code> o vacia.
     * @return Un objeto {@link EncryptionEngine.HashedPasswordDTO} que encapsula el hash de la
     * contrasena y el salt utilizados en el proceso.
     * @throws IllegalArgumentException Si la contrasena proporcionada es <code>null</code> o
     *                                  vacia.
     *                                  </body>
     */
    public static final EncryptionEngine.HashedPasswordDTO encryptProvidedClearPassword(String exUserClearPassword)
            throws IllegalArgumentException {
        return encryptionEngine.processNewUserPassword(exUserClearPassword);
    }

    /**
     * <body style="color: white;">
     * Este metodo permite validar una contrasena proporcionada por el usuario frente a un hash de
     * contrasena preexistente almacenado en la base de datos. Utiliza el motor de encriptacion
     * {@link EncryptionEngine} para llevar a cabo la comparacion de manera segura.
     * <p>
     * El proceso verifica si el hash derivado de la contrasena inicial proporcionada coincide con
     * la contrasena y el salt previamente almacenados, garantizando que la contrasena sea valida.
     * El metodo emplea tecnicas de criptografia moderna para evitar ataques como la comparacion en
     * tiempo constante.
     *
     * @param exUserClearPassword  La contrasena en texto claro proporcionada por el usuario. No
     *                             puede ser <code>null</code> o vacia.
     * @param dbUserHashedPassword Objeto {@link EncryptionEngine.HashedPasswordDTO} que contiene el
     *                             hash de la contrasena y el salt correspondiente almacenados en la
     *                             base de datos. No puede ser <code>null</code>.
     * @return <code>true</code> si la contrasena proporcionada coincide con la de la base de datos,
     * <code>false</code> en caso contrario.
     * @throws IllegalArgumentException Si alguno de los parametros proporcionados es
     *                                  <code>null</code>.
     *                                  </body>
     */
    public static final Boolean validateProvidedClearPassword(String exUserClearPassword,
                                                       EncryptionEngine.HashedPasswordDTO dbUserHashedPassword) 
            throws IllegalArgumentException
    {
        return encryptionEngine.validateSubmittedPassword(exUserClearPassword,
                                                               dbUserHashedPassword);
    }

    /**
     * <body style="color: white;">
     * Este metodo toma como entrada un nombre de usuario proporcionado por el usuario para generar
     * un identificador unico (<code>UUID</code>) en formato hexadecimal. Este identificador se
     * deriva utilizando tecnicas de generacion de UUIDs provistas por la clase
     * {@link UUIDFormulator}.
     *
     * <p>El UUID generado esta basado en el nombre de usuario como una semilla o factor de
     * entrada,
     * lo que garantiza que el identificador sea consistentemente unico para cada usuario
     * particular, segun el algoritmo interno utilizado.
     *
     * @param exUserUsername El nombre de usuario proporcionado por el usuario. No puede ser
     *                       <code>null</code> ni vacio.
     * @return Una cadena de texto que representa el UUID generado en formato hexadecimal.
     * @throws IllegalArgumentException Si el nombre de usuario proporcionado es <code>null</code> o
     *                                  vacio.
     *                                  </body>
     */
    public static final String getUsernameUUID(String exUserUsername) {
        return uuidFormulator.produceUUIDInHex(exUserUsername);
    }
}
