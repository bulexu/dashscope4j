package io.github.ompc.dashscope4j.internal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ompc.dashscope4j.Task;
import io.github.ompc.dashscope4j.internal.api.http.HttpSsEventProcessor;
import io.github.ompc.dashscope4j.internal.task.*;
import io.github.ompc.dashscope4j.internal.util.JacksonUtils;
import io.github.ompc.dashscope4j.util.TransformFlowProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.ompc.dashscope4j.internal.api.http.HttpHeader.*;
import static io.github.ompc.dashscope4j.internal.api.http.HttpHeader.ContentType.MIME_TEXT_EVENT_STREAM;
import static java.util.function.Function.identity;

/**
 * API执行器
 */
public class ApiExecutor {

    private static final ObjectMapper mapper = JacksonUtils.mapper();
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final String sk;
    private final HttpClient http;
    private final Executor executor;

    /**
     * 构造API执行器
     *
     * @param sk       SK
     * @param http     HTTP客户端
     * @param executor 线程池
     */
    public ApiExecutor(String sk, HttpClient http, Executor executor) {
        this.sk = sk;
        this.http = http;
        this.executor = executor;
    }

    // 委派API请求
    private static HttpRequest delegateHttpRequest(HttpRequest request, Consumer<HttpRequest.Builder> consumer) {
        final var builder = HttpRequest.newBuilder(request, (k, v) -> true);
        consumer.accept(builder);
        return builder.build();
    }

    /**
     * 异步执行API请求
     *
     * @param request 请求
     * @return 异步应答
     */
    public <R extends ApiResponse<?>> CompletableFuture<R> async(ApiRequest<R> request) {
        final var delegateHttpRequest = delegateHttpRequest(request.newHttpRequest(), builder -> {
            builder.header(HEADER_AUTHORIZATION, "Bearer %s".formatted(sk));
            builder.header(HEADER_X_DASHSCOPE_SSE, "disable");
        });
        return http.sendAsync(delegateHttpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(identity(), executor)
                .thenApply(httpResponse -> {
                    final var response = request.responseDeserializer().apply(httpResponse.body());
                    if (!response.ret().isSuccess()) {
                        throw new ApiException(httpResponse.statusCode(), response);
                    }
                    return response;
                });
    }

    /**
     * 流式处理API请求
     *
     * @param request 请求
     * @return 流式应答
     */
    public <R extends ApiResponse<?>> CompletableFuture<Flow.Publisher<R>> flow(ApiRequest<R> request) {
        final var delegateHttpRequest = delegateHttpRequest(request.newHttpRequest(), builder -> {
            builder.header(HEADER_AUTHORIZATION, "Bearer %s".formatted(sk));
            builder.header(HEADER_X_DASHSCOPE_SSE, "enable");
        });
        return http.sendAsync(delegateHttpRequest, HttpResponse.BodyHandlers.ofPublisher())
                .thenApplyAsync(identity(), executor)

                // 从HTTP响应数据流转换为SSE事件流
                .thenApply(httpResponse -> {

                            // 解析CT
                            final var ct = ContentType.parse(httpResponse.headers());

                            // 检查是否为SSE
                            if (!MIME_TEXT_EVENT_STREAM.equals(ct.mime())) {
                                throw new IllegalStateException("Illegal HTTP Content-Type! expect:%s, actual:%s".formatted(
                                        MIME_TEXT_EVENT_STREAM,
                                        ct.mime()
                                ));
                            }

                            // 开始处理SSE事件流
                            return HttpSsEventProcessor
                                    .fromByteBuffers(ContentType.parse(httpResponse.headers()).charset(), 10240)
                                    .transform(httpResponse.body());

                        }
                )

                // 从SSE事件流中转换为API应答流
                .thenApply(ssePublisher -> TransformFlowProcessor.transform(ssePublisher, event -> {
                    final var responses = new ArrayList<R>();
                    switch (event.type()) {
                        // 异常事件，直接抛出异常
                        case "error" -> throw new ApiException(
                                // 解析HTTP状态：HTTP_STATUS/429
                                event.meta().stream()
                                        .filter(meta -> meta.startsWith("HTTP_STATUS/"))
                                        .map(meta -> Integer.parseInt(meta.substring("HTTP_STATUS/".length())))
                                        .findFirst()
                                        .orElse(200),
                                // 解析应答
                                request.responseDeserializer().apply(event.data())
                        );
                        // 数据事件，处理数据
                        case "result" -> request.responseDeserializer().apply(event.data());
                        // 未知事件，抛出异常
                        default -> throw new RuntimeException("Unsupported event type: %s".formatted(event.type()));
                    }
                    return responses;
                }));
    }

    /**
     * 任务式处理API请求
     *
     * @param request 请求
     * @param <R>     应答类型
     * @return 任务应答
     */
    public <R extends ApiResponse<?>> CompletableFuture<Task.Half<R>> task(ApiRequest<R> request) {
        final var delegateHttpRequest = delegateHttpRequest(request.newHttpRequest(), builder -> {
            builder.header(HEADER_AUTHORIZATION, "Bearer %s".formatted(sk));
            builder.header(HEADER_X_DASHSCOPE_SSE, "disable");
            builder.header(HEADER_X_DASHSCOPE_ASYNC, "enable");
        });
        return http.sendAsync(delegateHttpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(identity(), executor)

                // 解析HTTP响应为任务半应答
                .thenApply(httpResponse -> {
                    final TaskHalfResponse response = JacksonUtils.toObject(mapper, httpResponse.body(), TaskHalfResponse.class);
                    if (!response.ret().isSuccess()) {
                        throw new ApiException(httpResponse.statusCode(), response);
                    }
                    return response;
                })

                // 任务滚动执行直至完成
                .thenApply(response -> strategy -> rollingTask(
                        new TaskGetRequest.Builder()
                                .taskId(response.output().taskId())
                                .building(builder -> Optional.ofNullable(request.timeout()).ifPresent(builder::timeout))
                                .build(),
                        strategy,
                        request.responseDeserializer()
                ));
    }

    /**
     * 滚动任务执行，直至完结（成功、取消、失败）
     *
     * @param request  获取任务请求
     * @param strategy 滚动等待策略
     * @param finisher 任务结束处理器
     * @param <R>      应答类型
     * @return 任务应答
     */
    private <R> CompletableFuture<R> rollingTask(TaskGetRequest request, Task.WaitStrategy strategy, Function<String, R> finisher) {
        return _rollingTask(request, strategy)
                .thenApply(response -> finisher.apply(response.output().body()));
    }

    // 滚动任务执行，直至完结（成功、取消、失败）
    private CompletableFuture<TaskGetResponse> _rollingTask(TaskGetRequest taskGetRequest, Task.WaitStrategy strategy) {
        return async(taskGetRequest)
                .thenCompose(taskGetResponse -> {

                    // 获取任务
                    final var task = taskGetResponse.output().task();

                    // 任务取消
                    if (task.status() == Task.Status.CANCELED) {
                        throw new TaskException.TaskCancelledException(task.id());
                    }

                    // 任务失败
                    if (task.status() == Task.Status.FAILED) {
                        throw new TaskException.TaskFailedException(task.id(), taskGetResponse.ret());
                    }

                    // 任务成功
                    if (task.status() == Task.Status.SUCCEEDED) {
                        return CompletableFuture.completedFuture(taskGetResponse);
                    }

                    // 任务继续
                    return strategy.until(task)

                            // 失败则取消任务
                            .whenComplete((v, ex) -> {
                                if (Objects.nonNull(ex)) {
                                    final var taskCancelRequest = new TaskCancelRequest.Builder()
                                            .taskId(task.id())
                                            .building(builder -> Optional.ofNullable(taskGetRequest.timeout()).ifPresent(builder::timeout))
                                            .build();
                                    async(taskCancelRequest)
                                            .whenComplete((cv, cex) -> logger.warn("dashscope://task/cancel completed: task={};", task.id(), cex));
                                }
                            })

                            // 继续轮询
                            .thenCompose(unused -> _rollingTask(taskGetRequest, strategy));
                });
    }

}
