# 完整示例：从Seed → 章节生成

## 📋 流程说明

本示例演示了完整的小说生成流程，从Seed生成到章节正文生成的完整过程。

## 🔄 执行流程

```
1. 生成Seed
   └─> NovelSeedAgent
       ├─ 输入: 题材、核心冲突、世界观
       ├─ RAG检索: 检索相似风格文本
       └─ 输出: NovelSeed

2. 规划小说
   └─> NovelPlannerAgent
       ├─ 输入: NovelSeed
       ├─ 计算: 总卷数、每卷章节数
       └─ 输出: NovelPlan

3. 规划卷/册
   └─> VolumePlannerAgent
       ├─ 输入: NovelPlan + 卷序号
       └─ 输出: VolumePlan

4. 生成章节梗概
   └─> ChapterOutlineAgent
       ├─ 输入: VolumePlan + 章节序号
       └─ 输出: ChapterOutline

5. 生成场景正文
   └─> SceneGenerationAgent
       ├─ 输入: ChapterOutline
       ├─ RAG检索: 检索相似风格场景
       ├─ LLM生成: 生成场景正文
       └─ 输出: Scene

6. 一致性校验
   └─> ConsistencyGuardAgent
       ├─ 检查: 人物性格是否一致
       └─ 输出: Boolean

7. KG规则校验
   └─> KGRuleValidatorAgent
       ├─ 检查: 是否违反世界规则
       └─ 输出: Boolean
```

## 💻 代码示例

### 1. 使用Orchestrator执行完整流程

```java
@Resource
private NovelAgentOrchestrator orchestrator;

@Test
public void testCompleteFlow() {
    // 输入参数
    String genre = "修仙";
    String coreConflict = "一个废材少年的逆袭之路";
    String worldSetting = "修真世界，分为炼气、筑基、金丹、元婴等境界";
    
    // 执行完整流程
    NovelPlan plan = orchestrator.generateNovel(genre, coreConflict, worldSetting);
    
    // 输出结果
    System.out.println("小说ID: " + plan.getNovelId());
    System.out.println("总卷数: " + plan.getTotalVolumes());
    System.out.println("每卷章节数: " + plan.getChaptersPerVolume());
}
```

### 2. 单独使用Agent

```java
@Resource
private NovelSeedAgent seedAgent;

@Test
public void testSeedGeneration() {
    NovelContext context = NovelContext.builder()
            .novelId("novel-001")
            .build();
    
    String input = "题材: 修仙\n核心冲突: 废材逆袭\n世界观: 修真世界";
    NovelSeed seed = seedAgent.execute(input, context);
    
    System.out.println("Seed ID: " + seed.getSeedId());
    System.out.println("标题: " + seed.getTitle());
}
```

## 🎯 关键设计点

### 1. 分层生成
- 不允许直接从"小说设定 → 正文"
- 必须经过：Seed → 梗概 → 场景 → 正文

### 2. RAG vs KG
- **RAG**：存储文本语义和风格记忆，用于检索相似文本
- **KG**：存储世界事实、规则、关系、伏笔，用于约束生成

### 3. 生成前后校验
- **生成前**：KG约束是否合法
- **生成后**：一致性与世界规则校验

## 📝 扩展说明

### 添加新的Agent

1. 实现 `IAgent` 接口或继承 `AbstractAgent`
2. 在 `NovelAgentConfig` 中注册
3. 在 `NovelAgentOrchestrator` 中使用

### 添加新的校验规则

1. 在 `KGRuleValidatorAgent` 中添加新的规则
2. 使用Cypher查询Neo4j
3. 返回校验结果

### 添加新的生成类型

1. 创建新的生成Agent（如 `CombatDetailAgent`）
2. 在Orchestrator中调用
3. 集成到生成流程中

## 🚀 下一步

1. 实现LLM客户端集成（DeepSeek/Qwen）
2. 完善各个Agent的LLM调用逻辑
3. 实现更多Agent（VolumePlannerAgent、ChapterOutlineAgent等）
4. 添加人机协作功能
5. 实现实体抽取和伏笔管理
