/**
 *
 */
package org.mitre.jose.jwk;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.util.Base64;
import com.nimbusds.jose.util.Base64URL;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author yisheng.gu
 */
public class SimapleOctetSequenceKeyMaker {

    public static void main(String[] args) {
        String s = "eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJzZWN1cml0eSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJkZWZhdWx0LXRva2VuLWRsd2Q0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImRlZmF1bHQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiJjYWVlZTM1OC1hYTg2LTExZTgtYWQ1ZS1hYzFmNmI4M2RkNTYiLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6c2VjdXJpdHk6ZGVmYXVsdCJ9";
        String jsonString = new Base64(s).decodeToString();
        JsonObject jsonObject = new JsonParser().parse(jsonString).getAsJsonObject();
        System.out.println(jsonObject.get("kubernetes.io/serviceaccount/service-account.uid").getAsString());
    }
    /**
     * @param keySize in bits
     * @return
     */
    public static OctetSequenceKey make(Integer keySize, KeyUse use, Algorithm alg,
        String kid, String token) {

        // holder for the random bytes
        byte[] bytes = new byte[keySize / 8];

        String jsonString = new Base64(token).decodeToString();
        JsonObject jsonObject = new JsonParser().parse(jsonString).getAsJsonObject();
        System.out.println(jsonObject.getAsString());



        // make a random number generator and fill our holder
        //SecureRandom sr = new SecureRandom();
        //sr.nextBytes(bytes);

        Base64URL encoded = Base64URL.encode(bytes);

        // make a key
        OctetSequenceKey octetSequenceKey = new OctetSequenceKey.Builder(encoded)
                .keyID(kid)
                .algorithm(alg)
                .keyUse(use)
                .build();

        return octetSequenceKey;
    }

    private static void simpleBytes(byte[] bytes, String token) {


    }


}
