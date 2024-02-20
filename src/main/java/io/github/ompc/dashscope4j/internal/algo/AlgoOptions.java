package io.github.ompc.dashscope4j.internal.algo;

import io.github.ompc.dashscope4j.Option;
import io.github.ompc.dashscope4j.internal.api.ApiOptions;

public interface AlgoOptions extends ApiOptions {

    /**
     * ENABLE_INCREMENTAL_OUTPUT
     * <p>启用增量输出</p>
     * <p>开启增量输出模式，后面输出不会包含已经输出的内容，您需要自行拼接整体输出。</p>
     */
    Option.SimpleOpt<Boolean> ENABLE_INCREMENTAL_OUTPUT = new Option.SimpleOpt<>("incremental_output", Boolean.class);

}