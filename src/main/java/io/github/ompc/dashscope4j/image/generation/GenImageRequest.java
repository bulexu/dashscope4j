package io.github.ompc.dashscope4j.image.generation;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.ompc.dashscope4j.base.algo.AlgoRequest;
import io.github.ompc.internal.dashscope4j.image.generation.GenImageRequestBuilder;

/**
 * 文生图请求
 */
public interface GenImageRequest extends AlgoRequest<GenImageResponse> {

    /**
     * 构建文生图请求
     *
     * @return 构建器
     */
    static Builder newBuilder() {
        return new GenImageRequestBuilder();
    }

    /**
     * 文生图请求构建器
     */
    interface Builder extends AlgoRequest.Builder<GenImageModel, GenImageRequest, Builder> {

        /**
         * 正向提示
         *
         * @param prompt 正向提示
         * @return this
         */
        Builder prompt(String prompt);

        /**
         * 负向提示
         *
         * @param negative 负向提示
         * @return this
         */
        Builder negative(String negative);

    }

    /**
     * 图片风格
     */
    enum Style {
        @JsonProperty("<auto>")
        AUTO,
        @JsonProperty("<3d cartoon>")
        CARTOON_3D,
        @JsonProperty("<anime>")
        ANIME,
        @JsonProperty("<oil painting>")
        OIL_PAINTING,
        @JsonProperty("<watercolor>")
        WATERCOLOR,
        @JsonProperty("<sketch>")
        SKETCH,
        @JsonProperty("<chinese painting>")
        CHINESE_PAINTING,
        @JsonProperty("<flat illustration>")
        FLAT_ILLUSTRATION
    }

    /**
     * 图片尺寸
     */
    enum Size {
        @JsonProperty("1024*1024")
        S_1024_1024,
        @JsonProperty("720*1280")
        S_720_1280,
        @JsonProperty("1280*720")
        S_1280_720
    }

}
