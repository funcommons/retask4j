package fun.commons.retask4j.http.test;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;

import fun.commons.retask4j.http.message.HttpMessageUtils;
import fun.commons.retask4j.http.message.HttpRequestData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug")
@Slf4j
public class DebugController {

    public DebugController() {
        log.info("DebugController init");
    }
    @RequestMapping("/request.info")
    @ResponseBody
    public JSONObject requestInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        HttpRequestData requestData = HttpMessageUtils.convertRequestData(request);
        JSONObject result = JSONObject.from(requestData);
        log.info(JSON.toJSONString(result, JSONWriter.Feature.PrettyFormat));
        return result;
    }

}
