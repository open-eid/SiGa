package ee.openeid.siga.test.utils;

import ee.openeid.siga.test.model.SigaApiFlow;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static ee.openeid.siga.test.helper.TestData.*;
import static ee.openeid.siga.test.utils.HmacSigner.generateHmacSignature;

public class RequestBuilder {

    public static JSONObject hashcodeContainersDataRequestWithDefault() throws JSONException {
        return hashcodeContainersDataRequest(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
    }

    public static JSONObject asicContainersDataRequestWithDefault() throws JSONException {
        return asicContainersDataRequest(DEFAULT_FILENAME, DEFAULT_DATAFILE_CONTENT, DEFAULT_ASICE_CONTAINER_NAME);
    }

    public static JSONObject asicContainersDataRequest(String fileName, String fileContent, String containerName) throws JSONException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        if (fileName != null)
            dataFileObject.put("fileName", fileName);
        if (fileContent != null)
            dataFileObject.put("fileContent", fileContent);
        datafiles.put(dataFileObject);
        if (containerName != null)
            request.put("containerName", containerName);
        request.put("dataFiles", datafiles);
        return request;
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

        File file = new File(classLoader.getResource("hashcode/" + containerName).getFile());

        String fileBase64 = Base64.encodeBase64String(Files.readAllBytes(file.toPath()));
        request.put("container", fileBase64);
        return request;
    }

    public static JSONObject asicContainerRequestFromFile(String containerName) throws JSONException, IOException {
        JSONObject request = new JSONObject();
        ClassLoader classLoader = RequestBuilder.class.getClassLoader();

        File file = new File(classLoader.getResource("asic/" + containerName).getFile());
        String fileBase64 = Base64.encodeBase64String(Files.readAllBytes(file.toPath()));
        request.put("container", fileBase64);
        request.put("containerName", containerName);
        return request;
    }

    public static JSONObject hashcodeContainerRequest(String containerBase64) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("container", containerBase64);
        return request;
    }

    public static JSONObject asicContainerRequest(String containerBase64, String containerName) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("container", containerBase64);
        request.put("containerName", containerName);
        return request;
    }

    public static JSONObject remoteSigningRequestWithDefault(String signingCertificate, String signatureProfile) throws JSONException {
        return remoteSigningRequest(signingCertificate, signatureProfile, null, null, null, null, null);
    }

    public static JSONObject remoteSigningRequest(String signingCertificate, String signatureProfile, String roles, String city, String stateOrProvince, String postalCode, String country) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("signingCertificate", signingCertificate);
        request.put("signatureProfile", signatureProfile);

        if (roles != null) {
            JSONArray rolesArray = new JSONArray();
            rolesArray.put(roles);
            request.put("roles", rolesArray);
        }

        if (city != null || stateOrProvince != null || postalCode != null || country != null) {
            request.put("signatureProductionPlace", buildSignatureProductionPlace(city, stateOrProvince, postalCode, country));
        }

        return request;
    }

    public static JSONObject remoteSigningSignatureValueRequest(String signatureValue) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("signatureValue", signatureValue);
        return request;
    }

    public static JSONObject midSigningRequestWithDefault(String personIdentifier, String phoneNo, String signatureProfile) throws JSONException {
        return midSigningRequest(personIdentifier, phoneNo, "EST", signatureProfile, "something", null, null, null, null, null);
    }

    public static JSONObject midSigningRequest(String personIdentifier, String phoneNo, String language, String signatureProfile, String messageToDisplay, String city, String stateOrProvince, String postalCode, String country, String roles) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", personIdentifier);
        request.put("phoneNo", phoneNo);
        request.put("language", language);
        request.put("signatureProfile", signatureProfile);

        if (messageToDisplay != null) {
            request.put("messageToDisplay", messageToDisplay);
        }

        if (roles != null) {
            JSONArray rolesArray = new JSONArray();
            rolesArray.put(roles);
            request.put("roles", rolesArray);
        }

        if (city != null || stateOrProvince != null || postalCode != null || country != null) {
            request.put("signatureProductionPlace", buildSignatureProductionPlace(city, stateOrProvince, postalCode, country));
        }
        return request;
    }

    public static JSONObject smartIdSigningRequestWithDefault(String personIdentifier, String signatureProfile) throws JSONException {
        return smartIdSigningRequest(personIdentifier, "EE", signatureProfile, "something", null, null, null, null, null);
    }

    public static JSONObject smartIdSigningRequest(String personIdentifier, String originCountry, String signatureProfile, String messageToDisplay, String city, String stateOrProvince, String postalCode, String country, String roles) throws JSONException {
        JSONObject request = new JSONObject();
        request.put("personIdentifier", personIdentifier);
        request.put("country", originCountry);
        request.put("signatureProfile", signatureProfile);

        if (messageToDisplay != null) {
            request.put("messageToDisplay", messageToDisplay);
        }

        if (roles != null) {
            JSONArray rolesArray = new JSONArray();
            rolesArray.put(roles);
            request.put("roles", rolesArray);
        }

        if (city != null || stateOrProvince != null || postalCode != null || country != null) {
            request.put("signatureProductionPlace", buildSignatureProductionPlace(city, stateOrProvince, postalCode, country));
        }
        return request;
    }

    public static JSONObject buildSignatureProductionPlace(String city, String stateOrProvince, String postalCode, String country) throws JSONException {
        JSONObject signatureProductionPlace = new JSONObject();
        if (city != null) {
            signatureProductionPlace.put("city", city);
        }
        if (stateOrProvince != null) {
            signatureProductionPlace.put("stateOrProvince", stateOrProvince);
        }
        if (postalCode != null) {
            signatureProductionPlace.put("postalCode", postalCode);
        }
        if (country != null) {
            signatureProductionPlace.put("countryName", country);
        }
        return signatureProductionPlace;
    }

    public static String signRequest(SigaApiFlow flow, String request, String method, String url) throws InvalidKeyException, NoSuchAlgorithmException {
        if (!flow.getForceSigningTime()) {
            flow.setSigningTime(getSigningTimeInSeconds().toString());
        }

        String signableString = flow.getServiceUuid() + ":" + flow.getSigningTime() + ":" + method + ":" + url + ":" + request;
        return generateHmacSignature(flow.getServiceSecret(), signableString, flow.getHmacAlgorithm());
    }

    private static Long getSigningTimeInSeconds() {
        return Instant.now().getEpochSecond();
    }

}
