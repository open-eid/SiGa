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
import static ee.openeid.siga.test.TestData.DEFAULT_FILESIZE;
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

    public static JSONObject hashcodeContainerRequest(String containerName) throws JSONException, IOException {
        JSONObject request = new JSONObject();
        ClassLoader classLoader = RequestBuilder.class.getClassLoader();
        String path = classLoader.getResource(containerName).getPath().substring(1);
        String file = Base64.encodeBase64String(Files.readAllBytes(FileSystems.getDefault().getPath(path)));
        request.put("container", file);
        return request;
    }

    public static JSONObject hashcodeRemoteSigningRequestWithDefault(String signingCertificate, String signatureProfile) throws JSONException {
        return hashcodeRemoteSigningRequest(signingCertificate, signatureProfile, null, null, null, null, null);
    }

    public static JSONObject hashcodeRemoteSigningRequest(String signingCertificate, String signatureProfile, String city, String stateOrProvince, String postalCode, String country, String roles) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("signingCertificate", signingCertificate);
        request.put("signatureProfile", signatureProfile);
        if (city != null){
            request.put("city", city);
        }
        if (stateOrProvince != null){
            request.put("stateOrProvince", stateOrProvince);
        }
        if (postalCode != null){
            request.put("postalCode", postalCode);
        }
        if (country != null){
            request.put("country", country);
        }
        if (roles != null){
            request.put("roles", roles);
        }
        return request;
    }

    public static JSONObject hashcodeRemoteSigningSignatureValueRequest(String signatureValue) throws JSONException, IOException {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);
        return request;
    }

    public static String signRequest(SigaApiFlow flow, String request, String hmacAlgo) throws InvalidKeyException, NoSuchAlgorithmException {
        flow.setSigningTime(getSigningTimeInSeconds().toString());
        String signableString = flow.getServiceUuid() + ":" + flow.getSigningTime() + ":" + request;
        return generateHmacSignature(flow.getServiceSecret(), signableString, hmacAlgo);
    }

    private static Long getSigningTimeInSeconds (){
        return  Instant.now().getEpochSecond();
    }

}
