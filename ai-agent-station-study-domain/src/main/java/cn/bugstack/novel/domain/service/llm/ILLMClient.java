package cn.bugstack.novel.domain.service.llm;

import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * LLM客户端服务接口
 * 封装LLM调用，提供统一的LLM调用接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public interface ILLMClient {
    
    /**
     * 同步调用LLM，返回完整响应
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return LLM响应内容
     */
    String call(String systemPrompt, String userPrompt);
    
    /**
     * 使用模板构建prompt并调用LLM
     *
     * @param systemPrompt 系统提示词
     * @param template     用户提示词模板
     * @param variables    模板变量
     * @return LLM响应内容
     */
    String callWithTemplate(String systemPrompt, String template, Map<String, Object> variables);
    
    /**
     * 流式调用LLM，返回Flux流
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return 流式响应Flux
     */
    Flux<String> stream(String systemPrompt, String userPrompt);
    
    /**
     * 使用模板构建prompt并流式调用LLM
     *
     * @param systemPrompt 系统提示词
     * @param template     用户提示词模板
     * @param variables    模板变量
     * @return 流式响应Flux
     */
    Flux<String> streamWithTemplate(String systemPrompt, String template, Map<String, Object> variables);
}
