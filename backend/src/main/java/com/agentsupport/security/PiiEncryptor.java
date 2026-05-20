package com.agentsupport.security;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy(false)
public class PiiEncryptor {

  private static volatile PiiEncryptor INSTANCE;

  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final SecureRandom RNG = new SecureRandom();

  private final SecretKeySpec secretKey;

  public PiiEncryptor(@Value("${pii.encryption-key}") String base64Key) {
    byte[] keyBytes = Base64.getDecoder().decode(base64Key);
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
  }

  @PostConstruct
  void registerInstance() {
    INSTANCE = this;
  }

  public static PiiEncryptor instance() {
    return INSTANCE;
  }

  public String encrypt(String plaintext) {
    try {
      byte[] iv = new byte[IV_LENGTH];
      RNG.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] result = new byte[IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, result, 0, IV_LENGTH);
      System.arraycopy(ciphertext, 0, result, IV_LENGTH, ciphertext.length);
      return Base64.getEncoder().encodeToString(result);
    } catch (Exception e) {
      throw new IllegalStateException("PII encryption failed", e);
    }
  }

  public String decrypt(String encoded) {
    try {
      byte[] decoded = Base64.getDecoder().decode(encoded);
      if (decoded.length < IV_LENGTH) throw new IllegalArgumentException("too short");
      byte[] iv = new byte[IV_LENGTH];
      byte[] ciphertext = new byte[decoded.length - IV_LENGTH];
      System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
      System.arraycopy(decoded, IV_LENGTH, ciphertext, 0, ciphertext.length);
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new IllegalStateException("PII decryption failed", e);
    }
  }
}
