package fun.commons.retask4j.http.server;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import fun.commons.retask4j.core.internal.FuTaskBase;
import fun.commons.retask4j.core.config.FuTaskBaseConfig;
import fun.commons.retask4j.core.message.FuTaskMessage;
import fun.commons.retask4j.http.caller.FuHttpTaskCallerService;
import fun.commons.retask4j.http.message.HttpMessageUtils;
import fun.commons.retask4j.http.message.HttpRequestData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/debug")
@Profile("dev")
@Slf4j
public class DebugController extends BaseController{

    static class ReTaskInfo extends FuTaskBase {
        public ReTaskInfo(RedissonClient redissonClient, String topic) {
            super(redissonClient, new FuTaskBaseConfig(topic));
        }

        @Override
        public JSONObject getTaskCountInfo(){
            return super.getTaskCountInfo();
        }

        public FuTaskMessage getMessageById(String taskId){
            List<FuTaskMessage> list = getMessagesById(List.of(taskId));
            if ( list != null && !list.isEmpty()) {
                return list.get(0);
            }else {
                return null;
            }
        }
    }

    @Autowired
    FuHttpTaskCallerService fuHttpTaskCallerService;

    private final Cache<String, ReTaskInfo> taskInfoCache = CacheBuilder.newBuilder()
            .maximumSize(64).expireAfterAccess(1, TimeUnit.HOURS).build();

    public DebugController() {
        log.info("DebugController init");
    }

    @PreDestroy
    public void cleanup() {
        taskInfoCache.invalidateAll();
    }

    private ReTaskInfo getTaskInfo(String topic) {
        try {
            return taskInfoCache.get(topic,
                    () -> new ReTaskInfo(fuHttpTaskCallerService.getRedissonClient(), topic));
        } catch (Exception e) {
            log.error("Failed to create ReTaskInfo for topic: {}", topic, e);
            throw new RuntimeException(e);
        }
    }

    @RequestMapping("/request.info")
    @ResponseBody
    public JSONObject requestInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpRequestData requestData = HttpMessageUtils.convertRequestData(request);
        JSONObject result = JSONObject.from(requestData);
        log.info(JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat));
        return result;
    }

    @RequestMapping("/{topic}/taskCount.do")
    @ResponseBody
    public void taskCount(@PathVariable("topic") String topic, HttpServletRequest request, HttpServletResponse response) throws Exception {
        ReTaskInfo tasker = getTaskInfo(topic);
        JSONObject jsonObject = tasker.getTaskCountInfo();
        log.info(JSON.toJSONString(jsonObject, JSONWriter.Feature.PrettyFormat));
        writeApiSuccess(response, jsonObject);
    }

    @RequestMapping("/{topic}/getTask.do")
    @ResponseBody
    public void getTask(@PathVariable("topic") String topic, @RequestParam("taskId") String  taskId, HttpServletRequest request, HttpServletResponse response) throws Exception {
        ReTaskInfo tasker = getTaskInfo(topic);
        FuTaskMessage fuTaskMessage = tasker.getMessageById(taskId);
        if (fuTaskMessage == null) {
            writeApiError(response,404, "taskId not found");
        }else {
            JSONObject result = JSONObject.from(fuTaskMessage);
            log.info(JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat));
            writeApiSuccess(response, result);
        }
    }

}
