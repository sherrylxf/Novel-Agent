package cn.bugstack.novel.domain.agent.service.armory;

import cn.bugstack.novel.domain.agent.IAgent;
import cn.bugstack.novel.domain.model.entity.NovelContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;

/**
 * Novel Agent装配支撑类
 * 参考课程第3-7、8节：动态实例化机制
 * 提供动态Bean注册功能
 *
 * @author xiaofuge bugstack.cn @小傅哥
 */
@Slf4j
public abstract class AbstractNovelArmorySupport {
    
    protected ApplicationContext applicationContext;
    
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }
    
    /**
     * 动态注册Agent Bean
     *
     * @param beanName Bean名称
     * @param agent Agent实例
     * @param <T> Agent类型
     */
    protected synchronized <T extends IAgent<?, ?>> void registerAgentBean(String beanName, T agent) {
        DefaultListableBeanFactory beanFactory = 
                (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        
        // 如果Bean已存在，先移除
        if (beanFactory.containsBeanDefinition(beanName)) {
            beanFactory.removeBeanDefinition(beanName);
            log.info("移除已存在的Bean: {}", beanName);
        }
        
        // 如果Bean实例已存在，先移除
        if (beanFactory.containsSingleton(beanName)) {
            beanFactory.destroySingleton(beanName);
            log.info("移除已存在的单例Bean: {}", beanName);
        }
        
        // 使用RootBeanDefinition直接注册实例，避免泛型类型推断问题
        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClass(agent.getClass());
        beanDefinition.setInstanceSupplier(() -> agent);
        beanDefinition.setScope(BeanDefinition.SCOPE_SINGLETON);
        
        // 注册新的Bean
        beanFactory.registerBeanDefinition(beanName, beanDefinition);
        log.info("成功注册Agent Bean: {}", beanName);
    }
    
    /**
     * 获取Agent Bean
     */
    @SuppressWarnings("unchecked")
    protected <T extends IAgent<?, ?>> T getAgentBean(String beanName) {
        return (T) applicationContext.getBean(beanName);
    }
    
    /**
     * 执行Agent逻辑（子类实现）
     */
    public abstract void execute(NovelContext context);
    
}
