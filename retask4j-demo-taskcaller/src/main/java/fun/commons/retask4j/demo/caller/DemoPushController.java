package fun.commons.retask4j.demo.caller;


import com.alibaba.fastjson2.JSONObject;

import fun.commons.retask4j.core.internal.FuTaskBatchManager;
import fun.commons.retask4j.core.config.FuTaskCallConfig;
import fun.commons.retask4j.core.api.FuTaskCaller;
import fun.commons.retask4j.core.message.FuTaskMessage;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/demo-push")
@Slf4j
public class DemoPushController implements InitializingBean, DisposableBean {

    @Autowired
    @Qualifier("redisson-client")
    RedissonClient redissonClient;

    FuTaskCallConfig<JSONObject> callConfig = new FuTaskCallConfig<>( "demo", JSONObject.class);

    volatile FuTaskCaller<JSONObject> caller ;


    volatile FuTaskBatchManager<FuTaskMessage,Boolean> batchCaller ;


    @RequestMapping("/send.do")
    @ResponseBody
    public Object send(HttpServletRequest request, HttpServletResponse response) throws Exception {

        JSONObject input = new JSONObject();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        input.put("id", taskId);
        input.put("name", "demo");
        input.put("time", System.currentTimeMillis());
        caller.sendTaskMessage(caller.newTaskMessage(taskId, input));
        return "ok";

    }

    @RequestMapping("/batch.do")
    @ResponseBody
    public CompletableFuture<String> batch(HttpServletRequest request, HttpServletResponse response) {

        JSONObject input = new JSONObject();
        String taskId = UUID.randomUUID().toString().replace("-", "");
        input.put("id", taskId);
        input.put("name", "demo");
        input.put("time", System.currentTimeMillis());
        FuTaskMessage message = caller.newTaskMessage(taskId,input);
        message.setRetryPlan(List.of(2,5,10));
        return batchCaller.submit(message)
                .thenApply(ok -> ok ? "ok" : "fail")
                .orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(ex -> "fail");

    }


    @Override
    public void afterPropertiesSet() throws Exception {

          caller = new FuTaskCaller<>(redissonClient, callConfig);
          caller.start();

         batchCaller = new FuTaskBatchManager<>(1000, 20, 4, (list)->{
            int count = caller.sendTaskMessage(list);
            return count > 0;
        });

    }

    @Override
    public void destroy() throws Exception {
        try {
            batchCaller.flush();
        } catch (Exception e) {
            log.warn("Error flushing batchCaller", e);
        }
        try {
            batchCaller.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down batchCaller", e);
        }
        try {
            caller.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down caller", e);
        }
    }
}
