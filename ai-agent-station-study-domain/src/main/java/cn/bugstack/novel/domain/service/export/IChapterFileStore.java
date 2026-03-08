package cn.bugstack.novel.domain.service.export;

/**
 * 章节文件导出（TXT）端口
 */
public interface IChapterFileStore {

    void writeChapterTxt(String novelId, Integer volumeNumber, Integer chapterNumber, String chapterTitle, String content);
}

