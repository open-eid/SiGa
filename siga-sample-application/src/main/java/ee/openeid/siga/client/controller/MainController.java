package ee.openeid.siga.client.controller;

import ee.openeid.siga.client.hashcode.HashcodeContainer;
import ee.openeid.siga.client.model.AsicContainerWrapper;
import ee.openeid.siga.client.model.HashcodeContainerWrapper;
import ee.openeid.siga.client.model.MobileSigningRequest;
import ee.openeid.siga.client.service.ContainerService;
import ee.openeid.siga.client.service.SigaApiClientService;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
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

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String startPage(final Model model) {
        return START_PAGE_VIEW_NAME;
    }

    @RequestMapping(value = "/convert-container", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public HashcodeContainerWrapper convertContainerToHashcodeContainer(MultipartHttpServletRequest request) throws IOException {
        Map<String, MultipartFile> fileMap = request.getFileMap();
        log.info("Nr of files uploaded: {}", fileMap.size());
        MultipartFile file = fileMap.entrySet().iterator().next().getValue();
        log.info("Converting container: {}", file.getOriginalFilename());
        InputStream inputStream = new ByteArrayInputStream(file.getBytes());
        HashcodeContainer hashcodeContainer = HashcodeContainer.fromRegularContainerBuilder().containerInputStream(inputStream).build();
        String id = UUID.randomUUID().toString();
        log.info("Generated file id: {}", id);
        return containerService.cacheHashcodeContainer(id, file.getOriginalFilename(), hashcodeContainer);
    }

    @RequestMapping(value = "/create-hashcode-container", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @SneakyThrows
    public HashcodeContainerWrapper createHashcodeContainerFromFiles(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> fileMap = request.getFileMap();
        log.info("Nr of files uploaded: {}", fileMap.size());
        return sigaApiClientService.createHashcodeContainer(fileMap.values());
    }

    @RequestMapping(value = "/create-container", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    @SneakyThrows
    public AsicContainerWrapper createContainerFromFiles(MultipartHttpServletRequest request) {
        Map<String, MultipartFile> fileMap = request.getFileMap();
        log.info("Nr of files uploaded: {}", fileMap.size());
        return sigaApiClientService.createAsicContainer(fileMap.values());
    }

    @GetMapping(value = "/download/hashcode/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity downloadHashcodeContainer(@PathVariable("id") String id) {
        HashcodeContainerWrapper cachedFile = containerService.getHashcodeContainer(id);
        log.info("Downloading hashcode container {} with id {}", cachedFile.getFileName(), cachedFile.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + cachedFile.getFileName() + "\"")
                .body(cachedFile.getHashcodeContainer());
    }

    @GetMapping(value = "/download/regular/{type}/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public ResponseEntity downloadRegularContainer(@PathVariable("type") String type, @PathVariable("id") String id) {
        String fileName;
        byte[] container;
        if ("ASIC".equals(type)) {
            AsicContainerWrapper cachedFile = containerService.getAsicContainer(id);
            container = cachedFile.getContainer();
            fileName = cachedFile.getName();
        } else {
            HashcodeContainerWrapper cachedFile = containerService.getHashcodeContainer(id);
            log.info("Downloading regular container {} with id {}", cachedFile.getFileName(), cachedFile.getId());

            HashcodeContainer hashcodeContainer = HashcodeContainer.fromHashcodeContainerBuilder()
                    .base64Container(Base64.getEncoder().encodeToString(cachedFile.getHashcodeContainer()))
                    .regularDataFiles(cachedFile.getOriginalDataFiles())
                    .build();
            container = hashcodeContainer.getRegularContainer();
            fileName = cachedFile.getFileName();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(container);
    }

    @RequestMapping(value = "/mobile-signing", method = RequestMethod.POST, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public MobileSigningRequest startMobileSigning(@RequestBody MobileSigningRequest request) {
        log.info("Mobile signing request: {}", request);
        sigaApiClientService.startMobileSigningFlow(request);
        return request;
    }
}
