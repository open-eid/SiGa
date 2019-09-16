package ee.openeid.siga.service.signature.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.digidoc4j.exceptions.DigiDoc4JException;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class DigiDoc4jTSLRefreshSchedulingConfiguration {

    private final org.digidoc4j.Configuration configuration;

    @Scheduled(cron = "${siga.dd4j.tsl-refresh-job-cron}")
    public void refreshConfigurationTSL() {
        try {
            configuration.getTSL().refresh();
        } catch (DigiDoc4JException e) {
            log.error("Failed to refresh TSL", e);
        }
    }

    @PostConstruct
    public void initialConfigurationTSLRefresh() {
        refreshConfigurationTSL();
    }
}
