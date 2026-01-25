# 基础镜像，可以先执行 docker pull openjdk:17-jdk-slim
FROM openjdk:17-jdk-slim

# 作者
MAINTAINER xiaofuge

# 配置
ENV PARAMS=""
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建日志目录
RUN mkdir -p /app/logs

# 添加应用
ADD target/ai-agent-station-study-app.jar /app/novel-agent-app.jar

# 工作目录
WORKDIR /app

# 暴露端口
EXPOSE 8091

# 启动应用
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/novel-agent-app.jar $PARAMS"]