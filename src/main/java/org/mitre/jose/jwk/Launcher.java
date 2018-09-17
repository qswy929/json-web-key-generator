package org.mitre.jose.jwk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.IOUtils;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.ECKey.Curve;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyType;
import com.nimbusds.jose.jwk.KeyUse;

/**
 * Small Helper App to generate Json Web Keys
 */
public class Launcher {

    private static Options options;

    public static void main(String[] args) {

        options = new Options();

        options.addOption("t", true, "Key Type, one of: " + KeyType.RSA.getValue() + ", " + KeyType.OCT.getValue() + ", " +
                KeyType.EC.getValue() + ", simple");
        options.addOption("s", true, "Key Size in bits, required for RSA and oct key types. Must be an integer divisible by 8");
        options.addOption("u", true, "Usage, one of: enc, sig (optional)");
        options.addOption("a", true, "Algorithm (optional)");
        options.addOption("i", true, "Key ID (optional), one will be generated if not defined");
        options.addOption("I", false, "Don't generate a Key ID if none defined");
        options.addOption("p", false, "Display public key separately");
        options.addOption("c", true, "Key Curve, required for EC key type. Must be one of " + Curve.P_256 + ", " + Curve.P_384
				+ ", " + Curve.P_521);
        options.addOption("S", false, "Wrap the generated key in a KeySet");
        options.addOption("o", true, "Write output to file (will append to existing KeySet if -S is used), No Display of Key "
				+ "Material");
        options.addOption("k", true, "Kubernetes Service Account Token");

        CommandLineParser parser = new PosixParser();
        try {
            CommandLine cmd = parser.parse(options, args);

            String kty = cmd.getOptionValue("t");
            String size = cmd.getOptionValue("s");
            String use = cmd.getOptionValue("u");
            String alg = cmd.getOptionValue("a");
            String kid = cmd.getOptionValue("i");
            String crv = cmd.getOptionValue("c");
            boolean keySet = cmd.hasOption("S");
            boolean pubKey = cmd.hasOption("p");
            boolean doNotGenerateKid = cmd.hasOption("I");
            String outFile = cmd.getOptionValue("o");
            String token = cmd.getOptionValue("k");

            // check for required fields
            if (kty == null) {
                printUsageAndExit("Key type must be supplied.");
            }

            // parse out the important bits

            KeyType keyType = KeyType.parse(kty);

            KeyUse keyUse = null;
            if (use != null) {
                if (use.equals("sig")) {
                    keyUse = KeyUse.SIGNATURE;
                } else if (use.equals("enc")) {
                    keyUse = KeyUse.ENCRYPTION;
                } else {
                    printUsageAndExit("Invalid key usage, must be 'sig' or 'enc', got " + use);
                }
            }

            if (Strings.isNullOrEmpty(kid)) {
                kid = doNotGenerateKid ? null : generateKid(keyUse);
            }

            Algorithm keyAlg = null;
            if (!Strings.isNullOrEmpty(alg)) {
                keyAlg = JWSAlgorithm.parse(alg);
            }

            JWK jwk = null;

            if (keyType.equals(KeyType.RSA)) {
                // surrounding try/catch catches numberformatexception from this
                if (Strings.isNullOrEmpty(size)) {
                    printUsageAndExit("Key size (in bits) is required for key type " + keyType);
                }

                Integer keySize = Integer.decode(size);
                if (keySize % 8 != 0) {
                    printUsageAndExit("Key size (in bits) must be divisible by 8, got " + keySize);
                }

                jwk = RSAKeyMaker.make(keySize, keyUse, keyAlg, kid);
            } else if (keyType.equals(KeyType.OCT)) {
                // surrounding try/catch catches numberformatexception from this
                if (Strings.isNullOrEmpty(size)) {
                    printUsageAndExit("Key size (in bits) is required for key type " + keyType);
                }
                Integer keySize = Integer.decode(size);
                if (keySize % 8 != 0) {
                    printUsageAndExit("Key size (in bits) must be divisible by 8, got " + keySize);
                }

                jwk = OctetSequenceKeyMaker.make(keySize, keyUse, keyAlg, kid);
            } else if (keyType.equals(KeyType.EC)) {
                if (Strings.isNullOrEmpty(crv)) {
                    printUsageAndExit("Curve is required for key type " + keyType);
                }
                Curve keyCurve = Curve.parse(crv);
                jwk = ECKeyMaker.make(keyCurve, keyUse, keyAlg, kid);
            } else if (keyType.getValue().equalsIgnoreCase("simple")) {
                if (Strings.isNullOrEmpty(size)) {
                    printUsageAndExit("Key size (in bits) is required for key type " + keyType);
                }
                Integer keySize = Integer.decode(size);
                if (keySize % 8 != 0) {
                    printUsageAndExit("Key size (in bits) must be divisible by 8, got " + keySize);
                }
                if (Strings.isNullOrEmpty(token)) {
                    printUsageAndExit("Token can't be empty when key type is 'simple'");
                }
                String[] parts = token.split(".");
                if (parts.length != 3) {
                    printUsageAndExit("Invalid token format.");
                }

                jwk = SimapleOctetSequenceKeyMaker.make(keySize, keyUse, keyAlg, kid, parts[1]);
            }
            else {
                printUsageAndExit("Unknown key type: " + keyType);
            }

            // round trip it through GSON to get a prettyprinter
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            if (outFile == null) {

                System.out.println("Full key:");

                printKey(keySet, jwk, gson);

                if (pubKey) {
                    System.out.println(); // spacer

                    // also print public key, if possible
                    JWK pub = jwk.toPublicJWK();

                    if (pub != null) {
                        System.out.println("Public key:");
                        printKey(keySet, pub, gson);
                    } else {
                        System.out.println("No public key.");
                    }
                }
            } else {
                writeKeyToFile(keySet, outFile, jwk, gson);
            }

        } catch (NumberFormatException e) {
            printUsageAndExit("Invalid key size: " + e.getMessage());
        } catch (ParseException e) {
            printUsageAndExit("Failed to parse arguments: " + e.getMessage());
        } catch (java.text.ParseException e) {
            printUsageAndExit("Could not parse existing KeySet: " + e.getMessage());
        } catch (IOException e) {
            printUsageAndExit("Could not read existing KeySet: " + e.getMessage());
        }
    }

    private static String generateKid(KeyUse keyUse) {
        String prefix = keyUse == null ? "" : keyUse.identifier();
        return prefix + (System.currentTimeMillis() / 1000);
    }

    private static void writeKeyToFile(boolean keySet, String outFile, JWK jwk, Gson gson) throws IOException,
            java.text.ParseException {
        JsonElement json;
        File output = new File(outFile);
        if (keySet) {
            List<JWK> existingKeys = output.exists() ? JWKSet.load(output).getKeys() : Collections.<JWK>emptyList();
            List<JWK> jwkList = new ArrayList<JWK>(existingKeys);
            jwkList.add(jwk);
            JWKSet jwkSet = new JWKSet(jwkList);
            json = new JsonParser().parse(jwkSet.toJSONObject(false).toJSONString());
        } else {
            json = new JsonParser().parse(jwk.toJSONString());
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(output);
            IOUtils.write(gson.toJson(json), os);
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    private static void printKey(boolean keySet, JWK jwk, Gson gson) {
        if (keySet) {
            JWKSet jwkSet = new JWKSet(jwk);
            JsonElement json = new JsonParser().parse(jwkSet.toJSONObject(false).toJSONString());
            System.out.println(gson.toJson(json));
        } else {
            JsonElement json = new JsonParser().parse(jwk.toJSONString());
            System.out.println(gson.toJson(json));
        }
    }

    // print out a usage message and quit
    private static void printUsageAndExit(String message) {
        if (message != null) {
            System.err.println(message);
        }

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar json-web-key-generator.jar -t <keyType> [options]", options);

        // kill the program
        System.exit(1);
    }
}
