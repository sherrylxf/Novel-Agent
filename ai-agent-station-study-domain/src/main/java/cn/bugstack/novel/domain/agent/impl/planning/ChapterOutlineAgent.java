package cn.bugstack.novel.domain.agent.impl.planning;

import cn.bugstack.novel.domain.agent.AbstractAgent;
import cn.bugstack.novel.domain.model.entity.ChapterOutline;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import cn.bugstack.novel.domain.model.entity.VolumePlan;
import cn.bugstack.novel.types.enums.AgentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 章节梗概Agent
 * 生成章节梗概，不写正文
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
@Component
public class ChapterOutlineAgent extends AbstractAgent<Object[], ChapterOutline> {
    
    public ChapterOutlineAgent() {
        super(AgentType.CHAPTER_OUTLINE);
    }
    
    @Override
    protected ChapterOutline doExecute(Object[] input, NovelContext context) {
        VolumePlan volumePlan = (VolumePlan) input[0];
        Integer chapterNumber = (Integer) input[1];
        
        log.info("开始生成章节梗概，章节序号: {}", chapterNumber);
        
        // 构建章节梗概（实际应该调用LLM生成）
        ChapterOutline outline = ChapterOutline.builder()
                .chapterId(UUID.randomUUID().toString())
                .chapterNumber(chapterNumber)
                .chapterTitle("第" + chapterNumber + "章")
                .outline(generateOutline(volumePlan, chapterNumber))
                .keyCharacters(generateKeyCharacters())
                .keyEvents(generateKeyEvents())
                .foreshadowing(new ArrayList<>())
                .scenes(new ArrayList<>())
                .build();
        
        log.info("章节梗概生成完成，章节ID: {}", outline.getChapterId());
        return outline;
    }
    
    private String generateOutline(VolumePlan volumePlan, int chapterNumber) {
        // 简化处理，实际应该调用LLM生成
        return String.format("第%d章梗概：主角在修炼过程中遇到挑战，通过努力克服困难，获得突破。", chapterNumber);
    }
    
    private List<String> generateKeyCharacters() {
        return Arrays.asList("林轩", "师父", "对手");
    }
    
    private List<String> generateKeyEvents() {
        return Arrays.asList("修炼突破", "遇到挑战", "获得机缘");
    }
    
}
