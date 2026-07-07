package cn.bugstack.novel.domain.service.prompt;

import cn.bugstack.novel.domain.service.rag.StoryMemoryDocumentUtil;

import java.util.Map;

/**
 * 静态 Few-Shot：覆盖对话 / 冲突推进 / 动作节奏等典型片段，用于风格对齐（非情节复用）。
 */
public final class FewShotExampleCatalog {

    private static final Map<String, String> EXAMPLES = Map.ofEntries(
            Map.entry("default", """
                    【输入】剧情：主角在陌生集市被盘查，需拖延时间等同伴。
                    【输出】他指尖扣着袖里半块凉透的玉佩，面上却堆起笑，话头东拉西扯，从粮价问到城防换防。对方眉头越拧越紧，他却像浑然不觉，只把语速放慢半拍——慢，便是他的胜算。
                    """),
            Map.entry("cultivation", """
                    【输入】剧情：秘境口对峙，敌众我寡，需先挫其锋。
                    【输出】剑未出鞘，先出声。他踏前半步，靴底碾碎石屑，目光却落在对方领队腰间的宗门符——那是外门执事才有的纹路。「诸位师兄，」他声音不高，却字字敲在风声间隙，「此地灵气逆流，再往前，折的是你们的道基。」
                    """),
            Map.entry("historical", """
                    【输入】剧情：朝堂外廊，需委婉回绝权贵拉拢。
                    【输出】他垂袖一揖，语气温润：「大人厚爱，晚生资质愚钝，唯恐贻误公事。」话说得软，脚步却未往偏厅让。廊下风过，带起一角青袍，像一道无声的界线。
                    """),
            Map.entry("urban", """
                    【输入】剧情：重生后首次与前世仇人同场谈判。
                    【输出】会议室空调声低鸣。他合上文件夹，抬眼时笑意浅得像一层薄冰：「条款我看过，第三页现金流模型，贵司忘了加坏账计提。」对方指节一顿，他已将笔帽轻轻扣在桌面——嗒，轻响，却像敲在旧日败局的骨节上。
                    """),
            Map.entry("romance", """
                    【输入】剧情：误会刚解，两人独处电梯，气氛僵持。
                    【输出】金属厢壁映出两道沉默的影。她盯着楼层数字跳动，忽然开口，声音比想象中稳：「那天我不是不信你，是怕信错了。」他喉结动了动，只伸手替她按住开门键，掌心温热，挡在她与门外人潮之间。
                    """),
            Map.entry("ancient", """
                    【输入】剧情：深宅回廊，女主需试探嬷嬷口风。
                    【输出】她捧茶立在一侧，目光落在嬷嬷绣鞋尖上，话却绕着炭火说：「天寒，母亲昨夜咳了两声。」嬷嬷叹口气，捻着佛珠：「姐儿有心了，只是东院那位……」话到半截，又咽回去，只将茶盏往她手边推了推。
                    """),
            Map.entry("sci_fi", """
                    【输入】剧情：近地轨道站警报，需先确认故障层级再行动。
                    【输出】红光扫过舱壁，他扣紧磁力靴，没有冲向闸口，而是先拍开维护面板——「先读日志，」耳机里传来地面台沙哑的声音，「别用直觉赌一条船的氧气。」三行报错滚过屏幕，他吐出一口气：「二级循环泵，手动旁路可撑十二分钟。」
                    """),
            Map.entry("modern", """
                    【输入】剧情：深夜便利店，主角偶遇旧识，彼此装不认识。
                    【输出】自动门开合，冷柜嗡鸣。他捏着关东煮的纸杯，视线掠过她手里的矿泉水的牌子——和当年一样。两人隔着货架对视半秒，又同时别开脸，像默契地给彼此留一条体面的退路。
                    """)
    );

    private FewShotExampleCatalog() {}

    public static String pick(String profileKey, int maxChars) {
        String raw = EXAMPLES.getOrDefault(profileKey, EXAMPLES.get("default"));
        return StoryMemoryDocumentUtil.excerpt(raw != null ? raw.trim() : "", Math.max(200, maxChars));
    }
}
