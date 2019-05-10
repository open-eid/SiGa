package ee.openeid.siga.client.service;

import ee.openeid.siga.client.hashcode.DetachedDataFileContainer;
import ee.openeid.siga.client.model.Container;
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
    public Container cache(String id, String fileName, byte[] hashcodeContainer, Map<String, byte[]> originalDataFiles) {
        return Container.builder()
                .id(id)
                .fileName(fileName)
                .hashcodeContainer(hashcodeContainer)
                .originalDataFiles(originalDataFiles)
                .build();
    }

    @CachePut(key = "#id")
    public Container cache(String id, String fileName, DetachedDataFileContainer hashcodeContainer) {
        return Container.builder()
                .id(id)
                .fileName(fileName)
                .hashcodeContainer(hashcodeContainer.getHashcodeContainer())
                .originalDataFiles(hashcodeContainer.getRegularDataFiles())
                .build();
    }

    @Cacheable(key = "#id")
    public Container get(String id) {
        log.info("Get file from cache: {}", id);
        return new Container();
    }
}
