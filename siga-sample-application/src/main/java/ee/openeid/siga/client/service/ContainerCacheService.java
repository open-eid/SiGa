package ee.openeid.siga.client.service;

import ee.openeid.siga.client.model.Container;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@CacheConfig(cacheNames = "file")
public class ContainerCacheService {

    @CachePut(key = "#id")
    public Container cache(String id, String fileName, byte[] file) {
        return Container.builder().fileName(fileName).file(file).id(id).build();
    }

    @Cacheable(key = "#id")
    public Container get(String id) {
        log.info("Get file from cache: {}", id);
        return new Container();
    }

}
