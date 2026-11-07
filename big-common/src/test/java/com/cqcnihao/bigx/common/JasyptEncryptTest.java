package com.cqcnihao.bigx.common;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.Test;

class JasyptEncryptTest {

    @Test
    void encryptPasswords() {
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setPassword("bigwebSecretKey");
        encryptor.setAlgorithm("PBEWithMD5AndDES");

        String[] passwords = {"root", "cqcnihao"};
        for (String pwd : passwords) {
            String encrypted = encryptor.encrypt(pwd);
            System.out.println("Original: " + pwd + " -> ENC(" + encrypted + ")");
        }
    }
}
