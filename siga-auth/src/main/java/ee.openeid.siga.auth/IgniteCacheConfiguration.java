package ee.openeid.siga.auth;

import lombok.experimental.FieldDefaults;
import org.apache.ignite.cache.spring.SpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static lombok.AccessLevel.PRIVATE;

@Configuration
@EnableCaching
@FieldDefaults(level = PRIVATE)
public class IgniteCacheConfiguration extends CachingConfigurerSupport {

    @Bean
    public CacheManager cacheManager() {
        final SpringCacheManager springCacheManager = new SpringCacheManager();
        springCacheManager.setIgniteInstanceName("siga-ignite");
        return springCacheManager;
    }
}
