package ee.openeid.siga.test.utils;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static ee.openeid.siga.test.TestData.*;
import static ee.openeid.siga.test.TestData.DEFAULT_FILESIZE;
import static ee.openeid.siga.test.utils.HmacSigner.generateHmacSignature;

public class RequestBuilder {

    public static String hashcodeContainersDataDefault() throws JSONException {
        return hashcodeContainersData(DEFAULT_FILENAME, DEFAULT_SHA256_DATAFILE, DEFAULT_SHA512_DATAFILE, DEFAULT_FILESIZE);
    }

    public static String hashcodeContainersData(String fileName, String fileHashSha256, String fileHashSha512, String fileSize) throws JSONException {
        JSONArray datafiles = new JSONArray();
        JSONObject dataFileObject = new JSONObject();
        JSONObject request = new JSONObject();
        dataFileObject.put("fileName", fileName);
        dataFileObject.put("fileHashSha256", fileHashSha256);
        dataFileObject.put("fileHashSha512", fileHashSha512);
        dataFileObject.put("fileSize", fileSize);
        datafiles.put(dataFileObject);
        request.put("dataFiles", datafiles);
        return request.toString();
    }

    public static String hashcodeContainer(String containerName) throws JSONException, IOException {
        JSONObject request = new JSONObject();
        ClassLoader classLoader = RequestBuilder.class.getClassLoader();
        String path = classLoader.getResource(containerName).getPath().substring(1);
        String file = Base64.encodeBase64String(Files.readAllBytes(FileSystems.getDefault().getPath(path)));
        request.put("container", file);
        return request.toString();
    }


    public static String signRequest(String serviceSecret, String serviceUuid, Long timestamp, String request, String  hmacAlgo) throws InvalidKeyException, NoSuchAlgorithmException {
        String signableString = serviceUuid + ":" + timestamp + ":" + request;
        return generateHmacSignature(serviceSecret, signableString, hmacAlgo);
    }
}
