package io.github.ompc.dashscope4j.internal.algo;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ompc.dashscope4j.Model;
import io.github.ompc.dashscope4j.Option;
import io.github.ompc.dashscope4j.algo.AlgoRequest;
import io.github.ompc.dashscope4j.algo.AlgoResponse;
import io.github.ompc.dashscope4j.internal.util.JacksonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.function.Function;

import static io.github.ompc.dashscope4j.internal.api.http.HttpHeader.ContentType.MIME_APPLICATION_JSON;
import static io.github.ompc.dashscope4j.internal.api.http.HttpHeader.HEADER_CONTENT_TYPE;

public abstract class AlgoRequestImpl<R extends AlgoResponse<?>> implements AlgoRequest<R> {

    private final static ObjectMapper mapper = JacksonUtils.mapper();
    private final static Logger logger = LoggerFactory.getLogger(AlgoRequestImpl.class);

    private final Model model;
    private final Object input;
    private final Option option;
    private final Duration timeout;
    private final Class<? extends R> responseType;

    protected AlgoRequestImpl(Model model, Object input, Option option, Duration timeout, Class<? extends R> responseType) {
        this.model = model;
        this.input = input;
        this.option = option;
        this.timeout = timeout;
        this.responseType = responseType;
    }

    @Override
    public HttpRequest newHttpRequest() {
        final var body = JacksonUtils.toJson(mapper, this);
        logger.debug("{}/{} => {}", this, model().name(), body);
        return HttpRequest.newBuilder()
                .uri(model().remote())
                .header(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    @Override
    public Function<String, R> responseDeserializer() {
        return body -> {
            logger.debug("{}/{} <= {}", this, model().name(), body);
            return JacksonUtils.toObject(mapper, body, responseType);
        };
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public Object input() {
        return input;
    }

    @Override
    public Option option() {
        return option;
    }

    @Override
    public Duration timeout() {
        return timeout;
    }

}
