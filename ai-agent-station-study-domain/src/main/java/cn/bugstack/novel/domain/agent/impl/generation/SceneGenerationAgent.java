package cn.bugstack.novel.domain.agent.impl.generation;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.Scene;
import cn.bugstack.novel.domain.service.rag.IRAGService;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 场景生成Agent
 * 根据章节梗概生成正文场景
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class SceneGenerationAgent extends AbstractAgent<ChapterOutline, Scene> {
    
    private final IRAGService ragService;
    
    public SceneGenerationAgent(IRAGService ragService) {
        super(AgentType.SCENE_GENERATION);
        this.ragService = ragService;
    }
    
    @Override
    protected Scene doExecute(ChapterOutline outline, NovelContext context) {
        log.info("开始生成场景，章节: {}", outline.getChapterTitle());
        
        // 从RAG检索相似风格文本
        var similarScenes = ragService.search(outline.getOutline(), "zh", 3);
        
        // 构建提示词（实际应该调用LLM）
        String prompt = buildPrompt(outline, similarScenes);
        
        // 调用LLM生成场景正文（这里简化处理）
        String content = generateContent(prompt);
        
        Scene scene = Scene.builder()
                .sceneId(UUID.randomUUID().toString())
                .sceneNumber(1)
                .sceneTitle(outline.getChapterTitle())
                .sceneType("对话") // 简化处理
                .content(content)
                .wordCount(content.length())
                .characters(outline.getKeyCharacters().toArray(new String[0]))
                .location("待确定")
                .build();
        
        log.info("场景生成完成，字数: {}", scene.getWordCount());
        return scene;
    }
    
    private String buildPrompt(ChapterOutline outline, java.util.List<IRAGService.SearchResult> similarScenes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("根据以下章节梗概生成场景正文：\n");
        prompt.append("章节标题：").append(outline.getChapterTitle()).append("\n");
        prompt.append("章节梗概：").append(outline.getOutline()).append("\n");
        prompt.append("关键人物：").append(String.join("、", outline.getKeyCharacters())).append("\n");
        
        if (!similarScenes.isEmpty()) {
            prompt.append("\n参考风格：\n");
            for (var result : similarScenes) {
                prompt.append(result.getContent().substring(0, Math.min(200, result.getContent().length())));
                prompt.append("\n");
            }
        }
        
        return prompt.toString();
    }
    
    private String generateContent(String prompt) {
        // 实际应该调用LLM生成
        // 这里返回模拟内容
        return "【这是模拟生成的场景正文，实际应该调用LLM生成】\n\n" +
                "林轩站在山巅，望着远方的云海，心中涌起一股豪情。\n" +
                "他深吸一口气，运转体内真元，准备突破下一个境界。\n" +
                "就在这时，远处传来一声长啸，一个身影快速接近...";
    }
    
}
