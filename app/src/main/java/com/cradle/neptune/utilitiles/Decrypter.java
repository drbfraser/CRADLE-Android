package com.cradle.neptune.utilitiles;


/*
Code taken and modified  from :  https://gist.github.com/SoftwareJock/cedc541568767dafc484
 */


import java.security.spec.KeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Random;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;


public class Decrypter {

    private static final String PREFS_NAME = "AnDeShPr",			// arbitrary Shared Preferences file name
            IVECTOR = "IVect";					  // arbitrary Shared Preferences string name
    private Cipher cipher;
    private SecretKey secretKySpec;
    private IvParameterSpec ivParmSpec;


    public Decrypter(Context contextIn) throws Exception {

        if (!(DecrypterSetup(contextIn)))
            return;			// setup failed


        // 4 test cases follow.
        StringBuilder strbDataEncrypted = new StringBuilder(),
                strbDataDecrypted = new StringBuilder();
        Toast toast;

        String encryptData = "1234+-ABCD";
        if (encrypt(encryptData, strbDataEncrypted)) {
            if (decrypt(strbDataEncrypted.toString(), strbDataDecrypted)) {

                Log.d("buggg","encrpt input: "+encryptData+ " encrypted output: "+ strbDataEncrypted + "\n Decryptd: "+strbDataDecrypted);

                toast = Toast.makeText(contextIn, "encrypt input = " + encryptData, Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "encrypt output = " + strbDataEncrypted.toString(), Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "decrypt output = " + strbDataDecrypted.toString(), Toast.LENGTH_LONG);
            }
            else
                toast = Toast.makeText(contextIn, "decryption failed", Toast.LENGTH_LONG);
        }
        else
            toast = Toast.makeText(contextIn, "encryption failed", Toast.LENGTH_LONG);
        toast.show();

        encryptData = " spaces ";
        if (encrypt(encryptData, strbDataEncrypted)) {
            if (decrypt(strbDataEncrypted.toString(), strbDataDecrypted)) {
                toast = Toast.makeText(contextIn, "encrypt input = " + encryptData, Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "encrypt output = " + strbDataEncrypted.toString(), Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "decrypt output = " + strbDataDecrypted.toString(), Toast.LENGTH_LONG);
            }
            else
                toast = Toast.makeText(contextIn, "decryption failed", Toast.LENGTH_LONG);
        }
        else
            toast = Toast.makeText(contextIn, "encryption failed", Toast.LENGTH_LONG);
        toast.show();

        encryptData = "A";
        if (encrypt(encryptData, strbDataEncrypted)) {
            if (decrypt(strbDataEncrypted.toString(), strbDataDecrypted)) {
                toast = Toast.makeText(contextIn, "encrypt input = " + encryptData, Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "encrypt output = " + strbDataEncrypted.toString(), Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "decrypt output = " + strbDataDecrypted.toString(), Toast.LENGTH_LONG);
            }
            else
                toast = Toast.makeText(contextIn, "decryption failed", Toast.LENGTH_LONG);
        }
        else
            toast = Toast.makeText(contextIn, "encryption failed", Toast.LENGTH_LONG);
        toast.show();

        encryptData = "12345678901234567890";
        if (encrypt(encryptData, strbDataEncrypted)) {
            if (decrypt(strbDataEncrypted.toString(), strbDataDecrypted)) {
                toast = Toast.makeText(contextIn, "encrypt input  = " + encryptData, Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "encrypt output  = " + strbDataEncrypted.toString(), Toast.LENGTH_LONG);
                toast.show();
                toast = Toast.makeText(contextIn, "decrypt output  = " + strbDataDecrypted.toString(), Toast.LENGTH_LONG);
            }
            else
                toast = Toast.makeText(contextIn, "decryption failed", Toast.LENGTH_LONG);
        }
        else
            toast = Toast.makeText(contextIn, "encryption failed", Toast.LENGTH_LONG);
        toast.show();

    } // AndroidDecrypter


    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // Returns:
    //   true  -- success encryption/decryption mechanism is available
    //   false -- encryption/decryption mechanism is not available
    public Boolean DecrypterSetup(Context context) throws Exception {
        String secretKeyAlgorithm = "PBKDF2WithHmacSHA1",
                keySalt = "61z5RB8j",			// chosen at random, minimum length of 64 bits
                passPhrase = "tN19e3P9G";		// chosen at random
        int iterationCount = 1024,
                keyStrength = 256;

        SecretKeyFactory factory;
        KeySpec spec;
        SecretKey secretKy;
        byte[] initializationVector = new byte[16];

        factory = SecretKeyFactory.getInstance(secretKeyAlgorithm);
        spec = new PBEKeySpec(passPhrase.toCharArray(), keySalt.getBytes(), iterationCount, keyStrength);
        secretKy = factory.generateSecret(spec);		// this call takes close to 10 seconds 
        secretKySpec = new SecretKeySpec(secretKy.getEncoded(), "AES");
        cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");

        // If an encryption initialization vector was created in a previous instance of this app, use it. 
        //  (The same initialization vector that was used to encrypt items must be used to decrypt them later.)
        //  Otherwise, create one.

        // The initialization vector is stored in the app's Shared Preferences area.
        SharedPreferences settingsSharedPref = context.getSharedPreferences(PREFS_NAME, 0);
        String strIV = settingsSharedPref.getString(IVECTOR, "");
        if (strIV.length() == 0) {
            Random randomGenerator = new Random();
            randomGenerator.nextBytes(initializationVector);
            // Save the initialization vector.
            SharedPreferences.Editor editSharedPref = settingsSharedPref.edit();
            editSharedPref.putString(IVECTOR, Base64.encodeToString(initializationVector, 0));
            if (!(editSharedPref.commit())) {
                // Log ("Shared Preferences commit failed at " + fullClassName + ", " + className + ", " + methodName + "; " + errorLineNumber, context);
                return false;
            }
        }
        else
            initializationVector = Base64.decode(strIV, 0);

        ivParmSpec = new IvParameterSpec(initializationVector);

        return true;
    } // DecrypterSetup


    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // If the input string is a null string, no encryption will be performed & the result string will be a null string & the returned result will be true (success).
    // Returns:
    //   true  -- success
    //   false -- failure
    private Boolean encrypt(String dataToEncrypt, StringBuilder strbDataEncrypted) throws Exception {

        strbDataEncrypted.setLength(0);

        // If the input string is null, no encryption is performed.
        if (dataToEncrypt.length() > 0) {
            byte[] utf8EncryptedData;
            cipher.init(Cipher.ENCRYPT_MODE, secretKySpec, ivParmSpec);
            utf8EncryptedData = cipher.doFinal(dataToEncrypt.getBytes("UTF-8"));
            strbDataEncrypted.append(Base64.encodeToString(utf8EncryptedData, 0));
        }

        return true;
    } // encrypt


    // +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
    // If the input string is a null string, no decryption will be performed & the result string will be a null string & the returned result will be true (success).
    // Returns:
    //   true  -- success
    //   false -- failure
    private Boolean decrypt(String dataToDecrypt, StringBuilder strbDataDecrypted) throws Exception {

        strbDataDecrypted.setLength(0);

        // If the input string is null, no decryption is performed.
        if (dataToDecrypt.length() > 0) {
            byte[] decryptedData;
            byte[] utf8;
            String strOut;

            cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKySpec, ivParmSpec);
            decryptedData = Base64.decode(dataToDecrypt, 0);
            utf8 = cipher.doFinal(decryptedData);
            strOut = new String(utf8, "UTF8");

            strbDataDecrypted.append(strOut);
        }

        return true;
    } // decrypt

} // AndroidDecrypter