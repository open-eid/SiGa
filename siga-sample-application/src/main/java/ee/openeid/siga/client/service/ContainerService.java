package ee.openeid.siga.client.service;


import ee.openeid.siga.client.model.AsicContainerWrapper;
import ee.openeid.siga.client.model.HashcodeContainerWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@CacheConfig(cacheNames = "container")
public class ContainerService {

    @CachePut(key = "#id")
    public AsicContainerWrapper cacheAsicContainer(String id, String name, byte[] container) {
        return AsicContainerWrapper.builder()
                .id(id)
                .name(name)
                .container(container)
                .build();
    }

    @CachePut(key = "#id")
    public HashcodeContainerWrapper cacheHashcodeContainer(String id, String fileName, byte[] hashcodeContainer, Map<String, byte[]> originalDataFiles) {
        return HashcodeContainerWrapper.builder()
                .id(id)
                .fileName(fileName)
                .hashcodeContainer(hashcodeContainer)
                .originalDataFiles(originalDataFiles)
                .build();
    }

    @Cacheable(key = "#id")
    public HashcodeContainerWrapper getHashcodeContainer(String id) {
        log.info("Get file from cacheHashcodeContainer: {}", id);
        return new HashcodeContainerWrapper();
    }

    @Cacheable(key = "#id")
    public AsicContainerWrapper getAsicContainer(String id) {
        log.info("Get file from cacheAsicContainer: {}", id);
        return new AsicContainerWrapper();
    }
}
