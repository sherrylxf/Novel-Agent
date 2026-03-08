package cn.bugstack.novel.trigger.http;

import cn.bugstack.novel.infrastructure.dao.INovelPlanDao;
import cn.bugstack.novel.infrastructure.dao.po.NovelPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试控制器
 * 用于测试数据库连接和数据保存
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.OPTIONS})
public class TestController {
    
    @Resource
    private INovelPlanDao novelPlanDao;
    
    /**
     * 测试数据库连接和插入（不使用事务，直接提交）
     */
    @PostMapping("/db/insert")
    public Map<String, Object> testInsert() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("开始测试数据库插入...");
            
            // 创建测试数据
            NovelPlan testPlan = NovelPlan.builder()
                    .planId("test-plan-" + System.currentTimeMillis())
                    .novelId("test-novel-" + System.currentTimeMillis())
                    .totalVolumes(14)
                    .chaptersPerVolume(20)
                    .overallOutline("这是一个测试大纲")
                    .build();
            
            log.info("准备插入数据：planId={}, novelId={}", testPlan.getPlanId(), testPlan.getNovelId());
            
            // 执行插入（不使用事务，直接提交）
            int insertResult = novelPlanDao.insert(testPlan);
            
            log.info("插入结果：{}", insertResult);
            
            // 等待一小段时间，确保数据已提交
            Thread.sleep(100);
            
            // 立即查询验证
            NovelPlan queriedPlan = novelPlanDao.queryByPlanId(testPlan.getPlanId());
            
            if (queriedPlan != null) {
                result.put("success", true);
                result.put("message", "插入成功并查询到数据");
                result.put("insertResult", insertResult);
                result.put("queriedPlan", queriedPlan);
                log.info("查询到数据：planId={}, novelId={}", queriedPlan.getPlanId(), queriedPlan.getNovelId());
            } else {
                result.put("success", false);
                result.put("message", "插入返回" + insertResult + "，但查询不到数据（可能是事务未提交或数据库连接问题）");
                result.put("insertResult", insertResult);
                log.warn("插入返回{}，但查询不到数据", insertResult);
            }
            
        } catch (Exception e) {
            log.error("测试插入失败", e);
            result.put("success", false);
            result.put("message", "插入失败：" + e.getMessage());
            result.put("error", e.getClass().getName());
            result.put("stackTrace", getStackTrace(e));
        }
        
        return result;
    }
    
    /**
     * 测试查询
     */
    @GetMapping("/db/query")
    public Map<String, Object> testQuery(@RequestParam(required = false) String planId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            log.info("开始测试数据库查询，planId: {}", planId);
            
            if (planId != null && !planId.isEmpty()) {
                NovelPlan plan = novelPlanDao.queryByPlanId(planId);
                result.put("success", true);
                result.put("data", plan);
                result.put("message", plan != null ? "查询成功" : "未找到数据");
            } else {
                // 查询所有数据
                java.util.List<NovelPlan> plans = novelPlanDao.queryByNovelId("test-novel-%");
                result.put("success", true);
                result.put("data", plans);
                result.put("count", plans != null ? plans.size() : 0);
            }
            
        } catch (Exception e) {
            log.error("测试查询失败", e);
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
            result.put("error", e.getClass().getName());
        }
        
        return result;
    }
    
    /**
     * 获取所有数据
     */
    @GetMapping("/db/all")
    public Map<String, Object> getAllPlans() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 使用一个不存在的novelId来测试，或者我们需要修改DAO支持查询所有
            // 这里先测试一个已知的novelId
            java.util.List<NovelPlan> plans = novelPlanDao.queryByNovelId("novel-1769683515669");
            
            result.put("success", true);
            result.put("data", plans);
            result.put("count", plans != null ? plans.size() : 0);
            result.put("message", "查询成功");
            
        } catch (Exception e) {
            log.error("查询所有数据失败", e);
            result.put("success", false);
            result.put("message", "查询失败：" + e.getMessage());
            result.put("error", e.getClass().getName());
        }
        
        return result;
    }
    
    private String getStackTrace(Exception e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
