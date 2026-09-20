package team.codingforest.moyeota.chat.infrastructure.search;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.*;
import team.codingforest.moyeota.chat.domain.search.MessageSearchQuery;
import team.codingforest.moyeota.chat.domain.exception.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ElasticsearchMessageIndexTest {
    @Test void 시간초과나_일부샤드_실패를_정상_빈결과로_반환하지_않는다() {
        for (String response : new String[]{"{\"timed_out\":true,\"hits\":{\"hits\":[]}}",
                "{\"_shards\":{\"failed\":1},\"hits\":{\"hits\":[]}}", "{}"}) {
            var builder = RestClient.builder().baseUrl("http://localhost:19200");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(requestTo("http://localhost:19200/chat-test")).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
            server.expect(requestTo("http://localhost:19200/chat-test/_search")).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            var index = new ElasticsearchMessageIndex(builder.build(), SearchOutboxWorkerTest.properties());
            assertThatThrownBy(() -> index.search(new MessageSearchQuery(1L, "출구", null, null, null, null, 10)))
                    .isInstanceOf(ChatException.class).extracting("errorCode").isEqualTo(ChatErrorCode.CHAT_SEARCH_UNAVAILABLE);
            server.verify();
        }
    }
}
