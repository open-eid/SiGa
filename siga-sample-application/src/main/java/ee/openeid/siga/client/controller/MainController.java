package ee.openeid.siga.client.controller;

import ee.openeid.siga.client.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.client.hashcode.util.ContainerUtil;
import ee.openeid.siga.client.model.Container;
import ee.openeid.siga.client.model.MobileSigningRequest;
import ee.openeid.siga.client.service.ContainerService;
import ee.openeid.siga.client.service.SigaApiClientService;
import ee.openeid.siga.webapp.json.HashcodeDataFile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
public class MainController {
    private static final String START_PAGE_VIEW_NAME = "index";

    @Autowired
    private SigaApiClientService sigaApiClientService;

    @Autowired
    private ContainerService containerService;

    @RequestMapping("/")
    public String startPage(final Model model) {
        return START_PAGE_VIEW_NAME;
    }

    @RequestMapping(value = "/convert-container", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public Container convertContainerToHashcodeContainer(MultipartHttpServletRequest request) throws IOException {
        Map<String, MultipartFile> fileMap = request.getFileMap();
        log.info("Nr of files uploaded: {}", fileMap.size());
        MultipartFile file = fileMap.entrySet().iterator().next().getValue();
        log.info("Converting container: {}", file.getOriginalFilename());
        DetachedDataFileContainer hashcodeContainer = new DetachedDataFileContainer();
        InputStream inputStream = new ByteArrayInputStream(file.getBytes());
        hashcodeContainer.open(inputStream);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        hashcodeContainer.save(outputStream);
        String id = UUID.randomUUID().toString();
        log.info("Generated file id: {}", id);
        return containerService.cache(id, file.getOriginalFilename(), outputStream.toByteArray());
    }

    @RequestMapping(value = "/create-container", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @SneakyThrows
    public Container createHashcodeContainerFromFiles(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> fileMap = request.getFileMap();
        log.info("Nr of files uploaded: {}", fileMap.size());
        List<HashcodeDataFile> dataFiles = new ArrayList<>();
        for (MultipartFile file : fileMap.values()) {
            log.info("Processing file: {}", file.getOriginalFilename());
            dataFiles.add(ContainerUtil.createHashcodeDataFile(file.getOriginalFilename(), file.getSize(), file.getBytes()));
        }
        return sigaApiClientService.createContainer(dataFiles);
    }

    @GetMapping(value = "/download/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity downloadContainer(@PathVariable("id") String id) {
        Container cachedFile = containerService.get(id);
        log.info("Downloading file {} with id {}", cachedFile.getFileName(), cachedFile.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cachedFile.getFileName() + "\"")
                .body(cachedFile.getFile());
    }

    @RequestMapping(value = "/mobile-signing", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public MobileSigningRequest startMobileSigning(@RequestBody MobileSigningRequest request) {
        log.info("Mobile signing request: {}", request);
        sigaApiClientService.startMobileSigningFlow(request);
        return request;
    }
}
