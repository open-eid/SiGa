package ee.openeid.siga.auth;

import ee.openeid.siga.auth.properties.SecurityConfigurationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheFactoryBean;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class EhCacheConfiguration extends CachingConfigurerSupport {

    @Autowired
    SecurityConfigurationProperties securityConfiguration;

    @Bean
    public EhCacheFactoryBean servicesCache() {
        EhCacheFactoryBean ehCacheFactory = new EhCacheFactoryBean();
        ehCacheFactory.setCacheManager(cacheManagerFactoryBean().getObject());
        ehCacheFactory.setCacheName("services");
        ehCacheFactory.setTimeToIdleSeconds(securityConfiguration.getEhcache().getTimeToIdle());
        ehCacheFactory.setTimeToLiveSeconds(securityConfiguration.getEhcache().getTimeToLive());
        return ehCacheFactory;
    }

    @Bean
    public CacheManager cacheManager() {
        return new EhCacheCacheManager(cacheManagerFactoryBean().getObject());
    }

    @Bean
    public EhCacheManagerFactoryBean cacheManagerFactoryBean() {
        return new EhCacheManagerFactoryBean();
    }
}
