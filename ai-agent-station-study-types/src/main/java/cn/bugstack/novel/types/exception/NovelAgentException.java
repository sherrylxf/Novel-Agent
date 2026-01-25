package cn.bugstack.novel.types.exception;

/**
 * Novel Agent异常
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
public class NovelAgentException extends RuntimeException {
    
    public NovelAgentException(String message) {
        super(message);
    }
    
    public NovelAgentException(String message, Throwable cause) {
        super(message, cause);
    }
    
}
