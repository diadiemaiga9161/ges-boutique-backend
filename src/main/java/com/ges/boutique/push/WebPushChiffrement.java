package com.ges.boutique.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.*;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement des notifications push (normes Web Push : RFC 8291 « aes128gcm » et RFC 8292
 * « VAPID »), avec les seuls outils de Java : aucune bibliothèque à ajouter.
 * Le téléphone fournit sa clé publique (p256dh) et un secret (auth) ; le message est chiffré pour
 * lui seul, puis envoyé au service de notifications d'Apple ou de Google.
 */
public final class WebPushChiffrement {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();
    private static final ECParameterSpec P256 = parametresP256();

    private WebPushChiffrement() {}

    public static String b64(byte[] octets) {
        return B64URL.encodeToString(octets);
    }

    public static byte[] deB64(String texte) {
        return B64URL_DEC.decode(texte.trim().replace('+', '-').replace('/', '_').replace("=", ""));
    }

    /** Nouvelle paire de clés P-256 (clés VAPID du serveur, ou clé éphémère d'un message). */
    public static KeyPair nouvellePaire() throws GeneralSecurityException {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        return g.generateKeyPair();
    }

    /** Clé publique au format « point non compressé » (65 octets), celui attendu par les navigateurs. */
    public static byte[] publiqueBrute(ECPublicKey cle) {
        byte[] x = sur32(cle.getW().getAffineX());
        byte[] y = sur32(cle.getW().getAffineY());
        byte[] r = new byte[65];
        r[0] = 0x04;
        System.arraycopy(x, 0, r, 1, 32);
        System.arraycopy(y, 0, r, 33, 32);
        return r;
    }

    public static ECPublicKey publiqueDepuisBrute(byte[] brute) throws GeneralSecurityException {
        if (brute.length != 65 || brute[0] != 0x04) throw new InvalidKeyException("Clé publique invalide");
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(brute, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(brute, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    public static ECPrivateKey priveeDepuisBrute(byte[] brute) throws GeneralSecurityException {
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, brute), P256));
    }

    public static byte[] priveeBrute(ECPrivateKey cle) {
        return sur32(cle.getS());
    }

    /** Chiffre le message pour un téléphone (sel et clé éphémère tirés au hasard). */
    public static byte[] chiffrer(byte[] message, byte[] p256dhTelephone, byte[] authTelephone) throws GeneralSecurityException {
        byte[] sel = new byte[16];
        new SecureRandom().nextBytes(sel);
        return chiffrer(message, p256dhTelephone, authTelephone, nouvellePaire(), sel);
    }

    /** Même chose avec clé éphémère et sel imposés (sert à vérifier avec l'exemple de la RFC 8291). */
    static byte[] chiffrer(byte[] message, byte[] p256dhTelephone, byte[] authTelephone,
                           KeyPair ephemere, byte[] sel) throws GeneralSecurityException {
        byte[] clePubliqueServeur = publiqueBrute((ECPublicKey) ephemere.getPublic());

        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(ephemere.getPrivate());
        ka.doPhase(publiqueDepuisBrute(p256dhTelephone), true);
        byte[] secretPartage = ka.generateSecret();

        byte[] prkCle = hmac(authTelephone, secretPartage);
        byte[] infoCle = concat("WebPush: info".getBytes(StandardCharsets.US_ASCII), new byte[]{0},
                p256dhTelephone, clePubliqueServeur, new byte[]{1});
        byte[] ikm = hmac(prkCle, infoCle);
        byte[] prk = hmac(sel, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 1})), 12);

        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] chiffre = aes.doFinal(concat(message, new byte[]{2}));

        ByteBuffer entete = ByteBuffer.allocate(16 + 4 + 1 + 65);
        entete.put(sel).putInt(4096).put((byte) 65).put(clePubliqueServeur);
        return concat(entete.array(), chiffre);
    }

    /** Jeton VAPID signé (ES256) qui prouve au service de notifications que l'envoi vient de ce serveur. */
    public static String jetonVapid(String audience, String contact, ECPrivateKey clePrivee, long expirationSecondes)
            throws GeneralSecurityException {
        String entete = b64("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String corps = b64(("{\"aud\":\"" + audience + "\",\"exp\":" + expirationSecondes
                + ",\"sub\":\"" + contact + "\"}").getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withECDSAinP1363Format");
        s.initSign(clePrivee);
        s.update((entete + "." + corps).getBytes(StandardCharsets.US_ASCII));
        return entete + "." + corps + "." + b64(s.sign());
    }

    private static byte[] hmac(byte[] cle, byte[] donnees) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle, "HmacSHA256"));
        return mac.doFinal(donnees);
    }

    private static byte[] concat(byte[]... parties) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parties) out.writeBytes(p);
        return out.toByteArray();
    }

    private static byte[] sur32(BigInteger n) {
        byte[] b = n.toByteArray();
        if (b.length == 32) return b;
        byte[] r = new byte[32];
        if (b.length > 32) System.arraycopy(b, b.length - 32, r, 0, 32);
        else System.arraycopy(b, 0, r, 32 - b.length, b.length);
        return r;
    }

    private static ECParameterSpec parametresP256() {
        try {
            AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
            p.init(new ECGenParameterSpec("secp256r1"));
            return p.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
