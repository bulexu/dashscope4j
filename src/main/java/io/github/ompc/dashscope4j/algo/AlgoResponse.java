package io.github.ompc.dashscope4j.algo;

import io.github.ompc.dashscope4j.api.ApiResponse;

/**
 * 算法应答
 *
 * @param <D> 数据类型
 */
public interface AlgoResponse<D extends ApiResponse.Output> extends ApiResponse<D> {

}