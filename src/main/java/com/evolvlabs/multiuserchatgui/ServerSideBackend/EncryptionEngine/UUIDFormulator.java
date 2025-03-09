package com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-25
 * @description: El presente archivo contiene la clase base requerida para formular un UUID por
 * cliente basado en sus parametros internos. Este parametro permite identificar rapidamente a
 * los clientes por una mezcla de sus parametros, username, hash del username y un numero
 * aleatorio generad durante ejecucion. La idea de esta clase es otorgar a los usuarios un
 * identificador unico que pueda ser usado para enviar informacion, asociar mensajes hacia ellos,
 * y definir en tiempo de ejecucion quien envio o recibio un mensaje para su display.
 */
public class UUIDFormulator {

    /*! Parametros Internos*/
    /**
     * Generador de numeros aleatorios con {@link java.security.SecureRandom}, una de las clases fuertes para
     * generacion aleatoria. Se usa para generar una cadena de numeros que adjuntar al UUID
     */
    private static final SecureRandom  numberGenerator = new SecureRandom();
    /**
     * {@link java.security.MessageDigest}, proveniente del paquete de java.security que permite encryptar
     * usando
     * algoritmos probados en la industria (SHA-256, SHA-512, MD5, etc). Se usa para obtener una
     * sequencia basada en el nombre del usuario.
     */
    private static MessageDigest usernameHasher;


    /**
     * Constructor base de la clase, como sus parametros estan definidos internamente, no tenemos
     * que pasar nada al constructor, simplemente manejar la inicializacion del MessageDigest ya
     * que este puede dar errores en el encriptado si el algoritmo no es correcto (o su cadena
     * esta mal establecida)
     */
    public UUIDFormulator(){
        try{
            this.usernameHasher = MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException noSuchAlgorithmException){
            System.err.println("Exception Thrown in Constructor for UUIDFormulator Class. No " +
                                       "algorithm was found under the instance SHA-256");
            System.err.println("noSuchAlgorithmException = "
                                       + noSuchAlgorithmException.getMessage());
        }
    }

    /**
     * Metodo que se puede usar para retornar una cadena en Hexadecimal encriptada del nombre de
     * usuario de un cliente, anadida al final una cadena de numeros aleatoria. Juntas, estas dos
     * capas nos permiten crear un identificador unico basado en un algoritmo de encriptado.
     * @param ex_ClientUsername: Nombre de usuario del cliente
     * @return : String con la cadena encriptada en Hexadecimal
     * @throws NullPointerException: En caso de que el parametro de entrada sea nulo
     */
    public String produceUUIDInHex(String ex_ClientUsername) throws NullPointerException{
        /*! Intentemos encriptar el mensaje original*/
        byte[] hashFromClientUsername;
        if (!ex_ClientUsername.isEmpty() && ex_ClientUsername != null ){
            hashFromClientUsername =
                    usernameHasher.digest(
                            ex_ClientUsername
                                    .getBytes(
                                            StandardCharsets.UTF_8));
            /*! Convertimos la string a hexadecimal*/
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashFromClientUsername) {
                /*
                 * En Java, existe el problema de la conversion de valores byte con signo (ya que
                 * asi se guardan en Java todos los tipos de datos numericos) cuando pasa de byte
                 * a entero. En este proceso, al intentar hacer un toHexString, si no realizamos
                 * *una mascara directa al byte que se va a convertir, tendriamos hashes incorrectos
                 */
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }

            /*! Agregamos el numero*/
            hexString.append(numberGenerator.nextInt(10_000));

            return hexString.toString();

        }
        else {
            throw new NullPointerException("Error Code 0x001 - [Raised] Parametro " +
                                                   "ex_ClientUsername, del metodo " +
                                                   "produceUUIDInHex fue nulo.");
        }
    }

}
