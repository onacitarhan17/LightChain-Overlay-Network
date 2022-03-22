package model.crypto.ecdsa;

import model.crypto.Signature;
import model.lightchain.Identifier;

/**
 * ECDSA signature implementation with signer ID.
 */
public class EcdsaSignature extends Signature {
  private static final String SIGN_ALG_SHA_3_256_WITH_ECDSA = "SHA3-256withECDSA";

  public EcdsaSignature(byte[] bytes, Identifier signerId) {
    super(bytes, signerId);
  }
}
