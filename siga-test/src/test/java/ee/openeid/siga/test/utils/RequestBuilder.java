package ee.openeid.siga.test.utils;

import ee.openeid.siga.test.model.SigaApiFlow;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.utils.HmacSigner.generateHmacSignature;

public class RequestBuilder {

    public static JSONObject hashcodeContainersDataRequestWithDefault() throws JSONException {
        return hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
    }

    public static JSONObject hashcodeContainersDataRequest(String fileName, String fileHashSha256, String fileHashSha512, String fileSize) throws JSONException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", fileName);
        dataFileObject.put("fileHashSha256", fileHashSha256);
        dataFileObject.put("fileHashSha512", fileHashSha512);
        dataFileObject.put("fileSize", fileSize);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        return request;
    }

    public static JSONObject hashcodeContainerRequestFromFile(String containerName) throws JSONException, IOException {
        JSONObject request = new JSONObject();
        ClassLoader classLoader = RequestBuilder.class.getClassLoader();
        String path = classLoader.getResource(containerName).getPath();
        String file = Base64.encodeBase64String(Files.readAllBytes(FileSystems.getDefault().getPath(path)));
        request.put("container", file);
        return request;
    }

    public static JSONObject hashcodeContainerRequest(String containerBase64) throws JSONException, IOException {
        JSONObject request = new JSONObject();
        request.put("container", containerBase64);
        return request;
    }

    public static JSONObject hashcodeRemoteSigningRequestWithDefault(String signingCertificate, String signatureProfile) throws JSONException {
        return hashcodeRemoteSigningRequest(signingCertificate, signatureProfile, null, null);
    }

    public static JSONObject hashcodeRemoteSigningRequest(String signingCertificate, String signatureProfile, String roles, String signatureProductionPlace) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("signingCertificate", signingCertificate);
        request.put("signatureProfile", signatureProfile);
        if (roles != null) {
            request.put("roles", roles);
        }
        if (signatureProductionPlace != null) {
            request.put("signatureProductionPlace", signatureProductionPlace);
        }
        return request;
    }

    public static JSONObject hashcodeRemoteSigningSignatureValueRequest(String signatureValue) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);
        return request;
    }

    public static JSONObject hashcodeMidSigningRequestWithDefault(String personIdentifier, String phoneNo) throws JSONException {
        return hashcodeMidSigningRequest(personIdentifier, phoneNo, "EE", "EST", "LT", "something", null, null, null, null, null);
    }

    public static JSONObject hashcodeMidSigningRequest(String personIdentifier, String phoneNo, String originCountry, String language, String signatureProfile, String messageToDisplay, String city, String stateOrProvince, String postalCode, String country, String roles) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", personIdentifier);
        request.put("phoneNo", phoneNo);
        request.put("country", originCountry);
        request.put("language", language);
        request.put("signatureProfile", signatureProfile);

        if (messageToDisplay != null) {
            request.put("messageToDisplay", messageToDisplay);
        }
        if (city != null) {
            request.put("city", city);
        }
        if (stateOrProvince != null) {
            request.put("stateOrProvince", stateOrProvince);
        }
        if (postalCode != null) {
            request.put("postalCode", postalCode);
        }
        if (country != null) {
            request.put("country", country);
        }
        if (roles != null) {
            request.put("roles", roles);
        }
        return request;
    }

    public static String signRequest(SigaApiFlow flow, String request, String method, String url, String hmacAlgo) throws InvalidKeyException, NoSuchAlgorithmException {
        flow.setSigningTime(getSigningTimeInSeconds().toString());
        String signableString = flow.getServiceUuid() + ":" + flow.getSigningTime() + ":" + method + ":" + url + ":" + request;
        return generateHmacSignature(flow.getServiceSecret(), signableString, hmacAlgo);
    }

    private static Long getSigningTimeInSeconds() {
        return Instant.now().getEpochSecond();
    }

}
