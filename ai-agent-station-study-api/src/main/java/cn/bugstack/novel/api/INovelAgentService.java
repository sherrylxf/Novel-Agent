package cn.bugstack.novel.api;

import cn.bugstack.novel.api.dto.NovelGenerateRequestDTO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

/**
 * Novel Agent 服务接口
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public interface INovelAgentService {
    
    /**
     * 生成小说（流式输出）
     *
     * @param request 请求参数
     * @param response HTTP响应
     * @return ResponseBodyEmitter（SSE流式输出）
     */
    ResponseBodyEmitter generateNovel(NovelGenerateRequestDTO request, HttpServletResponse response);
    
}
