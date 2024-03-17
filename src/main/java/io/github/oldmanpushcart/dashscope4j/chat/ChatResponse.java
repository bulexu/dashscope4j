package io.github.oldmanpushcart.dashscope4j.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.oldmanpushcart.dashscope4j.base.algo.AlgoResponse;
import io.github.oldmanpushcart.dashscope4j.base.api.ApiResponse;
import io.github.oldmanpushcart.dashscope4j.chat.message.Message;

import java.util.List;

/**
 * 对话应答
 */
public interface ChatResponse extends AlgoResponse<ChatResponse.Output> {

    /**
     * 对话应答数据
     */
    interface Output extends ApiResponse.Output {

        /**
         * 获取应答选择
         *
         * @return 应答选择
         */
        List<Choice> choices();

        /**
         * 获取最好的选择
         * <p>如果返回的{@link Choice}集合为空，则返回null</p>
         *
         * @return 最好的选择
         */
        Choice best();

    }

    /**
     * 结束标识
     */
    enum Finish {

        /**
         * 正常结束
         */
        @JsonProperty("stop")
        NORMAL(0),

        /**
         * 工具调用
         * @since 1.2.0
         */
        @JsonProperty("tool_calls")
        TOOL_CALLS(1),

        /**
         * 截断结束
         */
        @JsonProperty("length")
        OVERFLOW(2),

        /**
         * 尚未结束
         * <p>
         * 用于标识尚未结束，常见于开启了SSE的场景
         * </p>
         */
        @JsonProperty("null")
        NONE(100);

        private final int weight;

        Finish(int weight) {
            this.weight = weight;
        }

    }

    /**
     * 应答选择
     */
    interface Choice extends Comparable<Choice> {

        /**
         * 获取应答结束标识
         *
         * @return 响应结束标识
         */
        Finish finish();

        /**
         * 获取应答消息
         *
         * @return 应答消息
         */
        Message message();

        @Override
        default int compareTo(Choice o) {
            return Integer.compare(finish().weight, o.finish().weight);
        }

    }

}
