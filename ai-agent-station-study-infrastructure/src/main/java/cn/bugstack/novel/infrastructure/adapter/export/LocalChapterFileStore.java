package cn.bugstack.novel.infrastructure.adapter.export;

import cn.bugstack.novel.domain.service.export.IChapterFileStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 将章节导出为本地 txt 文件
 */
@Slf4j
@Service
public class LocalChapterFileStore implements IChapterFileStore {

    private final String baseDir;

    public LocalChapterFileStore(@Value("${novel.export.base-dir:./output/chapters}") String baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public void writeChapterTxt(String novelId, Integer volumeNumber, Integer chapterNumber, String chapterTitle, String content) {
        if (novelId == null || volumeNumber == null || chapterNumber == null) {
            return;
        }
        if (content == null) {
            content = "";
        }

        String safeTitle = sanitizeFileName(chapterTitle != null ? chapterTitle : ("chapter-" + chapterNumber));
        String volumeDirName = "volume-" + pad2(volumeNumber);
        String fileName = "chapter-" + pad3(chapterNumber) + "-" + safeTitle + ".txt";

        Path dir = Paths.get(baseDir, novelId, volumeDirName);
        Path file = dir.resolve(fileName);

        try {
            Files.createDirectories(dir);
            String text = buildText(chapterTitle, content);
            Files.writeString(
                    file,
                    text,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            log.info("章节txt已导出: {}", file.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("写入章节txt失败: " + file, e);
        }
    }

    private String buildText(String chapterTitle, String content) {
        StringBuilder sb = new StringBuilder();
        if (chapterTitle != null && !chapterTitle.isBlank()) {
            sb.append("# ").append(chapterTitle.trim()).append("\n\n");
        }
        sb.append(content);
        if (!content.endsWith("\n")) {
            sb.append("\n");
        }
        return sb.toString();
    }

    private String pad2(int n) {
        return n < 10 ? ("0" + n) : String.valueOf(n);
    }

    private String pad3(int n) {
        if (n < 10) return "00" + n;
        if (n < 100) return "0" + n;
        return String.valueOf(n);
    }

    private String sanitizeFileName(String input) {
        String s = input;
        // Windows/NTFS 不允许的字符: <>:"/\|?* 以及控制字符
        s = s.replaceAll("[\\\\/:*?\"<>|]", "_");
        s = s.replaceAll("[\\p{Cntrl}]", "");
        s = s.trim();
        if (s.isEmpty()) {
            return "untitled";
        }
        // 避免过长文件名
        if (s.length() > 80) {
            s = s.substring(0, 80).trim();
        }
        return s;
    }
}

