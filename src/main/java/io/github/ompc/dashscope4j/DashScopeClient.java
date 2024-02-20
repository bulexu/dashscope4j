package io.github.ompc.dashscope4j;

import io.github.ompc.dashscope4j.chat.ChatRequest;
import io.github.ompc.dashscope4j.chat.ChatResponse;
import io.github.ompc.dashscope4j.internal.DashScopeClientImpl;
import io.github.ompc.dashscope4j.internal.util.Buildable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;


public interface DashScopeClient {

    /**
     * 对话
     *
     * @param request 对话请求
     * @return 操作
     */
    OpAsyncAndStream<ChatResponse> chat(ChatRequest request);

    /**
     * DashScope客户端构建器
     *
     * @return 构建器
     */
    static Builder newBuilder() {
        return new DashScopeClientImpl.Builder();
    }

    /**
     * DashScope客户端构建器
     */
    interface Builder extends Buildable<DashScopeClient, Builder> {

        /**
         * 设置SK
         *
         * @param sk SK
         * @return this
         */
        Builder sk(String sk);

        /**
         * 设置线程池
         *
         * @param executor 线程池
         * @return this
         */
        Builder executor(Executor executor);

        /**
         * 设置连接超时
         *
         * @param connectTimeout 连接超时
         * @return this
         */
        Builder connectTimeout(Duration connectTimeout);

    }

    /**
     * 异步和流式操作
     *
     * @param <R> 操作类型
     */
    interface OpAsyncAndStream<R> extends OpAsync<R> {

        /**
         * 流式操作
         * <ul>
         *     <li>块模式：将会消费最终应答</li>
         *     <li>流模式：将会消费每一个SSE产生的应答</li>
         * </ul>
         *
         * @param consumer 响应消费者
         * @return 操作结果
         */
        CompletableFuture<R> stream(Consumer<R> consumer);

    }

    /**
     * 异步操作
     *
     * @param <R>
     */
    interface OpAsync<R> {

        /**
         * 异步
         *
         * @return 操作结果
         */
        CompletableFuture<R> async();

    }
}
