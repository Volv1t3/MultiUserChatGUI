package com.evolvlabs.multiuserchatgui.ServerSideBackend.EncryptionEngine;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.ForkJoinPool;

/**
 * @author : Paulo Cantos, Santiago Arellano
 * @date : 01-Mar-25
 * @description: El presente archivo contiene una serie de metodos que permiten encriptar
 * contrasenas ingresadas dentro de nuestra aplicacion a la hora de registrar clientes dentro del
 * servicio, usando algoritmos de encriptado de contrasenas fuertes, one-way, y provenientes del
 * estandar de Java.
 */
public class EncryptionEngine {

    /*! Parametros Internos*/
    /**
     * Parametro interno utilizado para determinar la cantidad de bits que usamos en el salting del
     * hash obtenido para la contrasena ingresada por el usuario. En nuestro cas son 16 bytes
     * generados con un {@link java.security.SecureRandom}, 128 bits en total.
     */
    private static final int LONGITUD_PASSWORD_SALTING  = 16;
    /**
     * Parametro que determina la cantidad de iteraciones que el algoritmo va a realizar antes de
     * retornar un resultado. Para este parametro nos basamos en una busqueda que retorno el
     * siguiente documento
     * <a href="https://nvlpubs.nist.gov/nistpubs/Legacy/SP/nistspecialpublication800-132.pdf"> NIST sobre Hashing</a>
     * . En base a este documento se decidio en un numero generico en el rango tolerado desde el
     * minimo de 1000 hasta el maximo de 10_000_000.
     */
    private static final int ITERACIONES_ALGORITMO      = 65536;
    /**
     * Este parametro determina la longitud del hash calculado.
     */
    private static final int LONGITUD_HASH_CREADO       = 256;

    /**
     * Parametro interno de tipo SecureRandom que permite generar el salting basado en sus
     * metodos de generacion de bits aleatorios.
     */
    private final SecureRandom randomSaltingGenerator;
    /**
     * Parametro que permite generar, en base a un metodo de fabrica interno del sistema llaves
     * criptograficas usando un algoritmo determinado. Este parametro se usa en conjuncion con el
     * proceso de encriptado Passowrd Based Key Derivation Formula 2 Con un algoritmo de
     * encriptado SHA256
     */
    private final SecretKeyFactory factoryParaLasKeysDelHash;
    static final String PBKDF_2_WITH_HMAC_SHA_256 = "PBKDF2WithHmacSHA256";

    /**
     * Constructor publico de la clase, no toma <b>ningun parametro ya que solo se encarga de
     * inicializar parametros internos</b>. La idea es usar un algoritmo de encriptacion fuerte
     * proveniente de la libreria estandar de Java. Existen otras versiones de calidad industrial
     * que se pueden usar pero son librerias externas.
     */
    public EncryptionEngine(){
        try{
            this.randomSaltingGenerator = new SecureRandom();
            this.factoryParaLasKeysDelHash = SecretKeyFactory
                    .getInstance(PBKDF_2_WITH_HMAC_SHA_256);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error en la carga del algoritmo solicitado, puede que la string " +
                                       "sea incorrecta");
            throw new RuntimeException(e);
        }
    }


    /**
     * Genera una formulacion de salting para reforzar la seguridad de contrasenas.
     *
     * <p>Este metodo utiliza la instancia interna del {@link SecureRandom} para generar un
     * arreglo de bytes aleatorios que se puede usar como un valor de "salt" para el hashing de las
     * contrasenas del usuario. El valor generado incrementa la complejidad de los hashes evitando
     * ataques como los de diccionario.</p>
     *
     * @param ex_SaltingLength La longitud deseada en bytes del arreglo generado. Este valor
     *                         determina cuanta aleatoriedad se anadira al algoritmo de hashing.
     * @return Un arreglo de bytes que contiene el salting generado de longitud especificada.
     * @throws NegativeArraySizeException Si {@code ex_SaltingLength} es menor a 0.
     */
    public final byte[] generateSaltingFormulation(int ex_SaltingLength)
            throws NegativeArraySizeException {
        byte[] saltingArray = new byte[ex_SaltingLength];
        this.randomSaltingGenerator.nextBytes(saltingArray);
        return saltingArray;
    }

    /**
     * Genera un hash seguro a partir de una contrasena ingresada por el usuario utilizando el
     * algoritmo PBKDF2 (Password-Based Key Derivation Function 2).
     *
     * <p>El metodo emplea el algoritmo estandarizado PBKDF2WithHmacSHA256 para derivar
     * una clave segura a partir de una contrasena y un "salting" aleatorio. Este enfoque utiliza un
     * enfoque basado en iteraciones multiples para dificultar intentos de ataque.</p>
     *
     * <p>El procedimiento incluye la generacion de una instancia {@link PBEKeySpec},
     * alimentada con los parametros pasados al metodo (contrasena, "salting", iteraciones y
     * longitud de clave deseada). Luego se utiliza una {@link SecretKeyFactory}, especificada con
     * el algoritmo PBKDF2WithHmacSHA256, para derivar el hash en bytes.
     * </p>
     *
     * <p>Este metodo asegura que las contrasenas sean procesadas de manera segura a nivel
     * criptografico en aplicaciones sensibles.</p>
     *
     * @param passwordArray      Un arreglo de caracteres que representa la contrasena ingresada por
     *                           el usuario. Es importante que esta contrasena sea fuerte y contenga
     *                           variedad de caracteres.
     * @param saltingFormulation Un arreglo de bytes generado previamente que actua como "salt".
     *                           Incluye elementos de aleatoriedad agregados para proteger contra
     *                           ataques de diccionario o rainbow tables.
     * @param numberOfIterations El numero de iteraciones con las que se procesara la funcion
     *                           PBKDF2. Un valor mas alto aumenta la complejidad y seguridad pero
     *                           requerira mas recursos computacionales.
     * @param lengthOfOutputKey  La longitud, en bits, del hash generado al final del proceso. Por
     *                           ejemplo, valores como 256 proporcionan un alto nivel de seguridad.
     * @return Un arreglo de bytes que contiene el hash seguro derivado usando los parametros
     * especificados. Este hash puede ser almacenado o usado para la autenticacion.
     * @throws RuntimeException Si ocurre un {@link InvalidKeySpecException}, lo que indica que los
     *                          parametros utilizados para generar la clave son invalidos.
     */
    public final byte[] generateCompletePassWrdHash(final char[] passwordArray,
                                                    final byte[] saltingFormulation,
                                                    final int numberOfIterations,
                                                    final int lengthOfOutputKey) {
        try {
            /*
             ? Para generar el hash, utilizamos el algoritmo de Password Based Key Derivation
             ? Function 2. Este algoritmo es el base que es estandarizo en Java, pero existen
             ? nuevos que se mencionan en la literatura que podriamos usar en librerias externas
             */
            KeySpec specForHashingKey = new PBEKeySpec(passwordArray,
                                                       saltingFormulation,
                                                       numberOfIterations,
                                                       lengthOfOutputKey);
    
            /*
             ? Para encriptar utilizamos una SecretKeyFactory con el mismo algoritmo que
             ? definimos anteriormente
             */
            return this.factoryParaLasKeysDelHash
                    .generateSecret(specForHashingKey).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Representa un contenedor para el almacenamiento de un hash de contrasena y su correspondiente
     * salting codificados en formato Base64, asegurando la comparacion y ordenamiento de estos
     * valores.
     */
    public static record HashedPasswordDTO(String _base64PWDHash,
                                           String _base64SaltingHash)
                            implements Comparable<HashedPasswordDTO>
    {

        /**
         * Constructor que inicializa un nuevo objeto {@link HashedPasswordDTO} con valores de hash
         * y salting en formato Base64, requeridos para garantizar la seguridad de las contrasenas.
         *
         * @param _base64PWDHash     String que contiene el valor del hash de la contrasena
         *                           codificado en Base64. Este valor debe ser generado y almacenado
         *                           de forma segura.
         * @param _base64SaltingHash String que representa el valor del salting asociado, tambien en
         *                           formato Base64, utilizado para agregar aleatoriedad y proteger
         *                           el hash de posibles ataques.
         */
        public HashedPasswordDTO(String _base64PWDHash, String _base64SaltingHash) {
            this._base64PWDHash = _base64PWDHash;
            this._base64SaltingHash = _base64SaltingHash;
        }

        /**
         * Devuelve una cadena que representa este objeto {@link HashedPasswordDTO}, mostrando sus
         * valores de hash y salting en Base64.
         *
         * @return Una representacion {@link String} de este objeto incluyendo los hashes y salting.
         */
        @Override
        public String toString() {
            return "HashedPasswordDTO{" +
                    "_base64PWDHash='" + _base64PWDHash + '\'' +
                    ", _base64SaltingHash='" + _base64SaltingHash + '\'' +
                    '}';
        }

        /**
         * Compara la igualdad de este objeto con otro. Dos objetos {@link HashedPasswordDTO} son
         * iguales si tanto sus hashes de contrasena como sus valores de salting coinciden.
         *
         * @param o El objeto a comparar con este.
         * @return {@code true} si los valores de _base64PWDHash y _base64SaltingHash coinciden con
         * los del objeto proporcionado, de lo contrario {@code false}.
         */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HashedPasswordDTO that = (HashedPasswordDTO) o;
            return _base64PWDHash.equals(that._base64PWDHash) &&
                    _base64SaltingHash.equals(that._base64SaltingHash);
        }

        /**
         * Compara dos objetos {@link HashedPasswordDTO} para determinar su orden. Esto utiliza la
         * comparacion lexicografica de los valores hash, y si son iguales, se utiliza la
         * comparacion de los valores de salting.
         *
         * @param other Otro objeto de tipo {@link HashedPasswordDTO} a comparar con este.
         * @return Un resultado negativo, cero, o positivo dependiendo de si este objeto es menor,
         * igual a, o mayor que el objeto proporcionado, basado en comparaciones lexicograficas.
         */
        @Override
        public int compareTo(HashedPasswordDTO other) {
            int pwdHashComparison = this._base64PWDHash.compareTo(other._base64PWDHash);
            return (pwdHashComparison != 0) ? pwdHashComparison : this._base64SaltingHash.compareTo(other._base64SaltingHash);
        }

    }

    /**
     * Convierte dos arreglos de bytes generados para hashing en cadenas codificadas en Base64, y
     * los empaqueta en un objeto {@link HashedPasswordDTO} para su almacenamiento seguro o
     * transferencia.
     *
     * <p>Este metodo utiliza la clase {@link Base64} para codificar los datos del hash y su
     * correspondiente salting en formato Base64, un formato seguro y eficiente para transmitir
     * datos binarios en forma de texto. Luego, empaqueta estos valores en una instancia del record
     * {@link HashedPasswordDTO}, que encapsula ambas cadenas y garantiza la integridad de los datos
     * sensibles de contrasena.</p>
     *
     * <p>La utilizacion de este metodo asegura que tanto el hashing como el salting sean
     * codificados
     * de forma universalmente compatible, reduciendo riesgos al compartir o almacenar los
     * valores.</p>
     *
     * @param saltingFormulation Un arreglo de bytes que representa el salting utilizado para
     *                           asegurar la contrasena. Este salting debe haber sido generado
     *                           utilizando un metodo seguro, como {@link SecureRandom}.
     * @param passwordHashResult Un arreglo de bytes que contiene el hash de la contrasena generada
     *                           previamente con un enfoque seguro basado en iteraciones.
     * @return Una instancia de {@link HashedPasswordDTO} que contiene ambas cadenas codificadas en
     * Base64 (hash y salting), listas para ser utilizadas.
     * @throws IllegalArgumentException Si alguno de los parametros proporcionados es {@code null}.
     *                                  Esto se controla para evitar errores al codificar en
     *                                  Base64.
     */
    public HashedPasswordDTO prepareInformationForDTO(final byte[] saltingFormulation,
                                                      final byte[] passwordHashResult) {
        if (saltingFormulation == null || passwordHashResult == null) {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] Los parametros " +
                                                       "saltingFormulation o " +
                                                       "passwordHashResult no pueden ser nulos.");
        }

        if (saltingFormulation.length == 0 || passwordHashResult.length == 0) {
            throw new IllegalArgumentException("Error Code 0x001 " +
                      "- [Raised] Los arreglos de bytes no pueden estar vacios.");
        }


        //? 1. Convertimos el saltingFormulation directamente a un base64 string
            String Base64SaltingFormulationString =
                    Base64.getEncoder().encodeToString(saltingFormulation);

            //? 2. Convertimos el passwordHashResult directamente a un base64 string
            String Base64PasswordHashResult =
                    Base64.getEncoder().encodeToString(passwordHashResult);

            //? 3. Generamos un RecordDTO para enviar la informacion
            return new HashedPasswordDTO(Base64PasswordHashResult,
                                         Base64SaltingFormulationString);
        }

    /**
     * Valida la contrasena proporcionada por el usuario frente a un conjunto de datos almacenados
     * del hash de contrasena y salting previamente generados.
     *
     * <p>Esta funcion utiliza el salting y hash almacenado en el objeto {@link HashedPasswordDTO}
     * para generar
     * un hash basado en la contrasena provista por el usuario para compararla con el hash
     * registrado. El resultado es una verificacion de igualdad utilizando la clase
     * {@link MessageDigest#isEqual(byte[], byte[])} para evitar problemas de tiempo con
     * comparaciones mas tradicionales y vulnerables.</p>
     *
     * <h3>Proceso del Metodo:</h3>
     * <ol>
     *     <li>Decodifica el salting y el hash almacenados en formato Base64 utilizando {@link Base64.Decoder}.</li>
     *     <li>Recrea un hash nuevo basado en la contrasena ingresada, utilizando el mismo algoritmo y parametros
     *         definidos previamente como {@link #generateCompletePassWrdHash(char[], byte[], int, int)}.</li>
     *     <li>Verifica si el hash generado coincide con el hash almacenado, devolviendo el resultado
     *         de dicha comparacion.</li>
     * </ol>
     *
     * @param exUserPassword          La contrasena ingresada por el usuario que se desea validar.
     *                                Se debe proporcionar en formato {@link String}.
     * @param exHashPasswordResultDTO Una instancia de {@link HashedPasswordDTO}, que contiene el
     *                                hash y salting generados previamente almacenados en cadenas
     *                                codificadas en Base64.
     * @return {@code true} si la contrasena generada coincide con la contrasena almacenada;
     * {@code false} si no coincide.
     * @throws NullPointerException Si alguno de los parametros proporcionados es {@code null}.
     */
    public boolean validateSubmittedPassword(String exUserPassword,
                                             HashedPasswordDTO exHashPasswordResultDTO) {
        if (exUserPassword == null || exHashPasswordResultDTO == null){
            throw new NullPointerException("Error Code 0x001 - [Raised] Los parametros " +
                                                   "exUserPassword" +
                                                   " o " +
                                                   "exHashPasswordResultDTO no pueden ser nulos.");
        }
        byte[] decodedSaltingInformation = null;
        byte[] decodedPWDHashResult = null;
        byte[] submittedPasswordHash = null;
        boolean isValid = false;

        try {
            //? 1. Convertimos el password del usuario hacia un password del sistema (hash)
            decodedSaltingInformation =
                    Base64.getDecoder().decode(exHashPasswordResultDTO._base64SaltingHash);
            decodedPWDHashResult =
                    Base64.getDecoder().decode(exHashPasswordResultDTO._base64PWDHash);
            //? 2. Convertimos a un password del sistema el password enviado desde la UI
            submittedPasswordHash =
                    generateCompletePassWrdHash(exUserPassword.toCharArray(),
                                                decodedSaltingInformation,
                                                ITERACIONES_ALGORITMO,
                                                LONGITUD_HASH_CREADO);

            //? 3. Revisamos igualdad de hashes, usamos una clase establecida para este trabajo como
            //? lo es MessgeDisgest.
            isValid = MessageDigest
                        .isEqual(submittedPasswordHash, decodedPWDHashResult);
        }catch (IllegalArgumentException illegalArgumentException){
            throw new IllegalArgumentException("Error Code 0x002 - [Raised] Error en la " +
                                                       "decodificacion Base64: "
                                                       + illegalArgumentException.getMessage());
        } finally {
            //? 4. Limpiamos la memoria
            if (decodedSaltingInformation != null) {
                Arrays.fill(decodedSaltingInformation, (byte) 0);
            }
            if (decodedPWDHashResult != null) {
                Arrays.fill(decodedPWDHashResult, (byte) 0);
            }
            if (submittedPasswordHash != null) {
                Arrays.fill(submittedPasswordHash, (byte) 0);
            }
        }
        return isValid;
    }

    /**
     * Procesa una nueva contrasena proporcionada por el usuario y genera un objeto de tipo
     * {@link HashedPasswordDTO} que contiene un hash seguro y su salting asociado, ambos en formato
     * Base64.
     *
     * <p>Este metodo utiliza metodos criptograficos para encriptar la contrasena con el objetivo
     * de almacenarla de forma segura. Implementa un enfoque basado en salting y hashing, aplicando
     * el algoritmo PBKDF2 con SHA-256, combinado con un salting aleatorio generado por
     * {@link SecureRandom}. La longitud del salting y la cantidad de iteraciones son configuradas
     * de acuerdo con mejores practicas de seguridad.</p>
     *
     * <h3>Proceso del Metodo:</h3>
     * <ol>
     *     <li>Valida la contrasena proporcionada asegurandose que no sea nula ni vacia.</li>
     *     <li>Genera un salting aleatorio utilizando el metodo {@link #generateSaltingFormulation(int)}.</li>
     *     <li>Aplica hashing seguro sobre la contrasena utilizando el algoritmo PBKDF2 y el salting generado
     *         mediante {@link #generateCompletePassWrdHash(char[], byte[], int, int)}.</li>
     *     <li>Empaqueta el hash resultante y el salting dentro de un objeto {@link HashedPasswordDTO}
     *         utilizando el metodo {@link #prepareInformationForDTO(byte[], byte[])}.</li>
     *     <li>Limpia la memoria de cualquier informacion sensible utilizada durante el proceso.</li>
     * </ol>

     * @param newPassword La nueva contrasena proporcionada por el usuario, la cual sera procesada
     *                    en el sistema. No puede ser nula ni vacia. Se recomienda que la contrasena
     *                    sea fuerte.
     * @return Un objeto {@link HashedPasswordDTO} que contiene un hash seguro y su correspondiente
     * salting, ambos codificados en formato Base64.
     * @throws IllegalArgumentException Si la contrasena proporcionada es {@code null} o esta
     *                                  vacia.
     */
    public HashedPasswordDTO processNewUserPassword(final String newPassword) {
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Error Code 0x001 - [Raised] La contrasena no " +
                                                       "puede ser nula o vacaa");
        }

        try {
            byte[] saltingFormulation =
                    generateSaltingFormulation(LONGITUD_PASSWORD_SALTING);
            byte[] passwordHash = generateCompletePassWrdHash(
                    newPassword.toCharArray(),
                    saltingFormulation,
                    ITERACIONES_ALGORITMO,
                    LONGITUD_HASH_CREADO
                                                             );

            return prepareInformationForDTO(saltingFormulation, passwordHash);
        } finally {
            // Limpieza de memoria
            Arrays.fill(newPassword.toCharArray(), '\0');
        }

    }

}


