package ee.openeid.siga.auth;

import org.apache.ignite.cache.spring.SpringCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class IgniteCacheConfiguration extends CachingConfigurerSupport {

    @Bean
    @Override
    public CacheManager cacheManager() {
        final SpringCacheManager springCacheManager = new SpringCacheManager();
        springCacheManager.setIgniteInstanceName("siga-ignite");
        return springCacheManager;
    }
}
