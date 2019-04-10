package ee.openeid.siga.test.statistics;

import ee.openeid.siga.common.event.SigaEvent;
import ee.openeid.siga.common.event.SigaEventName;
import ee.openeid.siga.test.TestBase;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.junit.BeforeClass;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

import static ee.openeid.siga.common.event.SigaEvent.EventResultType.SUCCESS;
import static ee.openeid.siga.common.event.SigaEvent.EventType.FINISH;
import static java.lang.Long.parseLong;
import static java.util.Arrays.stream;
import static org.elasticsearch.action.search.SearchType.QUERY_THEN_FETCH;
import static org.elasticsearch.index.query.QueryBuilders.*;
import static org.junit.Assert.assertEquals;

public abstract class StatisticsBaseT extends TestBase {
    protected static final long ELASTIC_QUERY_TIMEOUT;
    protected static final long ELASTIC_QUERY_POLL_INTERVAL;
    protected static int NR_OF_CONTAINERS_GENERATED;
    protected static TransportClient elasticClient;
    protected static List<String> containerIds = new ArrayList<>();
    protected static long statisticsStartingFromTimestamp;

    static {
        ELASTIC_QUERY_TIMEOUT = parseLong(properties.getProperty("siga.elastic.query-timeout-seconds"));
        ELASTIC_QUERY_POLL_INTERVAL = parseLong(properties.getProperty("siga.elastic.query-poll-interval-seconds"));
    }

    @BeforeClass
    public static void setup() throws UnknownHostException {
        String elasticHost = properties.getProperty("siga.elastic.host");
        String elasticTransportPort = properties.getProperty("siga.elastic.transport-port");
        String elasticClusterName = properties.getProperty("siga.elastic.cluster-name");
        elasticClient = new PreBuiltTransportClient(Settings.builder()
                .put("cluster.name", elasticClusterName)
                .build())
                .addTransportAddress(new TransportAddress(InetAddress.getByName(elasticHost), Integer.valueOf(elasticTransportPort)));
        statisticsStartingFromTimestamp = Instant.now().toEpochMilli();
    }

    protected boolean checkSearchResponse(SearchResponse response) {
        assertEquals(NR_OF_CONTAINERS_GENERATED, response.getHits().totalHits);
        assertEquals(NR_OF_CONTAINERS_GENERATED, countMatchingContainerIds(response));
        return true;
    }

    protected long countMatchingContainerIds(SearchResponse response) {
        return stream(response.getHits().getHits())
                .map(h -> ((HashMap<String, String>) h.getSourceAsMap().get("stats")).get("container_id"))
                .filter(h -> containerIds.contains(h)).count();
    }

    protected long countMatchingParametersWithAnyValue(SearchResponse response, SigaEventName.EventParam eventParam) {
        return countMatchingParameters(response, eventParam, null);
    }

    protected long countMatchingParameters(SearchResponse response, SigaEventName.EventParam eventParam, String value) {
        Stream s = stream(response.getHits().getHits()).map(h -> ((HashMap<String, String>) h.getSourceAsMap().get("stats")).get(eventParam.name().toLowerCase()));
        if (value != null) {
            return s.filter(h -> value.equals(h)).count();
        } else {
            return s.count();
        }
    }

    protected SearchRequestBuilder prepareSearchRequestFor(QueryBuilder query) {
        return elasticClient.prepareSearch()
                .setSearchType(QUERY_THEN_FETCH)
                .setPostFilter(query)
                .addSort("stats.timestamp", SortOrder.ASC);
    }

    protected QueryBuilder createQueryForSuccessEvent(SigaEventName eventName, ImmutablePair<String, String>... matchProperty) {
        return createQueryForSuccessEvent(eventName, FINISH, matchProperty);
    }

    protected QueryBuilder createQueryForSuccessEvent(SigaEventName eventName, SigaEvent.EventType eventType, ImmutablePair<String, String>... matchProperty) {
        BoolQueryBuilder bqb = QueryBuilders.boolQuery()
                .must(termsQuery("tags", "siga", "stats"))
                .must(matchQuery("stats.event_name", eventName.name()))
                .must(matchQuery("stats.event_type", eventType.name()))
                .must(rangeQuery("stats.timestamp").from(statisticsStartingFromTimestamp));

        if (FINISH.equals(eventType)) {
            bqb.must(matchQuery("stats.result", SUCCESS.name()));
        }
        for (ImmutablePair<String, String> p : matchProperty) {
            bqb.must(matchQuery(p.getKey(), p.getValue()));
        }
        return bqb;
    }
}
